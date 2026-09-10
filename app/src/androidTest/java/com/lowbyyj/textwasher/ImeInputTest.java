package com.lowbyyj.textwasher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.text.SpannableString;
import android.text.Spanned;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;

import java.util.concurrent.atomic.AtomicReference;

/** Exercise the IME composition protocol, not precomposed Unicode pasted into the editor. */
@SuppressWarnings("deprecation")
public class ImeInputTest extends ActivityInstrumentationTestCase2<MainActivity> {
    private EditText editor;
    private InputConnection connection;
    private ClipboardManager clipboard;

    public ImeInputTest() { super(MainActivity.class); }

    @Override protected void setUp() throws Exception {
        super.setUp();
        ImeTestEnvironment.activate(getInstrumentation());
        setActivityInitialTouchMode(false);
        MainActivity activity = getActivity();
        editor = activity.findViewById(android.R.id.edit);
        clipboard = activity.getSystemService(ClipboardManager.class);
        ui(() -> {
            editor.setText("");
            editor.requestFocus();
            clipboard.setPrimaryClip(ClipData.newPlainText("external", "untouched clipboard"));
            connection = editor.onCreateInputConnection(new EditorInfo());
            assertNotNull(connection);
        });
    }

    private void ui(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> {
            try { action.run(); }
            catch (Throwable error) { failure.set(error); }
        });
        getInstrumentation().waitForIdleSync();
        if (failure.get() instanceof AssertionError error) throw error;
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private void assertText(String expected) {
        assertEquals(expected, editor.getText().toString());
    }

    private void assertComposing(int start, int end) {
        assertEquals(start, BaseInputConnection.getComposingSpanStart(editor.getText()));
        assertEquals(end, BaseInputConnection.getComposingSpanEnd(editor.getText()));
    }

    public void testHangulCompositionReplacesPreviousSyllable() {
        ui(() -> {
            assertTrue(connection.setComposingText("ㅋ", 1));
            assertTrue(connection.setComposingText("키", 1));
            assertText("키");
            for (String update : new String[]{"킵", "키보", "키보ㄷ", "키보드"}) {
                connection.setComposingText(update, 1);
                assertText(update);
                assertComposing(0, update.length());
            }
            connection.commitText("키보드", 1);
            assertText("키보드");
            assertComposing(-1, -1);
        });
    }

    public void testHangulSentenceAcrossCommittedWords() {
        ui(() -> {
            String[][] words = {
                    {"ㅋ", "키", "킵", "키보", "키보ㄷ", "키보드"},
                    {"ㅁ", "머", "멀", "멅", "멀티"},
                    {"ㅇ", "이", "입", "입ㄹ", "입려", "입력"},
                    {"ㅇ", "이", "잇", "이슈"}
            };
            String prefix = "";
            for (int i = 0; i < words.length; i++) {
                String committed = words[i][words[i].length - 1] + (i < words.length - 1 ? " " : "");
                connection.beginBatchEdit();
                try {
                    for (String update : words[i]) {
                        connection.setComposingText(update, 1);
                        assertText(prefix + update);
                    }
                    connection.commitText(committed, 1);
                } finally {
                    connection.endBatchEdit();
                }
                prefix += committed;
                assertText(prefix);
            }
            assertText("키보드 멀티 입력 이슈");
            assertComposing(-1, -1);
            assertEquals("untouched clipboard", clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        });
    }

    public void testComposingRegionAndCommitCursor() {
        ui(() -> {
            editor.setText("앞 키보드 뒤");
            connection.setComposingRegion(2, 5);
            connection.setComposingText("키", 1);
            assertText("앞 키 뒤");
            connection.setComposingText("키보드", 1);
            assertText("앞 키보드 뒤");
            connection.commitText("입력", 0);
            assertText("앞 입력 뒤");
            assertEquals(2, editor.getSelectionStart());
            assertComposing(-1, -1);
        });
    }

    public void testCompositionCanShrinkToEmpty() {
        ui(() -> {
            editor.setText("앞 ");
            editor.setSelection(editor.length());
            for (String update : new String[]{"입력", "입려", "입ㄹ", "입", "이", "ㅇ", ""}) {
                connection.setComposingText(update, 1);
                assertText("앞 " + update);
            }
            connection.finishComposingText();
            assertText("앞 ");
            assertComposing(-1, -1);
        });
    }

    public void testKeyboardComposingSpansArePreserved() {
        ui(() -> {
            SpannableString composing = new SpannableString("한");
            Object marker = new Object();
            composing.setSpan(marker, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | Spanned.SPAN_COMPOSING);
            connection.setComposingText(composing, 1);
            assertEquals(0, editor.getText().getSpanStart(marker));
            assertComposing(0, 1);
            connection.setComposingText("한글", 1);
            assertText("한글");
            connection.finishComposingText();
            assertText("한글");
            assertComposing(-1, -1);
        });
    }

    public void testCommitMatchingClipboardIsStillTyping() {
        ui(() -> {
            clipboard.setPrimaryClip(ClipData.newPlainText("external", "**키보드**"));
            editor.setText("앞 ");
            editor.setSelection(editor.length());
            connection.setComposingText("키보", 1);
            connection.commitText("**키보드**", 1);
            assertText("앞 **키보드**");
            assertComposing(-1, -1);
            assertEquals("**키보드**", clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        });
    }

    public void testExplicitPasteBetweenComposedWords() {
        ui(() -> {
            connection.setComposingText("ㅋ", 1);
            connection.setComposingText("키", 1);
            connection.finishComposingText();
            clipboard.setPrimaryClip(ClipData.newPlainText("external", "**hello**"));
            editor.onTextContextMenuItem(android.R.id.paste);
            assertText("키hello");
            assertEquals("hello", clipboard.getPrimaryClip().getItemAt(0).getText().toString());
            connection.setComposingText("ㅂ", 1);
            connection.setComposingText("보", 1);
            connection.commitText("보", 1);
            assertText("키hello보");
        });
    }

    public void testRecoveryDebounceDoesNotEndComposition() {
        ui(() -> connection.setComposingText("키", 1));
        SystemClock.sleep(1000);
        ui(() -> {
            connection.setComposingText("키보드", 1);
            assertText("키보드");
            assertComposing(0, 3);
            connection.commitText("키보드", 1);
            assertText("키보드");
        });
    }
}
