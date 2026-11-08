package run.halo.simnook;

import static java.util.Map.entry;
import static run.halo.simnook.CardFacts.present;
import static run.halo.simnook.CardFacts.text;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** SIMerald v0.2.0 中文模板的检查规则。结果属于管理数据。 */
final class DataChecks {
    private record Country(String flag, String zh, String en, String prefix) {}

    private record Rate(String symbol, double min, double max) {}

    private static final List<Country> COUNTRIES = List.of(
            new Country("🇨🇳", "中国", "China", "+86"),
            new Country("🇭🇰", "中国香港", "Hong Kong", "+852"),
            new Country("🇲🇴", "中国澳门", "Macao", "+853"),
            new Country("🇹🇼", "中国台湾", "Taiwan", "+886"),
            new Country("🇯🇵", "日本", "Japan", "+81"),
            new Country("🇰🇷", "韩国", "South Korea", "+82"),
            new Country("🇸🇬", "新加坡", "Singapore", "+65"),
            new Country("🇲🇾", "马来西亚", "Malaysia", "+60"),
            new Country("🇹🇭", "泰国", "Thailand", "+66"),
            new Country("🇵🇭", "菲律宾", "Philippines", "+63"),
            new Country("🇮🇩", "印度尼西亚", "Indonesia", "+62"),
            new Country("🇻🇳", "越南", "Vietnam", "+84"),
            new Country("🇱🇰", "斯里兰卡", "Sri Lanka", "+94"),
            new Country("🇦🇪", "阿联酋", "UAE", "+971"),
            new Country("🇹🇷", "土耳其", "Turkey", "+90"),
            new Country("🇬🇧", "英国", "United Kingdom", "+44"),
            new Country("🇩🇪", "德国", "Germany", "+49"),
            new Country("🇫🇷", "法国", "France", "+33"),
            new Country("🇳🇱", "荷兰", "Netherlands", "+31"),
            new Country("🇪🇸", "西班牙", "Spain", "+34"),
            new Country("🇮🇹", "意大利", "Italy", "+39"),
            new Country("🇵🇱", "波兰", "Poland", "+48"),
            new Country("🇪🇪", "爱沙尼亚", "Estonia", "+372"),
            new Country("🇨🇭", "瑞士", "Switzerland", "+41"),
            new Country("🇺🇸", "美国", "United States", "+1"),
            new Country("🇨🇦", "加拿大", "Canada", "+1"),
            new Country("🇲🇽", "墨西哥", "Mexico", "+52"),
            new Country("🇧🇷", "巴西", "Brazil", "+55"),
            new Country("🇦🇺", "澳大利亚", "Australia", "+61"),
            new Country("🇳🇿", "新西兰", "New Zealand", "+64"));

    private static final Map<String, Rate> RATES = Map.ofEntries(
            entry("HKD", new Rate("HK$", 0.7, 1.1)), entry("MOP", new Rate("MOP$", 0.65, 1.1)),
            entry("TWD", new Rate("NT$", 0.15, 0.3)), entry("JPY", new Rate("¥", 0.03, 0.07)),
            entry("KRW", new Rate("₩", 0.003, 0.008)), entry("SGD", new Rate("S$", 4.5, 6.5)),
            entry("MYR", new Rate("RM", 1.2, 2.1)), entry("THB", new Rate("฿", 0.15, 0.3)),
            entry("PHP", new Rate("₱", 0.08, 0.16)), entry("IDR", new Rate("Rp", 0.0003, 0.0006)),
            entry("VND", new Rate("₫", 0.0002, 0.0004)), entry("LKR", new Rate("Rs", 0.015, 0.035)),
            entry("AED", new Rate("Dh", 1.5, 2.5)), entry("TRY", new Rate("₺", 0.08, 0.35)),
            entry("GBP", new Rate("£", 7.5, 11.5)), entry("EUR", new Rate("€", 6.5, 9.5)),
            entry("PLN", new Rate("zł", 1.4, 2.5)), entry("CHF", new Rate("Fr", 7, 10.5)),
            entry("USD", new Rate("$", 6, 8)), entry("CAD", new Rate("C$", 4, 6.2)),
            entry("MXN", new Rate("Mex$", 0.28, 0.5)), entry("BRL", new Rate("R$", 0.9, 1.8)),
            entry("AUD", new Rate("A$", 3.6, 5.6)), entry("NZD", new Rate("NZ$", 3.3, 5.3)));

    static List<String> card(SimCard.CardSpec spec, PublicCard view, LocalDate today) {
        return card(spec, view, today, Locale.SIMPLIFIED_CHINESE);
    }

    static List<String> card(SimCard.CardSpec spec, PublicCard view, LocalDate today, Locale locale) {
        var warnings = new ArrayList<String>();
        var number = text(spec.getNumber()).replace(" ", "");
        var country = text(spec.getCountryName());
        var prefix = COUNTRIES.stream()
                .filter(item -> item.flag().equals(spec.getRegion())
                        || country.endsWith(item.zh())
                        || country.endsWith(item.en()))
                .map(Country::prefix)
                .findFirst()
                .orElse("");
        if (number.isEmpty()) warnings.add(Messages.text(locale, "checks.numberMissing"));
        else if (!number.matches("\\+[0-9]+")) warnings.add(Messages.text(locale, "checks.numberFormat"));
        else if (!text(spec.getName()).contains(number.substring(Math.max(1, number.length() - 4)))) {
            warnings.add(Messages.text(locale, "checks.nameTail"));
        }
        if (!number.isEmpty() && !prefix.isEmpty() && !number.startsWith(prefix)) {
            warnings.add(Messages.text(locale, "checks.numberCountry", prefix));
        }

        var inactive = !"active".equals(view.status());
        var actions = view.keepAlive();
        if (!inactive) {
            var missing = new ArrayList<String>();
            if (!present(spec.getCountryName()) && !present(spec.getRegion()))
                missing.add(Messages.text(locale, "field.country"));
            if (!present(spec.getTypeLabel()) && !present(spec.getTypeKind()))
                missing.add(Messages.text(locale, "field.type"));
            if (!"virtual".equals(view.typeKind()) && !present(spec.getDevice()))
                missing.add(Messages.text(locale, "field.device"));
            if (actions.isEmpty()) missing.add(Messages.text(locale, "field.keepAlive"));
            if (!present(spec.getCycleLabel()) && spec.getCycleDays() == null)
                missing.add(Messages.text(locale, "field.cycle"));
            if (!missing.isEmpty())
                warnings.add(Messages.text(
                        locale, "checks.requiredFields", String.join(Messages.text(locale, "listSeparator"), missing)));
        }
        if ("expired".equals(view.status())) warnings.add(Messages.text(locale, "checks.expired"));
        if (inactive && spec.getServices() != null && !spec.getServices().isEmpty()) {
            warnings.add(Messages.text(
                    locale,
                    "checks.stillBound",
                    String.join("zh".equals(locale.getLanguage()) ? "," : ", ", spec.getServices())));
        }
        if (!inactive && view.expiresAt() == null) {
            warnings.add(Messages.text(locale, "checks.expiryMissing"));
        }
        var purchases = view.history().stream()
                .filter(record -> "purchase".equals(record.kind()))
                .toList();
        if (purchases.size() != 1)
            warnings.add(Messages.text(locale, "checks.purchaseCount", String.valueOf(purchases.size())));
        var purchaseDay = purchases.stream()
                .map(PublicCard.PublicRecord::date)
                .filter(CardFacts::present)
                .min(String::compareTo)
                .orElse(null);
        if (purchaseDay != null && view.history().stream().anyMatch(r -> earlier(r.date(), purchaseDay))) {
            warnings.add(Messages.text(locale, "checks.beforePurchase"));
        }
        checkedDate(spec.getCancelledAt(), Messages.text(locale, "field.cancelledAt"), warnings, locale);
        checkedDate(spec.getExpiresAt(), Messages.text(locale, "field.expiresAt"), warnings, locale);
        checkedDate(spec.getFirstPurchaseAt(), Messages.text(locale, "field.firstPurchaseAt"), warnings, locale);
        var override =
                checkedDate(spec.getNextRenewalAt(), Messages.text(locale, "field.nextRenewalAt"), warnings, locale);
        var overrideEnd =
                checkedDate(spec.getNextRenewalEnd(), Messages.text(locale, "field.nextRenewalEnd"), warnings, locale);
        var balanceDay = checkedDate(
                spec.getBalanceUpdatedAt(), Messages.text(locale, "field.balanceUpdatedAt"), warnings, locale);
        if (earlier(overrideEnd, override == null ? view.expiresAt() : override))
            warnings.add(Messages.text(locale, "checks.renewalEndBefore"));
        if (override != null) {
            var end = overrideEnd == null ? override : overrideEnd;
            if (earlier(view.expiresAt(), end)) warnings.add(Messages.text(locale, "checks.overrideAfterExpiry"));
            if (earlier(override, view.periodStart())) {
                warnings.add(Messages.text(locale, "checks.overrideBeforePeriod"));
            }
        }
        if ((spec.getPrice() == null) != !present(spec.getCurrency()))
            warnings.add(Messages.text(locale, "checks.priceCurrency"));
        var needsBalance = actions.stream()
                .anyMatch(action -> present(action)
                        && (action.startsWith("有效使用")
                                || action.startsWith("产生余额变动")
                                || action.startsWith("Regular use")
                                || action.startsWith("Balance change")));
        if (!inactive && needsBalance) {
            if (spec.getBalance() == null) warnings.add(Messages.text(locale, "checks.balanceMissing"));
            else if (!positive(spec.getBalance())) warnings.add(Messages.text(locale, "checks.balanceZero"));
            if (!present(spec.getBalanceUpdatedAt())) warnings.add(Messages.text(locale, "checks.balanceDateMissing"));
            else if (balanceDay != null && daysBetween(balanceDay, today.toString()) > 90)
                warnings.add(Messages.text(locale, "checks.balanceStale"));
        }
        return warnings;
    }

    static List<String> record(SimCard.ExpenseRecord record, SimCard.CardSpec card, LocalDate today) {
        return record(record, card, today, Locale.SIMPLIFIED_CHINESE);
    }

    static List<String> record(SimCard.ExpenseRecord record, SimCard.CardSpec card, LocalDate today, Locale locale) {
        var warnings = new ArrayList<String>();
        var day = checkedDate(record.getDate(), Messages.text(locale, "field.recordDate"), warnings, locale);
        var type = record.getType();
        var currency = text(record.getCurrency());
        var kind = CardFacts.recordKind(type);
        var typeLabel = "zh".equals(locale.getLanguage()) || "other".equals(kind)
                ? type
                : Messages.text(locale, "recordKind." + kind);
        var end = checkedDate(record.getExtendsTo(), Messages.text(locale, "field.extendsTo"), warnings, locale);
        if (!present(record.getDate())) warnings.add(Messages.text(locale, "checks.recordDateMissing"));
        else if (earlier(today.toString(), day)) warnings.add(Messages.text(locale, "checks.recordFuture"));
        if (!present(type)) warnings.add(Messages.text(locale, "checks.recordTypeMissing"));
        if (currency.isBlank()) warnings.add(Messages.text(locale, "checks.currencyMissing"));
        if (record.getAmount() == null) warnings.add(Messages.text(locale, "checks.amountMissing"));
        if (!currency.isBlank()
                && !"CNY".equals(currency)
                && positive(record.getAmount())
                && !positive(record.getRate())) {
            warnings.add(Messages.text(locale, "checks.rateMissing"));
        }
        var range = RATES.get(currency);
        var rate = record.getRate();
        if (range != null
                && positive(rate)
                && (rate.compareTo(BigDecimal.valueOf(range.min())) < 0
                        || rate.compareTo(BigDecimal.valueOf(range.max())) > 0)) {
            warnings.add(Messages.text(
                    locale,
                    "checks.rateRange",
                    plain(rate),
                    currency,
                    range.symbol(),
                    plain(BigDecimal.valueOf(range.min())),
                    plain(BigDecimal.valueOf(range.max()))));
        }
        var digits = PublicCard.digits(card.getNumber());
        if (digits.length() >= 4 && !text(record.getTitle()).contains(digits.substring(digits.length() - 4))) {
            warnings.add(Messages.text(locale, "checks.recordTail"));
        }
        var purchase = "purchase".equals(kind);
        var changesExpiry = purchase || "renewal".equals(kind);
        if (changesExpiry && !present(record.getExtendsTo()))
            warnings.add(Messages.text(locale, "checks.recordExpiryMissing", typeLabel));
        if (present(type) && !changesExpiry && present(record.getExtendsTo())) {
            warnings.add(Messages.text(locale, "checks.recordExpiryUnexpected", typeLabel));
        }
        if (present(day) && present(end)) {
            var span = daysBetween(day, end);
            if (span <= 0) warnings.add(Messages.text(locale, "checks.extendsBeforeDate"));
            var cycle = CardFacts.cycleDays(card);
            var limit = purchase ? 1830L : cycle <= 0 ? 400L : cycle + 30L;
            if (span > limit) warnings.add(Messages.text(locale, "checks.extendsTooLong", String.valueOf(limit)));
        }
        return warnings;
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static boolean earlier(String left, String right) {
        return present(left) && present(right) && left.compareTo(right) < 0;
    }

    private static long daysBetween(String start, String end) {
        return ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.parse(end));
    }

    private static String checkedDate(String value, String label, List<String> warnings, Locale locale) {
        var date = CardFacts.date(value);
        if (present(value) && date == null) warnings.add(Messages.text(locale, "checks.invalidDate", label));
        return date;
    }

    private static String plain(BigDecimal number) {
        return number.stripTrailingZeros().toPlainString();
    }
}
