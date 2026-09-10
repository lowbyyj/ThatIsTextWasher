package com.lowbyyj.textwasher;

import org.junit.Test;
import static org.junit.Assert.*;

public class MarkdownWasherTest {
    private final MarkdownWasher washer = new MarkdownWasher(html -> html.replaceAll("<[^>]+>", ""));

    @Test public void briefExamples() {
        assertEquals("hello", washer.wash("**hello**"));
        assertEquals("Title", washer.wash("# Title"));
        assertEquals("OpenAI", washer.wash("[OpenAI](https://openai.com)"));
        assertEquals("- item\n1. item\n> quote\n-> result", washer.wash("- item\n1. item\n> quote\n-> result"));
    }

    @Test public void keepsPlainLayoutAndUnicode() {
        String text = "  안녕하세요 🫧\n\n\nhello\tworld  \nlast line\n";
        // Trailing double spaces are a Markdown hard break; preserve its newline.
        assertEquals(text.replace("world  \n", "world\n"), washer.wash(text));
        assertEquals("a\n\nb\n", washer.wash("a\r\n\r\nb\r"));
    }

    @Test public void nestedInlineFormatting() {
        assertEquals("one two three", washer.wash("***one*** **two _three_**"));
        assertEquals("outer inner", washer.wash("[**outer** _inner_](https://example.com/a_(b) \"title\")"));
        assertEquals("before after", washer.wash("~~before~~ after"));
    }

    @Test public void keepsLiteralUnderscoresAndMath() {
        assertEquals("snake_case a_b_c 2 * 3 x -> y", washer.wash("snake_case a_b_c 2 * 3 x -> y"));
        assertEquals("**unfinished", washer.wash("**unfinished"));
        assertEquals("*literal* & <", washer.wash("\\*literal\\* &amp; &lt;"));
    }

    @Test public void fencesDoNotWashCode() {
        assertEquals("  **literal**\n<p>x</p>\n", washer.wash("```html\n  **literal**\n<p>x</p>\n```"));
        assertEquals("print(1)\n", washer.wash("~~~python\nprint(1)\n~~~\n"));
        assertEquals("hello\n", washer.wash("```\nhello\n"));
        assertEquals("", washer.wash("```\n```"));
    }

    @Test public void inlineCodeIsLiteral() {
        assertEquals("**keep**", washer.wash("`**keep**`"));
        assertEquals("a ` b", washer.wash("`` a ` b ``"));
        assertEquals("    **keep**\n", washer.wash("    **keep**\n"));
    }

    @Test public void headingsAndListsKeepStructure() {
        assertEquals("Title\n\n- bold\n  - nested\n\n> quote\n> next", washer.wash(
                "## Title ##\n\n- **bold**\n  - _nested_\n\n> **quote**\n> next"));
        assertEquals("Title\n\nbody", washer.wash("Title\n=====\n\nbody"));
        assertEquals("> code\n", washer.wash("> ```\n> code\n> ```"));
    }

    @Test public void linksImagesReferencesAndAutolinks() {
        assertEquals("alt", washer.wash("![alt](photo.png)"));
        assertEquals("", washer.wash("![](photo.png)"));
        assertEquals("Example\n\n", washer.wash("[Example][ref]\n\n[ref]: https://example.com"));
        assertEquals("https://example.com", washer.wash("<https://example.com>"));
        assertEquals("hello@example.com", washer.wash("<hello@example.com>"));
    }

    @Test public void hardBreaksAndMultilineEmphasis() {
        assertEquals("first\nsecond", washer.wash("first\\\nsecond"));
        assertEquals("first\nsecond", washer.wash("**first\nsecond**"));
        assertEquals("first\n> second", washer.wash("> **first\n> second**").substring(2));
    }

    @Test public void preservesEmptyInput() {
        assertEquals("", washer.wash(""));
        assertEquals("\n\n", washer.wash("\n\n"));
    }
}
