package com.lowbyyj.textwasher;

import org.commonmark.node.*;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;

/** Remove only syntax identified by the parser; leave source layout and code alone. */
final class MarkdownWasher {
    private final Parser parser = Parser.builder()
            .extensions(List.of(StrikethroughExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build();
    private final UnaryOperator<String> htmlToText;

    MarkdownWasher(UnaryOperator<String> htmlToText) {
        this.htmlToText = htmlToText;
    }

    String wash(String input) {
        String source = input.replace("\r\n", "\n").replace('\r', '\n');
        List<Edit> edits = new ArrayList<>();
        collect(parser.parse(source), source, edits);
        edits.sort(Comparator.comparingInt(e -> e.start));
        StringBuilder result = new StringBuilder(source.length());
        int position = 0;
        for (Edit edit : edits) {
            if (edit.start < position) continue;
            result.append(source, position, edit.start).append(edit.text);
            position = edit.end;
        }
        return result.append(source, position, source.length()).toString();
    }

    private void collect(Node node, String source, List<Edit> edits) {
        List<SourceSpan> spans = node.getSourceSpans();
        if (!spans.isEmpty()) {
            int start = start(node), end = end(node);
            if (node instanceof FencedCodeBlock fence) {
                removeLine(spans.get(0), source, edits);
                if (fence.getClosingFenceLength() != null && spans.size() > 1) {
                    removeLine(spans.get(spans.size() - 1), source, edits);
                }
                return;
            }
            if (node instanceof IndentedCodeBlock) return;
            if (node instanceof Code code) {
                edits.add(new Edit(start, end, code.getLiteral()));
                return;
            }
            if (node instanceof HtmlBlock block) {
                edits.add(new Edit(start, end, htmlToText.apply(block.getLiteral())));
                return;
            }
            if (node instanceof HtmlInline html) {
                edits.add(new Edit(start, end, htmlToText.apply(html.getLiteral())));
                return;
            }
            if (node instanceof LinkReferenceDefinition || node instanceof ThematicBreak) {
                for (SourceSpan span : spans) removeLine(span, source, edits);
                return;
            }
            if (node instanceof Heading) {
                SourceSpan first = spans.get(0);
                int p = first.getInputIndex();
                while (p < end && source.charAt(p) == ' ') p++;
                if (p < end && source.charAt(p) == '#') {
                    while (p < end && source.charAt(p) == '#') p++;
                    while (p < end && (source.charAt(p) == ' ' || source.charAt(p) == '\t')) p++;
                    edits.add(new Edit(start, p, ""));
                    int tail = end;
                    while (tail > p && Character.isWhitespace(source.charAt(tail - 1))) tail--;
                    int hashes = tail;
                    while (hashes > p && source.charAt(hashes - 1) == '#') hashes--;
                    if (hashes < tail && hashes > p && Character.isWhitespace(source.charAt(hashes - 1))) {
                        while (hashes > p && Character.isWhitespace(source.charAt(hashes - 1))) hashes--;
                        edits.add(new Edit(hashes, end, ""));
                    }
                } else if (spans.size() > 1) {
                    removeLine(spans.get(spans.size() - 1), source, edits);
                }
            }
            if (node instanceof Delimited delimited) {
                edits.add(new Edit(start, start + delimited.getOpeningDelimiter().length(), ""));
                edits.add(new Edit(end - delimited.getClosingDelimiter().length(), end, ""));
            }
            if (node instanceof Link || node instanceof Image) {
                if (source.charAt(start) == '<') {
                    edits.add(new Edit(start, start + 1, ""));
                    edits.add(new Edit(end - 1, end, ""));
                    return;
                }
                if (node.getFirstChild() == null || node.getFirstChild().getSourceSpans().isEmpty()) {
                    edits.add(new Edit(start, end, ""));
                    return;
                }
                edits.add(new Edit(start, start(node.getFirstChild()), ""));
                edits.add(new Edit(end(node.getLastChild()), end, ""));
            }
            if (node instanceof Text text && spans.size() == 1) {
                String raw = source.substring(start, end);
                if (!raw.equals(text.getLiteral())) edits.add(new Edit(start, end, text.getLiteral()));
            }
            if (node instanceof HardLineBreak) {
                // A hard break's span contains its backslash or trailing spaces, not the newline.
                edits.add(new Edit(start, end, ""));
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            collect(child, source, edits);
        }
    }

    private static int start(Node node) {
        return node.getSourceSpans().get(0).getInputIndex();
    }

    private static int end(Node node) {
        SourceSpan last = node.getSourceSpans().get(node.getSourceSpans().size() - 1);
        return last.getInputIndex() + last.getLength();
    }

    private static void removeLine(SourceSpan span, String source, List<Edit> edits) {
        int start = span.getInputIndex() - span.getColumnIndex();
        int end = span.getInputIndex() + span.getLength();
        if (end < source.length() && source.charAt(end) == '\n') end++;
        edits.add(new Edit(start, end, ""));
    }

    private record Edit(int start, int end, String text) {}
}
