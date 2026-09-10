package com.lowbyyj.textwasher;

import android.content.ClipData;
import android.text.Html;
import android.text.Spanned;
import android.text.style.BulletSpan;

import java.util.regex.Pattern;

final class PasteWasher {
    private static final Pattern HIDDEN_HTML = Pattern.compile(
            "(?is)<!--.*?-->|<(script|style|head)\\b[^>]*>.*?</\\1\\s*>");
    private MarkdownWasher markdown;

    String wash(ClipData clip) {
        if (clip == null) return null;
        StringBuilder result = new StringBuilder();
        boolean found = false;
        for (int i = 0; i < clip.getItemCount(); i++) {
            ClipData.Item item = clip.getItemAt(i);
            String chunk;
            if (item.getHtmlText() != null) {
                chunk = fromHtml(item.getHtmlText());
            } else if (item.getText() != null) {
                if (markdown == null) markdown = new MarkdownWasher(PasteWasher::fromHtml);
                chunk = markdown.wash(item.getText().toString());
            } else {
                // No URI coercion: pasting must never read a provider or fetch remote content.
                continue;
            }
            if (found) result.append('\n');
            result.append(chunk);
            found = true;
        }
        return found ? result.toString() : null;
    }

    static String fromHtml(String html) {
        if (html.matches("(?is)<br\\s*/?>")) return "\n";
        String visible = HIDDEN_HTML.matcher(html).replaceAll("");
        Spanned spanned = Html.fromHtml(visible, Html.FROM_HTML_MODE_COMPACT, null, null);
        StringBuilder text = new StringBuilder(spanned.toString().replace('\u00a0', ' '));
        // BulletSpan is styling too; turn it into a literal marker before dropping spans.
        BulletSpan[] bullets = spanned.getSpans(0, spanned.length(), BulletSpan.class);
        java.util.Arrays.sort(bullets, (a, b) -> Integer.compare(
                spanned.getSpanStart(b), spanned.getSpanStart(a)));
        for (BulletSpan bullet : bullets) text.insert(spanned.getSpanStart(bullet), "- ");
        // Html adds block-ending newlines even when the clipboard did not contain them.
        while (text.length() > 0 && text.charAt(text.length() - 1) == '\n') text.setLength(text.length() - 1);
        return text.toString().replace("\ufffc", "");
    }
}
