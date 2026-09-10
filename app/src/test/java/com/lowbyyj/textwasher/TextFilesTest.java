package com.lowbyyj.textwasher;

import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class TextFilesTest {
    @Test public void acceptsOnlyTxtExtension() {
        assertTrue(TextFiles.isTxt("scratch.txt"));
        assertTrue(TextFiles.isTxt("SCRATCH.TXT"));
        for (String name : new String[]{"a.md", "a.doc", "a.docx", "a.rtf", "a.txt.exe", "a", "a.txt "}) {
            assertFalse(name, TextFiles.isTxt(name));
        }
        assertFalse(TextFiles.isTxt(null));
    }

    @Test public void utf8PreservesContentInsteadOfWashing() throws Exception {
        String raw = "# Not a heading\r\n**한국어** 🫧\r\n";
        DocumentState state = TextFiles.decode(raw.getBytes(StandardCharsets.UTF_8));
        assertEquals(raw, state.text);
        assertFalse(state.bom);
    }

    @Test public void detectsUtf8AndUtf16Bom() throws Exception {
        DocumentState utf8 = TextFiles.decode(new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf,65});
        assertEquals("A", utf8.text);
        assertTrue(utf8.bom);
        DocumentState le = TextFiles.decode(new byte[]{(byte)0xff,(byte)0xfe,65,0});
        assertEquals("A", le.text);
        assertEquals("UTF-16LE", le.encoding);
        DocumentState be = TextFiles.decode(new byte[]{(byte)0xfe,(byte)0xff,0,65});
        assertEquals("A", be.text);
        assertEquals("UTF-16BE", be.encoding);
    }

    @Test(expected = TextFiles.FileProblem.class) public void rejectsBrokenEncoding() throws Exception {
        TextFiles.decode(new byte[]{(byte)0xff});
    }

    @Test(expected = TextFiles.FileProblem.class) public void rejectsBinary() throws Exception {
        TextFiles.decode(new byte[]{0,1,2});
    }

    @Test public void filenameHasFullTimestamp() {
        assertTrue(TextFiles.timestampName().matches("\\d{4}-\\d{2}-\\d{2}_\\d{6}\\.txt"));
    }
}
