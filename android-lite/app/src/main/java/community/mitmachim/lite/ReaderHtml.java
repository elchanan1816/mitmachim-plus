package community.mitmachim.lite;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Handles the forum's collapsed spoilers without a JavaScript/WebView dependency. */
final class ReaderHtml {
    private ReaderHtml() { }
    static boolean hasSpoiler(String html) {
        return html.contains("extended-markdown-spoiler") || html.contains("spoiler\"") || html.contains("spoiler'") || html.contains("<details");
    }
    static String prepare(String html, boolean reveal) {
        String value = html.replaceAll("(?is)<button\\b[^>]*extended-markdown-spoiler[^>]*>.*?</button\\s*>", reveal ? "" : "<p><b>[ספוילר מוסתר]</b></p>");
        if (reveal) return value;
        value = stripHidden(value, "div", true);
        return stripHidden(value, "details", false);
    }
    private static String stripHidden(String html, String tag, boolean checkClass) {
        Matcher matcher = Pattern.compile("(?is)<(/?)" + tag + "\\b[^>]*>").matcher(html);
        StringBuilder visible = new StringBuilder(); int copied = 0, depth = 0;
        while (matcher.find()) {
            boolean closing = !matcher.group(1).isEmpty();
            if (depth == 0 && !closing && (!checkClass || hiddenClass(matcher.group()))) {
                visible.append(html, copied, matcher.start()); depth = 1;
                if (!checkClass) visible.append("<p><b>[ספוילר מוסתר]</b></p>");
            } else if (depth > 0) {
                depth += closing ? -1 : 1;
                if (depth == 0) copied = matcher.end();
            }
        }
        if (depth == 0) visible.append(html, copied, html.length());
        // Malformed hidden markup remains hidden; do not accidentally reveal it.
        return visible.toString();
    }
    private static boolean hiddenClass(String tag) {
        Matcher classes = Pattern.compile("(?is)\\bclass\\s*=\\s*(['\"])(.*?)\\1").matcher(tag);
        if (!classes.find()) return false;
        for (String name : classes.group(2).split("\\s+")) if (name.equals("collapse") || name.equals("spoiler")) return true;
        return false;
    }
}
