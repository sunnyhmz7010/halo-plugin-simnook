package run.halo.simnook;

import static run.halo.simnook.BackupModels.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.PluginContext;
import run.halo.app.plugin.ReactiveSettingFetcher;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@Slf4j
public class BackupService {
    static final int MAX_BYTES = 32 * 1024 * 1024;
    static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .build();
    private final ReactiveExtensionClient client;
    private final PluginContext context;
    private final ReactiveSettingFetcher settings;

    public BackupService(ReactiveExtensionClient client, PluginContext context, ReactiveSettingFetcher settings) {
        this.client = client;
        this.context = context;
        this.settings = settings;
    }

    record Snapshot(Map<String, SimCard> cards, Map<String, PaymentAccount> accounts) {}

    Mono<Snapshot> snapshot() {
        return Mono.zip(
                client.list(SimCard.class, null, PublicCard.SOURCE_ORDER)
                        .collectMap(card -> card.getMetadata().getName()),
                client.list(
                                PaymentAccount.class,
                                null,
                                Comparator.comparing(
                                        account -> account.getMetadata().getName()))
                        .collectMap(account -> account.getMetadata().getName()),
                Snapshot::new);
    }

    Mono<BackupDocument> export() {
        return snapshot()
                .map(snapshot -> new BackupDocument(
                        "simnook-backup",
                        1,
                        Instant.now().toString(),
                        context.getVersion(),
                        snapshot.cards().values().stream()
                                .filter(BackupService::active)
                                .sorted(PublicCard.SOURCE_ORDER)
                                .map(card -> new CardEntry(card.getMetadata().getName(), card.getSpec()))
                                .toList(),
                        snapshot.accounts().values().stream()
                                .filter(BackupService::active)
                                .sorted(Comparator.comparing(
                                        account -> account.getMetadata().getName()))
                                .map(account ->
                                        new AccountEntry(account.getMetadata().getName(), account.getSpec()))
                                .toList()));
    }

    static BackupDocument parse(String content) {
        if (content == null || content.isBlank()) throw bad("请选择备份 JSON 文件");
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "备份文件不能超过 32 MiB");
        }
        final BackupDocument document;
        try {
            document = JSON.readValue(content, BackupDocument.class);
        } catch (RuntimeException invalid) {
            throw bad("备份 JSON 格式错误或包含不支持的字段");
        }
        if (document == null
                || !"simnook-backup".equals(document.format())
                || document.version() != 1
                || document.cards() == null
                || document.paymentAccounts() == null) throw bad("不支持的备份格式或版本");
        var names = new HashSet<String>();
        for (var entry : document.paymentAccounts()) {
            if (entry == null || entry.spec() == null) throw bad("支付账户缺少业务字段");
            validateName(entry.name(), entry.spec().getName(), names);
        }
        names.clear();
        for (var entry : document.cards()) {
            if (entry == null || entry.spec() == null) throw bad("号卡缺少业务字段");
            validateName(entry.name(), entry.spec().getName(), names);
            var ids = new HashSet<String>();
            var records = CardFacts.records(entry.spec());
            if (records.size() > 1000) throw bad("单张号卡最多包含 1000 条消费记录");
            for (var row : records) {
                if (row == null || (CardFacts.present(row.getId()) && !ids.add(row.getId())))
                    throw bad("消费记录为空或包含重复 ID");
            }
        }
        return document;
    }

    private static void validateName(String name, String title, Set<String> names) {
        if (name == null
                || name.length() > 253
                || !name.matches("[a-z0-9]([a-z0-9.-]*[a-z0-9])?")
                || !CardFacts.present(title)
                || title.length() > 100) throw bad("资源 ID 或展示名称无效");
        if (!names.add(name)) throw bad("备份包含重复资源 ID：" + name);
    }

    Mono<BackupPreview> preview(String content) {
        var document = parse(content);
        return snapshot().publishOn(Schedulers.boundedElastic()).map(snapshot -> {
            var items = new ArrayList<PreviewItem>();
            var accounts = snapshot.accounts().values().stream()
                    .filter(BackupService::active)
                    .map(account -> account.getMetadata().getName())
                    .collect(Collectors.toSet());
            document.paymentAccounts().forEach(entry -> accounts.add(entry.name()));
            for (var entry : document.paymentAccounts()) {
                items.add(previewItem(
                        "PaymentAccount",
                        entry.name(),
                        entry.spec().getName(),
                        entry.spec(),
                        snapshot.accounts().get(entry.name()),
                        List.of(),
                        0));
            }
            for (var entry : document.cards()) {
                var current = snapshot.cards().get(entry.name());
                var warnings = new ArrayList<String>();
                var missing = references(entry.spec()).stream()
                        .filter(id -> !accounts.contains(id))
                        .toList();
                if (!missing.isEmpty()) warnings.add("缺少支付账户：" + String.join(", ", missing));
                if (current != null && active(current)) {
                    var existing = CardFacts.records(current.getSpec()).stream()
                            .filter(row -> CardFacts.present(row.getId()))
                            .collect(Collectors.toMap(
                                    SimCard.ExpenseRecord::getId, row -> row, (first, ignored) -> first));
                    var conflicts = CardFacts.records(entry.spec()).stream()
                            .filter(row -> existing.containsKey(row.getId())
                                    && !fingerprint(row).equals(fingerprint(existing.get(row.getId()))))
                            .count();
                    if (conflicts > 0) warnings.add(conflicts + " 条同 ID 消费记录内容不同；追加时保留现有记录");
                }
                var appended = current == null
                        ? CardFacts.records(entry.spec()).size()
                        : appendRecords(CardFacts.records(current.getSpec()), CardFacts.records(entry.spec()))
                                        .size()
                                - CardFacts.records(current.getSpec()).size();
                items.add(previewItem(
                        "SimCard", entry.name(), entry.spec().getName(), entry.spec(), current, warnings, appended));
            }
            return new BackupPreview(hash(content), items);
        });
    }

    private static PreviewItem previewItem(
            String kind,
            String name,
            String title,
            Object incoming,
            AbstractExtension current,
            List<String> warnings,
            int appended) {
        var problems = new ArrayList<>(warnings);
        List<String> actions;
        if (current == null) actions = List.of("create", "skip");
        else if (!active(current)) {
            actions = List.of("skip");
            problems.add("此 ID 正在删除，回收完成前不能恢复");
        } else actions = "SimCard".equals(kind) ? List.of("skip", "overwrite", "append") : List.of("skip", "overwrite");
        Object before = current instanceof SimCard card
                ? card.getSpec()
                : current instanceof PaymentAccount account ? account.getSpec() : null;
        return new PreviewItem(
                kind, name, title, version(current), actions, differences(before, incoming), problems, appended);
    }

    static List<FieldDifference> differences(Object before, Object after) {
        var old = before == null ? JSON.createObjectNode() : JSON.<ObjectNode>valueToTree(before);
        var next = JSON.<ObjectNode>valueToTree(after);
        var keys = new LinkedHashSet<String>();
        old.propertyNames().forEach(keys::add);
        next.propertyNames().forEach(keys::add);
        return keys.stream()
                .filter(key -> !Objects.equals(old.get(key), next.get(key)))
                .map(key -> new FieldDifference(
                        key,
                        old.has(key) ? old.get(key).toString() : "null",
                        next.has(key) ? next.get(key).toString() : "null"))
                .toList();
    }

    Mono<RestoreResult> restore(RestoreRequest request) {
        var document = parse(request.content());
        if (!hash(request.content()).equals(request.fileHash())) throw bad("备份文件已变化，请重新预览");
        return snapshot().publishOn(Schedulers.boundedElastic()).flatMap(snapshot -> {
            var plan = prepare(document, request.decisions(), snapshot);
            var stopped = new AtomicBoolean();
            return Flux.fromIterable(plan)
                    .concatMap(item -> {
                        var decision = item.decision();
                        if ("skip".equals(decision.action())) return Mono.just(result(decision, "skipped", null));
                        if (stopped.get()) return Mono.just(result(decision, "pending", "前序写入失败，尚未处理"));
                        return Mono.defer(() -> write(item))
                                .thenReturn(result(decision, "success", null))
                                .onErrorResume(error -> {
                                    stopped.set(true);
                                    log.error("SIMnook 恢复失败：{}/{}", decision.kind(), decision.name(), error);
                                    return Mono.just(result(decision, "failed", "写入失败，可能已被修改；请重新预览后重试"));
                                });
                    })
                    .collectList()
                    .map(RestoreResult::new);
        });
    }

    record Planned(RestoreDecision decision, AbstractExtension resource) {}

    static List<Planned> prepare(BackupDocument document, List<RestoreDecision> decisions, Snapshot snapshot) {
        if (decisions == null) throw bad("缺少恢复选项");
        var choices = new HashMap<String, RestoreDecision>();
        for (var decision : decisions) {
            if (decision == null || choices.put(decision.kind() + "/" + decision.name(), decision) != null)
                throw bad("恢复选项重复或无效");
        }
        if (choices.size()
                != document.cards().size() + document.paymentAccounts().size()) throw bad("恢复选项与备份不一致");
        var result = new ArrayList<Planned>();
        var accountNames = snapshot.accounts().values().stream()
                .filter(BackupService::active)
                .map(account -> account.getMetadata().getName())
                .collect(Collectors.toSet());
        for (var entry : document.paymentAccounts()) {
            var current = snapshot.accounts().get(entry.name());
            var decision = decision(choices, "PaymentAccount", entry.name(), current);
            var resource = current == null ? new PaymentAccount() : JSON.convertValue(current, PaymentAccount.class);
            if (!"skip".equals(decision.action())) {
                if (current == null) resource.setMetadata(metadata(entry.name()));
                resource.setSpec(entry.spec());
                accountNames.add(entry.name());
            }
            result.add(new Planned(decision, resource));
        }
        for (var entry : document.cards()) {
            var current = snapshot.cards().get(entry.name());
            var decision = decision(choices, "SimCard", entry.name(), current);
            var resource = current == null ? new SimCard() : JSON.convertValue(current, SimCard.class);
            if (!"skip".equals(decision.action())) {
                if (current == null) resource.setMetadata(metadata(entry.name()));
                if ("append".equals(decision.action())) {
                    resource.getSpec()
                            .setRecords(appendRecords(
                                    CardFacts.records(resource.getSpec()), CardFacts.records(entry.spec())));
                } else resource.setSpec(entry.spec());
                if (CardFacts.records(resource.getSpec()).size() > 1000) throw bad("追加后消费记录超过 1000 条：" + entry.name());
                var missing = references(resource.getSpec()).stream()
                        .filter(id -> !accountNames.contains(id))
                        .toList();
                if (!missing.isEmpty()) throw bad("号卡 " + entry.name() + " 缺少支付账户：" + String.join(", ", missing));
            }
            result.add(new Planned(decision, resource));
        }
        return result;
    }

    private static RestoreDecision decision(
            Map<String, RestoreDecision> choices, String kind, String name, AbstractExtension current) {
        var decision = choices.remove(kind + "/" + name);
        if (decision == null) throw bad("缺少资源的恢复选项：" + name);
        if ("skip".equals(decision.action())) return decision;
        if (!Objects.equals(version(current), decision.expectedVersion()) || current != null && !active(current)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "资源已变化，请重新预览：" + name);
        }
        if (current == null && !"create".equals(decision.action())
                || current != null
                        && !("overwrite".equals(decision.action())
                                || "SimCard".equals(kind) && "append".equals(decision.action()))) {
            throw bad("无效的恢复操作：" + name);
        }
        return decision;
    }

    private Mono<?> write(Planned item) {
        var resource = item.resource();
        if ("create".equals(item.decision().action())) return client.create(resource);
        return client.update(resource);
    }

    static List<SimCard.ExpenseRecord> appendRecords(
            List<SimCard.ExpenseRecord> existing, List<SimCard.ExpenseRecord> incoming) {
        var result = new ArrayList<>(existing);
        var ids = existing.stream()
                .filter(row -> CardFacts.present(row.getId()))
                .collect(Collectors.toMap(SimCard.ExpenseRecord::getId, row -> row, (first, ignored) -> first));
        var available =
                existing.stream().collect(Collectors.groupingBy(BackupService::fingerprint, Collectors.counting()));
        // 先占用同 ID 的现有记录，剩余内容计数用于匹配无 ID 的旧交易。
        for (var row : incoming) {
            if (CardFacts.present(row.getId()) && ids.containsKey(row.getId())) {
                available.computeIfPresent(fingerprint(ids.get(row.getId())), (key, count) -> Math.max(0, count - 1));
            }
        }
        for (var row : incoming) {
            if (CardFacts.present(row.getId())) {
                if (!ids.containsKey(row.getId())) result.add(row);
            } else {
                var fingerprint = fingerprint(row);
                var count = available.getOrDefault(fingerprint, 0L);
                if (count > 0) available.put(fingerprint, count - 1);
                else result.add(row);
            }
        }
        return result;
    }

    private static String fingerprint(SimCard.ExpenseRecord row) {
        ObjectNode node = JSON.valueToTree(row);
        node.remove("id");
        if (row.getAmount() != null) node.put("amount", row.getAmount().stripTrailingZeros());
        if (row.getRate() != null) node.put("rate", row.getRate().stripTrailingZeros());
        return node.toString();
    }

    private static Set<String> references(SimCard.CardSpec spec) {
        var refs = new HashSet<String>();
        if (spec.getPaymentAccountIds() != null) refs.addAll(spec.getPaymentAccountIds());
        for (var row : CardFacts.records(spec))
            if (row.getPaymentAccountIds() != null) refs.addAll(row.getPaymentAccountIds());
        refs.removeIf(id -> !CardFacts.present(id));
        return refs;
    }

    Mono<String> csv(String kind) {
        if (!List.of("cards", "records").contains(kind)) throw bad("不支持的 CSV 类型");
        return export().zipWith(settings.getSettingValues().map(RenewalSettings::from))
                .publishOn(Schedulers.boundedElastic())
                .map(pair -> {
                    var document = pair.getT1();
                    var today = pair.getT2().today();
                    var rows = new StringBuilder();
                    if ("cards".equals(kind)) {
                        rows.append(
                                "id,name,carrier,number,country,currency,price,expiresAt,nextRenewalAt,nextRenewalEnd\n");
                        for (var card : document.cards()) {
                            var spec = card.spec();
                            var facts = CardFacts.from(spec, today);
                            csvRow(
                                    rows,
                                    card.name(),
                                    spec.getName(),
                                    spec.getCarrier(),
                                    spec.getNumber(),
                                    spec.getCountryName(),
                                    spec.getCurrency(),
                                    spec.getPrice(),
                                    facts.expiry(),
                                    facts.renewal(),
                                    facts.renewalEnd());
                        }
                    } else {
                        rows.append(
                                "cardId,cardName,recordId,type,date,amount,currency,rate,extendsTo,title,paymentAccountIds\n");
                        for (var card : document.cards())
                            for (var row : CardFacts.records(card.spec())) {
                                csvRow(
                                        rows,
                                        card.name(),
                                        card.spec().getName(),
                                        row.getId(),
                                        row.getType(),
                                        row.getDate(),
                                        row.getAmount(),
                                        row.getCurrency(),
                                        row.getRate(),
                                        row.getExtendsTo(),
                                        row.getTitle(),
                                        row.getPaymentAccountIds() == null
                                                ? ""
                                                : String.join(";", row.getPaymentAccountIds()));
                            }
                    }
                    return rows.toString();
                });
    }

    static void csvRow(StringBuilder out, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(',');
            var value = values[i] instanceof java.math.BigDecimal amount
                    ? amount.toPlainString()
                    : Objects.toString(values[i], "");
            if (value.stripLeading().matches("(?s)^[=+@-].*") || value.startsWith("\t") || value.startsWith("\r"))
                value = "'" + value;
            out.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        out.append('\n');
    }

    private static boolean active(AbstractExtension resource) {
        return resource.getMetadata().getDeletionTimestamp() == null;
    }

    private static String version(AbstractExtension resource) {
        return resource == null ? null : Objects.toString(resource.getMetadata().getVersion(), null);
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }

    private static RestoreItem result(RestoreDecision decision, String status, String message) {
        return new RestoreItem(decision.kind(), decision.name(), decision.action(), status, message);
    }

    static String hash(String content) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
