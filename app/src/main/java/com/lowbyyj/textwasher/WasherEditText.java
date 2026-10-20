package com.lowbyyj.textwasher;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.view.DragEvent;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.Toast;

final class WasherEditText extends EditText {
    private final PasteWasher washer = new PasteWasher();
    private boolean autoCopy = true;
    private int mouseSelectionAnchor = -1;

    WasherEditText(Context context) {
        super(context);
        // Keep the native input connection and its composing spans intact. Span removal
        // belongs to the explicit paste path; doing it to IME updates duplicates syllables.
        if (Build.VERSION.SDK_INT >= 31) {
            setOnReceiveContentListener(new String[]{"text/*"}, (view, payload) -> {
                paste(payload.getClip());
                return null;
            });
        }
    }

    void setAutoCopy(boolean enabled) { autoCopy = enabled; }
    boolean isAutoCopy() { return autoCopy; }

    @Override public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            ClipboardManager clipboard = getContext().getSystemService(ClipboardManager.class);
            paste(clipboard.getPrimaryClip());
            return true;
        }
        return super.onTextContextMenuItem(id);
    }

    @Override public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        if (event.isCtrlPressed() && !event.isAltPressed() && keyCode == KeyEvent.KEYCODE_V) {
            return onTextContextMenuItem(android.R.id.paste);
        }
        return super.onKeyShortcut(keyCode, event);
    }

    // Native EditText retains click and accessibility handling; this only extends a selection.
    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mouseSelectionAnchor = -1;
            boolean shift = (KeyEvent.normalizeMetaState(event.getMetaState()) & KeyEvent.META_SHIFT_ON) != 0;
            if (isEnabled() && getLayout() != null && event.isFromSource(InputDevice.SOURCE_MOUSE)
                    && event.isButtonPressed(MotionEvent.BUTTON_PRIMARY) && shift) {
                int anchor = Math.max(0, getSelectionStart());
                if (requestFocus()) {
                    mouseSelectionAnchor = anchor;
                    cancelLongPress();
                }
            }
        }
        if (mouseSelectionAnchor >= 0 && event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            // Preserve the anchor, including backward selections. Consume the release too,
            // so the native single-click handler cannot collapse the range afterwards.
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE
                    || action == MotionEvent.ACTION_UP) {
                setSelection(Math.min(mouseSelectionAnchor, length()),
                        getOffsetForPosition(event.getX(), event.getY()));
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mouseSelectionAnchor = -1;
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override public boolean onDragEvent(DragEvent event) {
        // Keep internal selection moves native; external text drops use the same wash path.
        if (event.getLocalState() != null) return super.onDragEvent(event);
        if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) {
            return event.getClipDescription() != null && event.getClipDescription().hasMimeType("text/*");
        }
        if (event.getAction() == DragEvent.ACTION_DROP) {
            setSelection(getOffsetForPosition(event.getX(), event.getY()));
            paste(event.getClipData());
            return true;
        }
        return true;
    }

    void paste(ClipData clip) {
        String cleaned = washer.wash(clip);
        if (cleaned == null) return;
        int first = Math.max(0, Math.min(getSelectionStart(), getSelectionEnd()));
        int last = Math.max(first, Math.max(getSelectionStart(), getSelectionEnd()));
        beginBatchEdit();
        try {
            getText().replace(first, last, cleaned);
            setSelection(first + cleaned.length());
        } finally {
            endBatchEdit();
        }
        if (autoCopy) {
            try {
                getContext().getSystemService(ClipboardManager.class)
                        .setPrimaryClip(ClipData.newPlainText("", cleaned));
            } catch (RuntimeException ignored) {
                Toast.makeText(getContext(), R.string.clipboard_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
