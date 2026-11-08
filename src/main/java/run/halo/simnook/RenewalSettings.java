package run.halo.simnook;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

/** 提醒与号卡视图共用业务时区，避免容器时区影响日期分档。 */
record RenewalSettings(LocalTime sendTime, ZoneId zone, int daysAhead) {
    static RenewalSettings from(Map<String, JsonNode> values) {
        var group = values.getOrDefault("reminders", MissingNode.getInstance());
        var time = group.path("sendTime").asString("09:00");
        var zone = group.path("timezone").asString("Asia/Shanghai");
        var days = group.path("daysAhead").asInt(7);
        if (!time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]") || days < 1 || days > 60) {
            throw new IllegalArgumentException("提醒时间必须为 HH:mm，提醒范围必须在 1 到 60 天之间");
        }
        return new RenewalSettings(LocalTime.parse(time), ZoneId.of(zone), days);
    }

    LocalDate today() {
        return today(Clock.systemUTC());
    }

    LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(zone));
    }

    boolean includes(CardFacts facts, LocalDate today) {
        if ("cancelled".equals(facts.status()) || facts.renewal() == null) return false;
        var code = CardFacts.urgencyCode(facts.status(), facts.renewal(), facts.renewalEnd(), today);
        return "overdue".equals(code)
                || "window".equals(code)
                || !LocalDate.parse(facts.renewal()).isAfter(today.plusDays(daysAhead));
    }
}
