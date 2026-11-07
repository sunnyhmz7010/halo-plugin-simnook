package run.halo.simnook;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

final class Messages {
    private Messages() {}

    static Locale locale(String language) {
        return language == null
                        || language.isBlank()
                        || language.toLowerCase(Locale.ROOT).startsWith("zh")
                ? Locale.SIMPLIFIED_CHINESE
                : Locale.ENGLISH;
    }

    static String text(Locale locale, String key, Object... values) {
        var supported = "zh".equals(locale.getLanguage()) ? Locale.SIMPLIFIED_CHINESE : Locale.ENGLISH;
        var messages = ResourceBundle.getBundle("i18n.messages", supported, Messages.class.getClassLoader());
        return new MessageFormat(messages.getString(key), supported).format(values);
    }
}
