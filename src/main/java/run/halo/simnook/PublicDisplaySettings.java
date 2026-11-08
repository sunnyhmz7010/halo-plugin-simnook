package run.halo.simnook;

import static java.util.Map.entry;

import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

/** 从 Halo 的 ConfigMap 挑选可公开的展示设置，也为升级前的配置补齐默认值。 */
record PublicDisplaySettings(String title, Map<String, Object> theme) {
    static PublicDisplaySettings from(Map<String, JsonNode> values) {
        var general = values.getOrDefault("general", MissingNode.getInstance());
        var announcement = values.getOrDefault("announcement", MissingNode.getInstance());
        var background = values.getOrDefault("background", MissingNode.getInstance());
        var title = text(general, "sitename", "", 100).strip();
        return new PublicDisplaySettings(
                title.isEmpty() ? "SIMnook" : title,
                Map.ofEntries(
                        entry("defaultLanguage", choice(general, "defaultLanguage", "auto", "auto", "zh-CN", "en")),
                        entry("defaultTheme", choice(general, "defaultTheme", "auto", "auto", "dark", "light")),
                        entry(
                                "defaultCardStyle",
                                choice(general, "defaultCardStyle", "namecard", "namecard", "dial", "bar")),
                        entry(
                                "defaultSort",
                                choice(general, "defaultSort", "renewal", "renewal", "order", "name", "country")),
                        entry(
                                "earthViewMode",
                                choice(
                                        general,
                                        "earthViewMode",
                                        "earth",
                                        "earth",
                                        "earth-stop",
                                        "maps",
                                        "cards",
                                        "hide")),
                        entry("cancelledLast", general.path("cancelledLast").asBoolean(true)),
                        entry(
                                "disablePageAnimation",
                                general.path("disablePageAnimation").asBoolean(false)),
                        entry(
                                "hideAdminEntryWhenLoggedOut",
                                general.path("hideAdminEntryWhenLoggedOut").asBoolean(false)),
                        entry(
                                "visitorInfoCardEnabled",
                                general.path("visitorInfoCardEnabled").asBoolean(true)),
                        entry("alertEnabled", announcement.path("alertEnabled").asBoolean(false)),
                        entry("alertTitle", text(announcement, "alertTitle", "", 100)),
                        entry("alertContent", text(announcement, "alertContent", "", 10_000)),
                        entry(
                                "backgroundEnabled",
                                background.path("backgroundEnabled").asBoolean(false)),
                        entry("backgroundType", choice(background, "backgroundType", "image", "image", "video")),
                        entry("lightBackgroundUrl", text(background, "lightBackgroundUrl", "", 4_096)),
                        entry("darkBackgroundUrl", text(background, "darkBackgroundUrl", "", 4_096)),
                        entry(
                                "backgroundBlur",
                                Math.clamp(background.path("backgroundBlur").asInt(0), 0, 100)),
                        entry(
                                "backgroundOverlay",
                                Math.clamp(background.path("backgroundOverlay").asInt(0), -100, 100))));
    }

    private static String text(JsonNode group, String key, String fallback, int limit) {
        var value = group.path(key);
        if (!value.isString()) {
            return fallback;
        }
        var text = value.asString();
        return text.substring(0, Math.min(text.length(), limit));
    }

    private static String choice(JsonNode group, String key, String fallback, String... choices) {
        var value = text(group, key, fallback, 32);
        return Set.of(choices).contains(value) ? value : fallback;
    }
}
