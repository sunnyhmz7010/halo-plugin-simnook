package run.halo.simnook;

import static run.halo.simnook.CardFacts.present;
import static run.halo.simnook.CardFacts.text;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ReactiveExtensionClient;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 由号卡管理角色访问，公开 cards 路由始终使用独立的脱敏模型。 */
@Component
public class ManagementEndpoint implements CustomEndpoint {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final ReactiveExtensionClient client;

    public ManagementEndpoint(ReactiveExtensionClient client) {
        this.client = client;
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.simnook.halo.run/v1alpha1");
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
                .GET("cardviews", request -> {
                    var today = LocalDate.now();
                    var locale = Messages.locale(request.headers().firstHeader(HttpHeaders.ACCEPT_LANGUAGE));
                    return accountNames()
                            .flatMap(names -> client.list(
                                            SimCard.class,
                                            card -> card.getMetadata().getDeletionTimestamp() == null,
                                            PublicCard.SOURCE_ORDER)
                                    .index()
                                    .map(item -> managedCard(
                                            item.getT2(),
                                            names,
                                            today,
                                            item.getT1().intValue(),
                                            locale))
                                    .collectList())
                            .flatMap(cards ->
                                    privateResponse().bodyValue(Map.of("cards", cards, "demo", false, "full", true)));
                })
                .GET(
                        "cardviews/{name}",
                        request -> client.fetch(SimCard.class, request.pathVariable("name"))
                                .filter(card -> card.getMetadata().getDeletionTimestamp() == null)
                                .zipWith(accountNames())
                                .flatMap(result -> privateResponse()
                                        .bodyValue(managedCard(
                                                result.getT1(),
                                                result.getT2(),
                                                LocalDate.now(),
                                                0,
                                                Messages.locale(
                                                        request.headers().firstHeader(HttpHeaders.ACCEPT_LANGUAGE)))))
                                .switchIfEmpty(ServerResponse.notFound().build()))
                .build();
    }

    private Mono<Map<String, String>> accountNames() {
        return client.list(
                        PaymentAccount.class,
                        account -> account.getMetadata().getDeletionTimestamp() == null,
                        Comparator.comparing(account -> account.getMetadata().getName()))
                .collectMap(
                        account -> account.getMetadata().getName(),
                        account -> account.getSpec().getName());
    }

    static ObjectNode managedCard(SimCard card, Map<String, String> names, LocalDate today, int order) {
        return managedCard(card, names, today, order, Locale.SIMPLIFIED_CHINESE);
    }

    static ObjectNode managedCard(SimCard card, Map<String, String> names, LocalDate today, int order, Locale locale) {
        var publicCard = PublicCard.from(card, today, order);
        var spec = card.getSpec();
        var records = CardFacts.records(spec);
        var sourceById = IntStream.range(0, records.size())
                .boxed()
                .collect(Collectors.toMap(
                        i -> PublicCard.recordId(publicCard.id(), records.get(i), i),
                        records::get,
                        (first, ignored) -> first));
        var payees = accountLabels(spec.getPaymentAccountIds(), spec.getPaymentAccounts(), names);
        var checks = DataChecks.card(spec, publicCard, payees, today, locale);

        ObjectNode result = JSON.valueToTree(publicCard);
        result.put("statusLabel", Messages.text(locale, "status." + publicCard.status()));
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
        result.set("paymentAccounts", JSON.valueToTree(payees));
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
            var accounts = accountLabels(
                    source.getPaymentAccountIds(),
                    present(source.getAccount()) ? List.of(source.getAccount()) : List.of(),
                    names);
            ObjectNode row = JSON.valueToTree(item);
            row.put("title", text(source.getTitle()));
            row.set("accounts", JSON.valueToTree(accounts));
            row.set("checks", JSON.valueToTree(DataChecks.record(source, spec, accounts, today, locale)));
            history.add(row);
        }
        return result;
    }

    private static List<String> accountLabels(List<String> ids, List<String> legacy, Map<String, String> names) {
        var labels = new LinkedHashSet<String>();
        if (ids != null) ids.stream().map(names::get).filter(CardFacts::present).forEach(labels::add);
        if (legacy != null) legacy.stream().filter(CardFacts::present).forEach(labels::add);
        return List.copyOf(labels);
    }

    private static ServerResponse.BodyBuilder privateResponse() {
        return ServerResponse.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .varyBy(HttpHeaders.COOKIE, HttpHeaders.AUTHORIZATION, HttpHeaders.ACCEPT_LANGUAGE);
    }
}
