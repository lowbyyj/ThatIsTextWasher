package com.lowbyyj.textwasher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.text.Layout;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import java.util.concurrent.atomic.AtomicReference;

/** Exercise real editor selection and native undo history through input events. */
@SuppressWarnings("deprecation")
public class EditorInteractionTest extends ActivityInstrumentationTestCase2<MainActivity> {
    private EditText editor;
    private ClipboardManager clipboard;

    public EditorInteractionTest() { super(MainActivity.class); }

    @Override protected void setUp() throws Exception {
        super.setUp();
        ImeTestEnvironment.activate(getInstrumentation());
        setActivityInitialTouchMode(false);
        MainActivity activity = getActivity();
        editor = activity.findViewById(android.R.id.edit);
        clipboard = activity.getSystemService(ClipboardManager.class);
        ui(() -> { editor.setText(""); editor.requestFocus(); });
    }

    private void ui(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> {
            try { action.run(); } catch (Throwable error) { failure.set(error); }
        });
        getInstrumentation().waitForIdleSync();
        if (failure.get() instanceof AssertionError error) throw error;
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private void shortcut(int key, boolean shift) {
        int modifiers = KeyEvent.META_CTRL_ON | (shift ? KeyEvent.META_SHIFT_ON : 0);
        long now = SystemClock.uptimeMillis();
        getInstrumentation().sendKeySync(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0, modifiers));
        getInstrumentation().sendKeySync(new KeyEvent(now, now, KeyEvent.ACTION_UP, key, 0, modifiers));
        getInstrumentation().waitForIdleSync();
    }

    private void assertText(String expected) { ui(() -> assertEquals(expected, editor.getText().toString())); }
    private void assertSelection(int anchor, int end) {
        ui(() -> {
            assertEquals("Selection anchor", anchor, editor.getSelectionStart());
            assertEquals("Selection endpoint", end, editor.getSelectionEnd());
        });
    }

    private void paste(String value) {
        ui(() -> clipboard.setPrimaryClip(ClipData.newPlainText("external", value)));
        shortcut(KeyEvent.KEYCODE_V, false);
    }

    private void pointer(int action, int offset, int modifiers, int source, long downTime) {
        Layout layout = editor.getLayout();
        assertNotNull(layout);
        int line = layout.getLineForOffset(offset);
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = source == InputDevice.SOURCE_MOUSE
                ? MotionEvent.TOOL_TYPE_MOUSE : MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords();
        coordinates.x = layout.getPrimaryHorizontal(offset) + editor.getTotalPaddingLeft() - editor.getScrollX();
        coordinates.y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
                + editor.getTotalPaddingTop() - editor.getScrollY();
        coordinates.pressure = action == MotionEvent.ACTION_UP ? 0 : 1;
        int buttons = source == InputDevice.SOURCE_MOUSE && action != MotionEvent.ACTION_UP
                && action != MotionEvent.ACTION_CANCEL ? MotionEvent.BUTTON_PRIMARY : 0;
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 1,
                new MotionEvent.PointerProperties[]{properties}, new MotionEvent.PointerCoords[]{coordinates},
                modifiers, buttons, 1, 1, 0, 0, source, 0);
        try { editor.dispatchTouchEvent(event); } finally { event.recycle(); }
    }

    private void click(int offset, int modifiers, int source) {
        ui(() -> {
            long time = SystemClock.uptimeMillis();
            pointer(MotionEvent.ACTION_DOWN, offset, modifiers, source, time);
            pointer(MotionEvent.ACTION_UP, offset, modifiers, source, time);
        });
    }

    public void testRedoAfterMultipleUndoPreservesClipboard() {
        paste("**one**");
        assertText("one");
        // Insertion and cut form distinct native undo steps; adjacent inserts may merge.
        shortcut(KeyEvent.KEYCODE_A, false);
        shortcut(KeyEvent.KEYCODE_X, false);
        assertText("");
        shortcut(KeyEvent.KEYCODE_Z, false);
        assertText("one");
        shortcut(KeyEvent.KEYCODE_Z, false);
        assertText("");
        ui(() -> clipboard.setPrimaryClip(ClipData.newPlainText("external", "**unrelated**")));
        shortcut(KeyEvent.KEYCODE_Z, true);
        assertText("one");
        shortcut(KeyEvent.KEYCODE_Z, true);
        assertText("");
        shortcut(KeyEvent.KEYCODE_Z, true);
        assertText("");
        ui(() -> assertEquals("**unrelated**", clipboard.getPrimaryClip().getItemAt(0).getText().toString()));
    }

    public void testRedoAfterCutAndAfterNewTyping() {
        getInstrumentation().sendStringSync("hello");
        shortcut(KeyEvent.KEYCODE_A, false);
        shortcut(KeyEvent.KEYCODE_X, false);
        assertText("");
        shortcut(KeyEvent.KEYCODE_Z, false);
        assertText("hello");
        shortcut(KeyEvent.KEYCODE_Z, true);
        assertText("");
        shortcut(KeyEvent.KEYCODE_Z, false);
        ui(() -> editor.setSelection(editor.length()));
        getInstrumentation().sendStringSync("!");
        shortcut(KeyEvent.KEYCODE_Z, true);
        assertText("hello!");
    }

    public void testRedoRestoresCommittedHangul() {
        ui(() -> {
            var connection = editor.onCreateInputConnection(new EditorInfo());
            assertNotNull(connection);
            connection.setComposingText("ㅎ", 1);
            connection.setComposingText("하", 1);
            connection.setComposingText("한", 1);
            connection.commitText("한", 1);
        });
        assertText("한");
        shortcut(KeyEvent.KEYCODE_Z, false);
        assertText("");
        shortcut(KeyEvent.KEYCODE_Z, true);
        assertText("한");
    }

    public void testPointerSelectionKeepsAnchorInBothDirections() {
        ui(() -> { editor.setText("alpha beta gamma"); editor.setSelection(6); });
        click(10, KeyEvent.META_SHIFT_ON, InputDevice.SOURCE_MOUSE);
        assertSelection(6, 10);
        click(2, KeyEvent.META_SHIFT_ON, InputDevice.SOURCE_MOUSE);
        assertSelection(6, 2);
        click(16, KeyEvent.META_SHIFT_ON, InputDevice.SOURCE_MOUSE);
        assertSelection(6, 16);
        click(6, KeyEvent.META_SHIFT_ON, InputDevice.SOURCE_MOUSE);
        assertSelection(6, 6);
    }

    public void testPointerSelectionAcrossLinesAndReplacementUndoRedo() {
        String value = "한글 😀 입력\n둘째 줄 끝";
        int anchor = value.indexOf("입력");
        int end = value.indexOf(" 끝");
        ui(() -> { editor.setText(value); editor.setSelection(anchor); });
        click(end, KeyEvent.META_SHIFT_ON, InputDevice.SOURCE_MOUSE);
        assertSelection(anchor, end);
        shortcut(KeyEvent.KEYCODE_C, false);
        ui(() -> assertEquals(value.substring(anchor, end), clipboard.getPrimaryClip().getItemAt(0).getText().toString()));
        paste("**replacement**");
        String replacement = value.substring(0, anchor) + "replacement" + value.substring(end);
        assertText(replacement);
        shortcut(KeyEvent.KEYCODE_Z, false);
        assertText(value);
        shortcut(KeyEvent.KEYCODE_Z, true);
        assertText(replacement);
    }

    public void testPointerSelectionOnScrolledWrappedText() {
        String value = ("한글 English words wrap across the editor width. ").repeat(100);
        ui(() -> editor.setText(value));
        int[] offsets = new int[2];
        ui(() -> {
            Layout layout = editor.getLayout();
            offsets[0] = layout.getLineStart(40) + 2;
            offsets[1] = layout.getLineStart(42) + 4;
            editor.setSelection(offsets[0]);
            editor.scrollTo(0, layout.getLineTop(39));
        });
        click(offsets[1], KeyEvent.META_SHIFT_ON, InputDevice.SOURCE_MOUSE);
        assertSelection(offsets[0], offsets[1]);
    }

    public void testPointerGestureReleaseAndPlainClick() {
        ui(() -> { editor.setText("alpha beta gamma"); editor.setSelection(6); });
        ui(() -> {
            long time = SystemClock.uptimeMillis();
            pointer(MotionEvent.ACTION_DOWN, 10, KeyEvent.META_SHIFT_ON, InputDevice.SOURCE_MOUSE, time);
            pointer(MotionEvent.ACTION_MOVE, 16, KeyEvent.META_SHIFT_ON, InputDevice.SOURCE_MOUSE, time);
            pointer(MotionEvent.ACTION_UP, 16, 0, InputDevice.SOURCE_MOUSE, time);
        });
        assertSelection(6, 16);
        click(3, 0, InputDevice.SOURCE_MOUSE);
        assertSelection(3, 3);
    }

    public void testCancelledPointerGestureDoesNotCaptureTouch() {
        ui(() -> { editor.setText("alpha beta gamma"); editor.setSelection(6); });
        ui(() -> {
            long time = SystemClock.uptimeMillis();
            pointer(MotionEvent.ACTION_DOWN, 10, KeyEvent.META_SHIFT_ON, InputDevice.SOURCE_MOUSE, time);
            pointer(MotionEvent.ACTION_CANCEL, 10, 0, InputDevice.SOURCE_MOUSE, time);
        });
        click(2, 0, InputDevice.SOURCE_TOUCHSCREEN);
        assertSelection(2, 2);
    }
}
