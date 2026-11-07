package run.halo.simnook;

import static run.halo.simnook.CardFacts.present;
import static run.halo.simnook.CardFacts.text;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/** 公开字段白名单。禁止在这里序列化 CardSpec、ExpenseRecord 或支付账户。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PublicCard(
        String id,
        String name,
        String carrier,
        String last4,
        String numberMasked,
        String region,
        String countryName,
        String typeKind,
        String typeColor,
        String logo,
        List<String> keepAlive,
        List<String> keepAliveCodes,
        int cycleDays,
        String cycleLabel,
        BigDecimal price,
        String currency,
        String expiresAt,
        String nextRenewalAt,
        String nextRenewalEnd,
        String periodStart,
        String status,
        String statusLabel,
        String cancelledAt,
        String firstPurchaseAt,
        BigDecimal totalSpentCNY,
        String urgency,
        int order,
        List<PublicRecord> history) {

    private static final Pattern NAME_SEPARATOR = Pattern.compile("\\s*[·•|]\\s*");
    static final Comparator<SimCard> SOURCE_ORDER = Comparator.comparing(
                    (SimCard card) -> card.getMetadata().getCreationTimestamp(),
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(card -> card.getMetadata().getName());

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PublicRecord(
            String id,
            String type,
            String kind,
            String date,
            BigDecimal amount,
            String currency,
            BigDecimal rate,
            BigDecimal amountCNY,
            String extendsTo) {}

    public static PublicCard from(SimCard card, LocalDate today) {
        return from(card, today, 0);
    }

    static PublicCard from(SimCard card, LocalDate today, int sourceOrder) {
        var spec = card.getSpec();
        var facts = CardFacts.from(spec, today);
        var records = CardFacts.records(spec);
        var cardId = card.getMetadata().getName();
        var history = IntStream.range(0, records.size())
                .boxed()
                .sorted(Comparator.comparing(records::get, CardFacts.RECORD_ORDER.reversed()))
                .map(index -> {
                    var record = records.get(index);
                    return new PublicRecord(
                            recordId(cardId, record, index),
                            text(record.getType()),
                            CardFacts.recordKind(record.getType()),
                            CardFacts.date(record.getDate()),
                            record.getAmount(),
                            currency(record.getCurrency()),
                            record.getRate(),
                            CardFacts.amountCny(record),
                            CardFacts.date(record.getExtendsTo()));
                })
                .toList();
        var nameParts = NAME_SEPARATOR.split(spec.getName(), 3);
        var number = digits(spec.getNumber());
        var tail = number.length() >= 4 ? last4(number) : nameParts.length >= 2 ? last4(digits(nameParts[1])) : "";
        var carrier = present(spec.getCarrier()) ? spec.getCarrier() : spec.getName();
        if (!present(spec.getCarrier()) && nameParts.length > 0 && present(nameParts[0])) {
            carrier = nameParts[0].strip();
        }
        var keepAlive = spec.getKeepAlive() == null ? List.<String>of() : spec.getKeepAlive();
        return new PublicCard(
                cardId,
                spec.getName(),
                carrier,
                tail,
                tail.isEmpty() ? "••••" : "•••• " + tail,
                text(spec.getRegion()),
                text(spec.getCountryName()),
                CardFacts.typeKind(spec),
                CardFacts.typeColor(spec),
                spec.getLogo(),
                keepAlive,
                keepAlive.stream().map(CardFacts::keepAliveCode).toList(),
                CardFacts.cycleDays(spec),
                text(spec.getCycleLabel()),
                Objects.requireNonNullElse(spec.getPrice(), BigDecimal.ZERO),
                currency(spec.getCurrency()),
                facts.expiry(),
                facts.renewal(),
                facts.renewalEnd(),
                facts.periodStart(),
                facts.status(),
                facts.statusLabel(),
                CardFacts.date(spec.getCancelledAt()),
                facts.firstPurchase(),
                history.stream()
                        .map(PublicRecord::amountCNY)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                facts.urgency(),
                Objects.requireNonNullElse(spec.getOrder(), sourceOrder),
                history);
    }

    static String recordId(String cardId, SimCard.ExpenseRecord record, int index) {
        return cardId + "-" + (present(record.getId()) ? record.getId() : index);
    }

    static String digits(String value) {
        return text(value).replaceAll("[^0-9]", "");
    }

    private static String last4(String digits) {
        return digits.substring(Math.max(0, digits.length() - 4));
    }

    private static String currency(String currency) {
        return present(currency) ? currency : "CNY";
    }
}
