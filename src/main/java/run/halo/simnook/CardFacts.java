package run.halo.simnook;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** 复现 SIMerald v0.2.0 中文模板公式，兼容已有 Halo 卡片的手填覆盖。 */
record CardFacts(
        String expiry,
        String renewal,
        String renewalEnd,
        String status,
        String statusLabel,
        String periodStart,
        String firstPurchase,
        String urgency) {
    private static final List<List<String>> KEEP_ALIVE_VALUES = List.of(
            List.of("topup", "充值余额", "Top up balance"),
            List.of("addon", "购买/启用增值服务", "Buy or enable an add-on"),
            List.of("use", "有效使用（含收发短信）", "Regular use (incl. SMS)"),
            List.of("balanceChange", "产生余额变动（如发漫游短信）", "Balance change (e.g. roaming SMS)"),
            List.of("autoCharge", "自动扣费", "Auto-charge"),
            List.of("plan", "购买续费套餐", "Buy a renewal plan"));

    static String keepAliveCode(String value) {
        var label = text(value).strip();
        return KEEP_ALIVE_VALUES.stream()
                .filter(item -> item.subList(1, item.size()).stream().anyMatch(label::equalsIgnoreCase))
                .map(List::getFirst)
                .findFirst()
                .orElse(null);
    }

    private static final Pattern FIRST_DIGITS = Pattern.compile("[0-9]+");
    private static final Comparator<String> DATES = Comparator.nullsFirst(Comparator.naturalOrder());
    static final Comparator<SimCard.ExpenseRecord> RECORD_ORDER = Comparator.comparing(
                    (SimCard.ExpenseRecord record) -> date(record.getDate()), DATES)
            .thenComparing(record -> date(record.getExtendsTo()), DATES);

    static CardFacts from(SimCard.CardSpec spec, LocalDate today) {
        var records = records(spec);
        var recent = records.stream()
                .filter(record -> date(record.getDate()) != null && date(record.getExtendsTo()) != null)
                .max(RECORD_ORDER)
                .orElse(null);
        var expiry = date(spec.getExpiresAt());
        if (expiry == null && recent != null) expiry = date(recent.getExtendsTo());
        var start = date(spec.getNextRenewalAt());
        if (start == null) start = expiry;
        var end = date(spec.getNextRenewalEnd());
        if (start == null || (end != null && end.compareTo(start) < 0)) end = null;

        var cancelled = Boolean.TRUE.equals(spec.getCancelled()) || date(spec.getCancelledAt()) != null;
        var expired = expiry != null && LocalDate.parse(expiry).isBefore(today);
        var status = cancelled ? "cancelled" : expired ? "expired" : "active";
        var statusLabel = cancelled ? "已注销" : expired ? "已过期" : "使用中";
        var first = date(spec.getFirstPurchaseAt());
        if (first == null) {
            first = records.stream()
                    .map(SimCard.ExpenseRecord::getDate)
                    .map(CardFacts::date)
                    .filter(Objects::nonNull)
                    .min(String::compareTo)
                    .orElse(null);
        }
        return new CardFacts(
                expiry,
                cancelled ? null : start,
                cancelled ? null : end,
                status,
                statusLabel,
                recent == null ? null : date(recent.getDate()),
                first,
                urgency(status, start, end, today));
    }

    static String urgency(String status, String renewal, String end, LocalDate today) {
        return urgency(status, renewal, end, today, Locale.SIMPLIFIED_CHINESE);
    }

    static String urgency(String status, String renewal, String end, LocalDate today, Locale locale) {
        var code = urgencyCode(status, renewal, end, today);
        if ("window".equals(code)) {
            return Messages.text(
                    locale, "urgency.window", String.valueOf(ChronoUnit.DAYS.between(today, LocalDate.parse(end))));
        }
        return Messages.text(locale, "urgency." + code);
    }

    static String urgencyCode(String status, String renewal, String end, LocalDate today) {
        if ("cancelled".equals(status)) return "cancelled";
        if (renewal == null) return "noDate";
        var start = LocalDate.parse(renewal);
        var last = end == null ? start : LocalDate.parse(end);
        if ("expired".equals(status) || last.isBefore(today)) return "overdue";
        if (last.isAfter(start) && !today.isBefore(start)) {
            return "window";
        }
        var days = ChronoUnit.DAYS.between(today, start);
        if (days <= 3) return "days3";
        if (days <= 7) return "days7";
        if (days <= 15) return "days15";
        if (days <= 30) return "days30";
        if (days <= 60) return "days60";
        return "beyond";
    }

    static List<SimCard.ExpenseRecord> records(SimCard.CardSpec spec) {
        return spec.getRecords() == null ? List.of() : spec.getRecords();
    }

    static BigDecimal amountCny(SimCard.ExpenseRecord record) {
        var amount = record.getAmount();
        if (amount == null || amount.signum() == 0) return BigDecimal.ZERO;
        if ("CNY".equals(record.getCurrency())) return amount;
        var rate = record.getRate();
        return rate == null || rate.signum() <= 0 ? null : amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    static String recordKind(String type) {
        var label = text(type).strip().toLowerCase(Locale.ROOT);
        if (label.contains("购卡")
                || label.contains("开卡")
                || label.contains("首次")
                || label.contains("首购")
                || label.contains("purchase")
                || label.contains("activation")
                || label.contains("initial")) return "purchase";
        if (label.contains("续费") || label.contains("保号") || label.contains("renew")) return "renewal";
        if (label.contains("充值")
                || label.contains("top-up")
                || label.contains("top up")
                || label.contains("topup")
                || label.contains("recharge")) return "topup";
        return "other";
    }

    static String typeKind(SimCard.CardSpec spec) {
        if (present(spec.getTypeKind())) return spec.getTypeKind();
        var label = text(spec.getTypeLabel()).toLowerCase(Locale.ROOT);
        if (label.contains("虚拟") || label.contains("virtual") || label.contains("app")) return "virtual";
        if (label.contains("实体") || label.contains("physical")) return "physical";
        if (label.contains("卡托") || label.contains("adapter") || label.contains("estk") || label.contains("euicc"))
            return "adapter";
        return label.contains("esim") ? "embedded" : "physical";
    }

    static int cycleDays(SimCard.CardSpec spec) {
        if (spec.getCycleDays() != null) return spec.getCycleDays();
        var matcher = FIRST_DIGITS.matcher(text(spec.getCycleLabel()));
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException tooLarge) {
            return 0; // 文本里超出整数范围的数字不是有效续费天数。
        }
    }

    static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    static String typeColor(SimCard.CardSpec spec) {
        if (present(spec.getTypeColor())) return spec.getTypeColor();
        return switch (text(spec.getTypeKind())) {
            case "physical" -> "brown";
            case "embedded" -> "blue";
            case "adapter" -> "purple";
            case "virtual" -> "gray";
            default -> "default";
        };
    }

    static String typeLabel(SimCard.CardSpec spec) {
        if (present(spec.getTypeLabel())) return spec.getTypeLabel();
        return switch (text(spec.getTypeKind())) {
            case "physical" -> "实体 SIM";
            case "embedded" -> "eSIM · 直写手机";
            case "adapter" -> "eSIM · 卡托";
            case "virtual" -> "虚拟号码（仅 App）";
            default -> "";
        };
    }

    static String date(String value) {
        if (value == null || value.length() != 10) return null;
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeParseException invalidDate) {
            // Halo 的通用 CRUD 可以保存无效日期，公开视图按缺省处理，管理视图给出检查提示。
            return null;
        }
    }

    static String text(String value) {
        return value == null ? "" : value;
    }
}
