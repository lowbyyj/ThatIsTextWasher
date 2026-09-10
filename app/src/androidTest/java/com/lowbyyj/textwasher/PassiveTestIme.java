package com.lowbyyj.textwasher;

import android.inputmethodservice.InputMethodService;

/** Test APK only: the test drives composition, so a second IME must not cancel or correct it. */
public final class PassiveTestIme extends InputMethodService {
    @Override public boolean onEvaluateInputViewShown() { return false; }
    @Override public boolean onEvaluateFullscreenMode() { return false; }
}
