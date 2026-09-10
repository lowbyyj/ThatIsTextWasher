package com.lowbyyj.textwasher;

import android.content.*;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.ContentInfo;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("deprecation")
public class AppTest extends ActivityInstrumentationTestCase2<MainActivity> {
    private MainActivity activity;
    private WasherEditText editor;
    private ClipboardManager clipboard;

    public AppTest() { super(MainActivity.class); }

    @Override protected void setUp() throws Exception {
        super.setUp();
        setActivityInitialTouchMode(false);
        activity = getActivity();
        editor = activity.findViewById(android.R.id.edit);
        clipboard = activity.getSystemService(ClipboardManager.class);
        ui(() -> {
            editor.setText("");
            editor.setAutoCopy(true);
            editor.requestFocus();
        });
    }

    private void ui(Runnable action) {
        getInstrumentation().runOnMainSync(action);
        getInstrumentation().waitForIdleSync();
    }

    private String text() {
        AtomicReference<String> result = new AtomicReference<>();
        ui(() -> result.set(editor.getText().toString()));
        return result.get();
    }

    private String clip() {
        AtomicReference<String> result = new AtomicReference<>();
        ui(() -> result.set(clipboard.getPrimaryClip().getItemAt(0).getText().toString()));
        return result.get();
    }

    private void shortcut(int key) {
        long now = SystemClock.uptimeMillis();
        getInstrumentation().sendKeySync(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0, KeyEvent.META_CTRL_ON));
        getInstrumentation().sendKeySync(new KeyEvent(now, now, KeyEvent.ACTION_UP, key, 0, KeyEvent.META_CTRL_ON));
        getInstrumentation().waitForIdleSync();
    }

    public void testTypingAndSelectCopyCutUndo() {
        getInstrumentation().sendStringSync("hello");
        assertEquals("hello", text());
        shortcut(KeyEvent.KEYCODE_A);
        shortcut(KeyEvent.KEYCODE_C);
        assertEquals("hello", clip());
        shortcut(KeyEvent.KEYCODE_X);
        assertEquals("", text());
        shortcut(KeyEvent.KEYCODE_Z);
        assertEquals("hello", text());
    }

    public void testCtrlVPastesOnlyWashedChunkAndUndoIsOneStep() {
        ui(() -> {
            editor.setText("prefix ");
            editor.setSelection(editor.length());
            clipboard.setPrimaryClip(ClipData.newPlainText("external", "# Title\n\n**hello**\n- item\n> quote\n-> result"));
        });
        shortcut(KeyEvent.KEYCODE_V);
        String chunk = "Title\n\nhello\n- item\n> quote\n-> result";
        assertEquals("prefix " + chunk, text());
        assertEquals(chunk, clip());
        shortcut(KeyEvent.KEYCODE_Z);
        assertEquals("prefix ", text());
    }

    public void testPasteMenuAndPlainPasteAreAlwaysWashed() {
        ui(() -> {
            clipboard.setPrimaryClip(ClipData.newPlainText("external", "**one**"));
            editor.onTextContextMenuItem(android.R.id.pasteAsPlainText);
            clipboard.setPrimaryClip(ClipData.newPlainText("external", " _two_"));
            editor.onTextContextMenuItem(android.R.id.paste);
        });
        assertEquals("one two", text());
        assertEquals(" two", clip());
    }

    public void testPlainPasteReplacesSelection() {
        ui(() -> {
            editor.setText("abc def xyz");
            editor.setSelection(4, 7);
            clipboard.setPrimaryClip(ClipData.newPlainText("external", "안녕 🫧\nnext"));
            editor.onTextContextMenuItem(android.R.id.paste);
        });
        assertEquals("abc 안녕 🫧\nnext xyz", text());
        assertEquals("안녕 🫧\nnext", clip());
    }

    public void testHtmlAndStyledPasteDropsFormatting() {
        ui(() -> {
            clipboard.setPrimaryClip(ClipData.newHtmlText("external", "fallback",
                    "<p><b>Hello</b> &amp; <i>world</i></p><p>Next<br>line</p><script>secret()</script>"));
            editor.onTextContextMenuItem(android.R.id.paste);
        });
        assertEquals("Hello & world\nNext\nline", text());
        assertEquals(text(), clip());
        ui(() -> {
            editor.setText("");
            SpannableString styled = new SpannableString("styled");
            styled.setSpan(new StyleSpan(Typeface.BOLD), 0, styled.length(), 0);
            clipboard.setPrimaryClip(ClipData.newPlainText("external", styled));
            editor.onTextContextMenuItem(android.R.id.paste);
            assertEquals(0, editor.getText().getSpans(0, editor.length(), StyleSpan.class).length);
        });
        assertEquals("styled", text());
    }

    public void testAutoCopyOffStillWashes() {
        ui(() -> {
            editor.setAutoCopy(false);
            clipboard.setPrimaryClip(ClipData.newPlainText("external", "**keep clipboard**"));
            editor.onTextContextMenuItem(android.R.id.paste);
        });
        assertEquals("keep clipboard", text());
        assertEquals("**keep clipboard**", clip());
    }

    public void testReceiveContentAndKeyboardClipboardChip() {
        if (Build.VERSION.SDK_INT >= 31) {
            ui(() -> editor.performReceiveContent(new ContentInfo.Builder(
                    ClipData.newPlainText("external", "**received**"), ContentInfo.SOURCE_CLIPBOARD).build()));
            assertEquals("received", text());
        }
        ui(() -> {
            editor.setText("");
            clipboard.setPrimaryClip(ClipData.newPlainText("external", "**chip**"));
            InputConnection connection = editor.onCreateInputConnection(new EditorInfo());
            connection.commitText("**chip**", 1);
        });
        assertEquals("chip", text());
        assertEquals("chip", clip());
    }

    public void testTimestampSaveAndShorterOverwrite() throws Exception {
        ContentResolver resolver = activity.getContentResolver();
        DocumentState fresh = new DocumentState();
        fresh.text = "original longer content\n한국어";
        DocumentState saved = TextFiles.save(resolver, fresh);
        Uri uri = Uri.parse(saved.uri);
        try {
            assertTrue(saved.name.matches("\\d{4}-\\d{2}-\\d{2}_\\d{6}( \\(\\d+\\))?\\.txt"));
            assertEquals(fresh.text, TextFiles.open(resolver, uri).text);
            saved.text = "short";
            DocumentState overwritten = TextFiles.save(resolver, saved);
            assertEquals(saved.uri, overwritten.uri);
            assertEquals("short", TextFiles.open(resolver, uri).text);
            assertFalse(overwritten.dirty);
        } finally { resolver.delete(uri, null, null); }
    }

    public void testOpenIsNotWashedAndRecoveryNeverWritesOriginal() throws Exception {
        ContentResolver resolver = activity.getContentResolver();
        DocumentState fixture = new DocumentState();
        fixture.text = "# Keep this\n**exactly**";
        DocumentState saved = TextFiles.save(resolver, fixture);
        Uri uri = Uri.parse(saved.uri);
        try {
            ui(() -> activity.openUri(uri, 0));
            waitText(fixture.text);
            ui(() -> assertTrue("Opening must return keyboard focus to the editor", editor.hasFocus()));
            shortcut(KeyEvent.KEYCODE_A);
            getInstrumentation().sendStringSync("unsaved changes");
            assertEquals("unsaved changes", text());
            SystemClock.sleep(1000);
            DocumentState recovered = new RecoveryStore(activity).read();
            assertEquals("unsaved changes", recovered.text);
            assertEquals(saved.uri, recovered.uri);
            assertEquals(fixture.text, TextFiles.open(resolver, uri).text);
            ui(() -> {
                editor.append(" before pause");
                getInstrumentation().callActivityOnPause(activity);
            });
            assertEquals("unsaved changes before pause", new RecoveryStore(activity).read().text);
            assertEquals(fixture.text, TextFiles.open(resolver, uri).text);
            ui(() -> getInstrumentation().callActivityOnResume(activity));
            shortcut(KeyEvent.KEYCODE_S);
            long limit = SystemClock.uptimeMillis() + 5000;
            while (!TextFiles.open(resolver, uri).text.equals("unsaved changes before pause")
                    && SystemClock.uptimeMillis() < limit) SystemClock.sleep(50);
            assertEquals("unsaved changes before pause", TextFiles.open(resolver, uri).text);
            ui(() -> assertTrue("Saving must return keyboard focus to the editor", editor.hasFocus()));
        } finally { resolver.delete(uri, null, null); }
    }

    public void testNonTxtRejectedEvenWhenProviderSaysTextPlain() throws Exception {
        ContentResolver resolver = activity.getContentResolver();
        for (String name : new String[]{"rejected.md", "rejected.docx", "rejected.rtf", "rejected.txt.exe"}) {
            Uri uri = Uri.parse("content://com.lowbyyj.textwasher.test.files/" + name);
            assertEquals(name, TextFiles.displayName(resolver, uri));
            try {
                TextFiles.open(resolver, uri);
                fail(name + " must be rejected");
            } catch (TextFiles.FileProblem expected) {
                assertEquals(R.string.txt_only, expected.message);
            }
        }
    }

    public void testReadOnlySaveFailureKeepsWorkingText() throws Exception {
        Uri uri = Uri.parse("content://com.lowbyyj.textwasher.test.files/readonly.txt");
        ui(() -> activity.openUri(uri, 0));
        waitText("**original**");
        ui(() -> editor.setText("still here"));
        shortcut(KeyEvent.KEYCODE_S);
        SystemClock.sleep(300);
        assertEquals("still here", text());
        assertEquals("**original**", TextFiles.open(activity.getContentResolver(), uri).text);
        SystemClock.sleep(700);
        assertEquals("still here", new RecoveryStore(activity).read().text);
    }

    public void testNoInternetOrStoragePermissions() throws Exception {
        PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), PackageManager.GET_PERMISSIONS);
        assertTrue(Arrays.toString(info.requestedPermissions), info.requestedPermissions == null || info.requestedPermissions.length == 0);
        assertEquals(PackageManager.PERMISSION_DENIED, activity.checkSelfPermission("android.permission.INTERNET"));
    }

    private void waitText(String expected) {
        long limit = SystemClock.uptimeMillis() + 5000;
        while (!text().equals(expected) && SystemClock.uptimeMillis() < limit) SystemClock.sleep(50);
        assertEquals(expected, text());
    }
}
