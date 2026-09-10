package com.lowbyyj.textwasher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int OPEN_FILE = 1;
    private static final ExecutorService FILE_IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private Session session;
    private RecoveryStore recovery;
    private SharedPreferences preferences;
    private WasherEditText editor;
    private TextView filename;
    private Button open;
    private Button save;
    private boolean binding;
    private final Runnable recover = () -> {
        capture();
        recovery.saveLater(session.state.copy(), () -> MAIN.post(() -> {
            if (!isDestroyed()) toast(R.string.recovery_failed);
        }));
    };

    /** Retain a pending file operation across rotation, without restarting it or losing its URI. */
    private static final class Session {
        DocumentState state = new DocumentState();
        MainActivity screen;
        boolean busy;
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        recovery = new RecoveryStore(getApplicationContext());
        preferences = getSharedPreferences("options", MODE_PRIVATE);
        session = (Session) getLastNonConfigurationInstance();
        if (session == null) {
            session = new Session();
            try { session.state = recovery.read(); }
            catch (IOException error) { toast(R.string.recovery_read_failed); }
        }
        session.screen = this;
        buildScreen();
        bindDocument();
        editor.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable text) {
                if (binding) return;
                session.state.dirty = true;
                MAIN.removeCallbacks(recover);
                MAIN.postDelayed(recover, 700);
            }
        });
        editor.requestFocus();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.paper));
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                        | WindowInsets.Type.ime());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return WindowInsets.CONSUMED;
            });
            boolean light = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    != Configuration.UI_MODE_NIGHT_YES;
            int appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            root.post(() -> {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) controller.setSystemBarsAppearance(light ? appearance : 0, appearance);
            });
        }
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), 0, 0, 0);
        filename = new TextView(this);
        filename.setTextColor(getColor(R.color.ink));
        filename.setTextSize(14);
        filename.setSingleLine();
        filename.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        bar.addView(filename, new LinearLayout.LayoutParams(0, dp(52), 1));
        filename.setGravity(Gravity.CENTER_VERTICAL);
        open = button(R.string.open);
        open.setOnClickListener(view -> requestOpen());
        bar.addView(open);
        save = button(R.string.save);
        save.setOnClickListener(view -> save(null));
        bar.addView(save);
        Button more = button(R.string.more);
        more.setText("⋮");
        more.setTextSize(22);
        more.setContentDescription(getString(R.string.more));
        more.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(52)));
        more.setOnClickListener(view -> {
            PopupMenu menu = new PopupMenu(this, more);
            menu.getMenu().add(R.string.auto_copy).setCheckable(true).setChecked(editor.isAutoCopy());
            menu.setOnMenuItemClickListener(item -> {
                boolean enabled = !editor.isAutoCopy();
                editor.setAutoCopy(enabled);
                preferences.edit().putBoolean("auto_copy", enabled).apply();
                return true;
            });
            menu.show();
        });
        bar.addView(more);
        root.addView(bar);
        View line = new View(this);
        line.setBackgroundColor(getColor(R.color.rule));
        root.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        editor = new WasherEditText(this);
        editor.setId(android.R.id.edit);
        editor.setSaveEnabled(false); // Large documents belong in the recovery file, not a Binder bundle.
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setTextSize(16);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextColor(getColor(R.color.ink));
        editor.setBackground(null);
        editor.setPadding(dp(16), dp(14), dp(16), dp(20));
        editor.setContentDescription(getString(R.string.editor));
        editor.setAutoCopy(preferences.getBoolean("auto_copy", true));
        root.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private Button button(int text) {
        Button button = new Button(this, null, android.R.attr.borderlessButtonStyle);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(getColor(R.color.ink));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(60), dp(52)));
        return button;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void bindDocument() {
        binding = true;
        editor.setText(session.state.text);
        int length = editor.length();
        editor.setSelection(Math.max(0, Math.min(session.state.selectionStart, length)),
                Math.max(0, Math.min(session.state.selectionEnd, length)));
        int scroll = session.state.scrollY;
        editor.post(() -> editor.scrollTo(0, scroll));
        binding = false;
        bindHeader();
    }

    private void bindHeader() {
        filename.setText(session.state.name.isEmpty() ? getString(R.string.untitled) : session.state.name);
        open.setEnabled(!session.busy);
        save.setEnabled(!session.busy);
        editor.setEnabled(!session.busy);
        if (!session.busy) editor.requestFocus();
    }

    private void capture() {
        session.state.text = editor.getText().toString();
        session.state.selectionStart = editor.getSelectionStart();
        session.state.selectionEnd = editor.getSelectionEnd();
        session.state.scrollY = editor.getScrollY();
    }

    private void requestOpen() {
        if (session.busy) return;
        if (session.state.dirty) {
            new AlertDialog.Builder(this).setMessage(R.string.unsaved)
                    .setPositiveButton(R.string.save, (dialog, which) -> save(this::launchPicker))
                    .setNegativeButton(R.string.discard, (dialog, which) -> launchPicker())
                    .setNeutralButton(R.string.cancel, null).show();
        } else launchPicker();
    }

    private void launchPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("text/plain")
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, OPEN_FILE); }
        catch (android.content.ActivityNotFoundException error) { toast(R.string.open_failed); }
    }

    @Override public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != OPEN_FILE || result != RESULT_OK || data == null || data.getData() == null) return;
        openUri(data.getData(), data.getFlags());
    }

    void openUri(Uri uri, int flags) {
        if (session.busy) return;
        capture();
        session.busy = true;
        bindHeader();
        Session pending = session;
        android.content.ContentResolver resolver = getContentResolver();
        RecoveryStore store = recovery;
        FILE_IO.execute(() -> {
            try {
                DocumentState opened = TextFiles.open(resolver, uri);
                int grants = flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if ((flags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
                    resolver.takePersistableUriPermission(uri, grants);
                }
                MAIN.post(() -> {
                    String previous = pending.state.uri;
                    pending.state = opened;
                    pending.busy = false;
                    if (pending.screen != null) pending.screen.bindDocument();
                    persist(store, pending);
                    if (!previous.isEmpty() && !previous.equals(opened.uri)) {
                        releaseGrant(resolver, Uri.parse(previous));
                    }
                });
            } catch (IOException | RuntimeException error) {
                fail(pending, error, R.string.open_failed);
            }
        });
    }

    private static void releaseGrant(android.content.ContentResolver resolver, Uri uri) {
        for (android.content.UriPermission permission : resolver.getPersistedUriPermissions()) {
            if (permission.getUri().equals(uri)) {
                int flags = (permission.isReadPermission() ? Intent.FLAG_GRANT_READ_URI_PERMISSION : 0)
                        | (permission.isWritePermission() ? Intent.FLAG_GRANT_WRITE_URI_PERMISSION : 0);
                try { resolver.releasePersistableUriPermission(uri, flags); }
                catch (SecurityException ignored) { /* Grant was already revoked. */ }
            }
        }
    }

    void save(Runnable after) {
        if (session.busy) return;
        capture();
        session.busy = true;
        bindHeader();
        Session pending = session;
        DocumentState document = session.state.copy();
        android.content.ContentResolver resolver = getContentResolver();
        RecoveryStore store = recovery;
        FILE_IO.execute(() -> {
            try {
                DocumentState saved = TextFiles.save(resolver, document);
                MAIN.post(() -> {
                    pending.state = saved;
                    pending.busy = false;
                    persist(store, pending);
                    if (pending.screen != null) {
                        pending.screen.bindHeader();
                        pending.screen.toast(document.uri.isEmpty() ? R.string.saved_downloads : R.string.saved);
                        if (after != null) pending.screen.launchPicker();
                    }
                });
            } catch (IOException | RuntimeException error) {
                fail(pending, error, R.string.save_failed);
            }
        });
    }

    private static void persist(RecoveryStore store, Session pending) {
        try { store.saveNow(pending.state.copy()); }
        catch (IOException error) {
            if (pending.screen != null) pending.screen.toast(R.string.recovery_failed);
        }
    }

    private static void fail(Session pending, Exception error, int fallback) {
        MAIN.post(() -> {
            pending.busy = false;
            if (pending.screen != null) {
                pending.screen.bindHeader();
                pending.screen.toast(error instanceof TextFiles.FileProblem problem ? problem.message : fallback);
            }
        });
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.isCtrlPressed() && !event.isAltPressed()) {
            int action = switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_A -> android.R.id.selectAll;
                case KeyEvent.KEYCODE_C -> android.R.id.copy;
                case KeyEvent.KEYCODE_V -> android.R.id.paste;
                case KeyEvent.KEYCODE_X -> android.R.id.cut;
                case KeyEvent.KEYCODE_Z -> android.R.id.undo;
                default -> 0;
            };
            if (event.getKeyCode() == KeyEvent.KEYCODE_S || action != 0) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 && !session.busy) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_S) save(null);
                    else editor.onTextContextMenuItem(action);
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public Object onRetainNonConfigurationInstance() {
        capture();
        return session;
    }

    @Override protected void onPause() {
        super.onPause();
        MAIN.removeCallbacks(recover);
        capture();
        persist(recovery, session);
    }

    @Override protected void onDestroy() {
        MAIN.removeCallbacks(recover);
        if (session.screen == this) session.screen = null;
        super.onDestroy();
    }

    private void toast(int message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
}
