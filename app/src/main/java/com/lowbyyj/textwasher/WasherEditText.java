package com.lowbyyj.textwasher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.ContentInfo;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;
import android.widget.Toast;

final class WasherEditText extends EditText {
    private final PasteWasher washer = new PasteWasher();
    private boolean autoCopy = true;

    WasherEditText(Context context) {
        super(context);
        setFilters(new InputFilter[]{(source, start, end, dest, dstart, dend) ->
                source instanceof Spanned ? source.subSequence(start, end).toString() : null});
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

    @Override public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection base = super.onCreateInputConnection(outAttrs);
        if (base == null) return null;
        return new InputConnectionWrapper(base, false) {
            @Override public boolean commitText(CharSequence text, int newCursorPosition) {
                // Some keyboards insert a clipboard chip via commitText instead of Paste.
                // Only recognize an exact clipboard match; ordinary typing is never washed.
                ClipboardManager clipboard = getContext().getSystemService(ClipboardManager.class);
                if (text != null && (text.length() > 1 || text instanceof Spanned) && clipboard.hasPrimaryClip()) {
                    ClipData clip = clipboard.getPrimaryClip();
                    if (clip != null && clip.getItemCount() == 1 && clip.getItemAt(0).getText() != null
                            && text.toString().contentEquals(clip.getItemAt(0).getText())) {
                        paste(clip);
                        return true;
                    }
                }
                return super.commitText(text == null ? "" : text.toString(), newCursorPosition);
            }
        };
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
