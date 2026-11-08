package run.halo.simnook;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static run.halo.simnook.CardFacts.present;
import static run.halo.simnook.CardFacts.text;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 由号卡管理角色访问，公开 cards 路由始终使用独立的脱敏模型。 */
@Component
public class ManagementEndpoint implements CustomEndpoint {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final ReactiveExtensionClient client;
    private final ReactiveSettingFetcher settings;

    public ManagementEndpoint(ReactiveExtensionClient client, ReactiveSettingFetcher settings) {
        this.client = client;
        this.settings = settings;
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.simnook.halo.run/v1alpha1");
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
                .GET(
                        "cardviews",
                        request -> {
                            var locale = Messages.locale(request.headers().firstHeader(HttpHeaders.ACCEPT_LANGUAGE));
                            return settings.getSettingValues()
                                    .map(RenewalSettings::from)
                                    .flatMap(schedule -> client.list(
                                                    SimCard.class,
                                                    card -> card.getMetadata().getDeletionTimestamp() == null,
                                                    PublicCard.SOURCE_ORDER)
                                            .index()
                                            .map(item -> managedCard(
                                                    item.getT2(),
                                                    schedule.today(),
                                                    item.getT1().intValue(),
                                                    locale))
                                            .collectList()
                                            .flatMap(cards -> privateResponse()
                                                    .bodyValue(Map.of("cards", cards, "demo", false, "full", true))));
                        },
                        builder -> builder.operationId("listCardViews")
                                .tag("SimnookManagementV1alpha1")
                                .response(responseBuilder().implementation(ManagementModels.CardViews.class)))
                .GET(
                        "cardviews/{name}",
                        request -> client.fetch(SimCard.class, request.pathVariable("name"))
                                .filter(card -> card.getMetadata().getDeletionTimestamp() == null)
                                .zipWith(settings.getSettingValues().map(RenewalSettings::from))
                                .flatMap(result -> privateResponse()
                                        .bodyValue(managedCard(
                                                result.getT1(),
                                                result.getT2().today(),
                                                0,
                                                Messages.locale(
                                                        request.headers().firstHeader(HttpHeaders.ACCEPT_LANGUAGE)))))
                                .switchIfEmpty(ServerResponse.notFound().build()),
                        builder -> builder.operationId("getCardView")
                                .tag("SimnookManagementV1alpha1")
                                .parameter(parameterBuilder()
                                        .name("name")
                                        .in(ParameterIn.PATH)
                                        .required(true)
                                        .implementation(String.class))
                                .response(responseBuilder().implementation(ManagementModels.ManagedCardView.class)))
                .POST(
                        "cardviews/{name}/renewal",
                        request -> request.bodyToMono(ManagementModels.QuickRenewalRequest.class)
                                .flatMap(update -> client.fetch(SimCard.class, request.pathVariable("name"))
                                        .filter(card -> card.getMetadata().getDeletionTimestamp() == null)
                                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                                        .flatMap(card -> {
                                            var renewed = renew(card, update);
                                            return renewed == card ? Mono.just(card) : client.update(renewed);
                                        }))
                                .flatMap(card -> ServerResponse.ok()
                                        .cacheControl(CacheControl.noStore())
                                        .bodyValue(card)),
                        builder -> builder.operationId("quickRenewCard")
                                .tag("SimnookManagementV1alpha1")
                                .parameter(parameterBuilder()
                                        .name("name")
                                        .in(ParameterIn.PATH)
                                        .required(true)
                                        .implementation(String.class))
                                .requestBody(
                                        requestBodyBuilder().implementation(ManagementModels.QuickRenewalRequest.class))
                                .response(responseBuilder().implementation(SimCard.class)))
                .build();
    }

    static SimCard renew(SimCard card, ManagementModels.QuickRenewalRequest update) {
        var record = update.record();
        if (record == null
                || !present(record.getId())
                || !"renewal".equals(CardFacts.recordKind(record.getType()))
                || CardFacts.date(record.getDate()) == null
                || CardFacts.date(record.getExtendsTo()) == null
                || record.getAmount() == null
                || record.getAmount().signum() < 0
                || record.getCurrency() == null
                || !record.getCurrency().matches("[A-Z]{3}")
                || !"CNY".equals(record.getCurrency())
                        && (record.getRate() == null || record.getRate().signum() <= 0)) {
            throw BackupService.bad("请填写有效的续费日期、金额、币种、汇率与延长日期");
        }
        var next = present(update.nextRenewalAt()) ? CardFacts.date(update.nextRenewalAt()) : null;
        var end = present(update.nextRenewalEnd()) ? CardFacts.date(update.nextRenewalEnd()) : null;
        if (present(update.nextRenewalAt()) && next == null
                || present(update.nextRenewalEnd()) && end == null
                || end != null && end.compareTo(next == null ? record.getExtendsTo() : next) < 0) {
            throw BackupService.bad("续费截止日不能早于起始日");
        }
        var existing = CardFacts.records(card.getSpec()).stream()
                .filter(row -> record.getId().equals(row.getId()))
                .findFirst();
        if (existing.isPresent()) {
            if (JSON.valueToTree(existing.get()).equals(JSON.valueToTree(record))
                    && Objects.equals(card.getSpec().getNextRenewalAt(), next)
                    && Objects.equals(card.getSpec().getNextRenewalEnd(), end)) return card;
            throw new ResponseStatusException(HttpStatus.CONFLICT, "消费记录 ID 已存在且内容不同");
        }
        if (!Objects.equals(Objects.toString(card.getMetadata().getVersion(), null), update.expectedVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "号卡已变化，请重新加载并保留草稿");
        }
        if (Boolean.TRUE.equals(card.getSpec().getCancelled())
                || CardFacts.date(card.getSpec().getCancelledAt()) != null
                || CardFacts.records(card.getSpec()).size() >= 1000) throw BackupService.bad("已注销号卡或记录已满，不能追加续费");
        // 在服务端保留原有 BigDecimal，浏览器只提交本次记录，避免旧金额经过 JS Number 舍入。
        var result = JSON.convertValue(card, SimCard.class);
        var rows = new ArrayList<>(CardFacts.records(result.getSpec()));
        rows.add(record);
        result.getSpec().setRecords(rows);
        if (present(result.getSpec().getExpiresAt())) result.getSpec().setExpiresAt(record.getExtendsTo());
        result.getSpec().setNextRenewalAt(next);
        result.getSpec().setNextRenewalEnd(end);
        return result;
    }

    static ObjectNode managedCard(SimCard card, LocalDate today, int order) {
        return managedCard(card, today, order, Locale.SIMPLIFIED_CHINESE);
    }

    static ObjectNode managedCard(SimCard card, LocalDate today, int order, Locale locale) {
        var publicCard = PublicCard.from(card, today, order);
        var spec = card.getSpec();
        var records = CardFacts.records(spec);
        var sourceById = IntStream.range(0, records.size())
                .boxed()
                .collect(Collectors.toMap(
                        i -> PublicCard.recordId(publicCard.id(), records.get(i), i),
                        records::get,
                        (first, ignored) -> first));
        var checks = DataChecks.card(spec, publicCard, today, locale);

        ObjectNode result = JSON.valueToTree(publicCard);
        result.put("statusLabel", Messages.text(locale, "status." + publicCard.status()));
        result.put(
                "urgencyCode",
                CardFacts.urgencyCode(
                        publicCard.status(), publicCard.nextRenewalAt(), publicCard.nextRenewalEnd(), today));
        result.put("asOfDate", today.toString());
        result.put(
                "urgency",
                CardFacts.urgency(
                        publicCard.status(), publicCard.nextRenewalAt(), publicCard.nextRenewalEnd(), today, locale));
        result.put(
                "type",
                present(spec.getTypeLabel())
                        ? spec.getTypeLabel()
                        : Messages.text(locale, "type." + CardFacts.typeKind(spec)));
        result.put("number", spec.getNumber());
        result.put("balance", spec.getBalance());
        result.put("balanceUpdatedAt", CardFacts.date(spec.getBalanceUpdatedAt()));
        result.put("device", spec.getDevice());
        result.put("account", spec.getAccount());
        result.set("services", JSON.valueToTree(spec.getServices() == null ? List.of() : spec.getServices()));
        result.set("checks", JSON.valueToTree(checks));
        var separator = Messages.text(locale, "checkSeparator");
        var warning = String.join(separator, checks);
        result.put(
                "dataCheck",
                present(spec.getDataCheck())
                        ? (warning.isEmpty() ? spec.getDataCheck() : warning + separator + spec.getDataCheck())
                        : warning);

        var history = result.putArray("history");
        for (var item : publicCard.history()) {
            var source = sourceById.get(item.id());
            ObjectNode row = JSON.valueToTree(item);
            row.put("title", text(source.getTitle()));
            row.set("checks", JSON.valueToTree(DataChecks.record(source, spec, today, locale)));
            history.add(row);
        }
        return result;
    }

    private static ServerResponse.BodyBuilder privateResponse() {
        return ServerResponse.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .varyBy(HttpHeaders.COOKIE, HttpHeaders.AUTHORIZATION, HttpHeaders.ACCEPT_LANGUAGE);
    }
}
