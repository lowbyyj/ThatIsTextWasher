package com.lowbyyj.textwasher;

import android.app.Instrumentation;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import java.io.FileInputStream;
import java.io.IOException;

final class ImeTestEnvironment {
    static void activate(Instrumentation instrumentation) {
        String id = instrumentation.getContext().getPackageName() + "/" + PassiveTestIme.class.getName();
        String active = Settings.Secure.getString(instrumentation.getTargetContext().getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);
        if (id.equals(active)) return;
        command(instrumentation, "ime enable " + id);
        command(instrumentation, "ime set " + id);
        instrumentation.waitForIdleSync();
    }

    private static void command(Instrumentation instrumentation, String command) {
        try (ParcelFileDescriptor descriptor = instrumentation.getUiAutomation().executeShellCommand(command);
             FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            byte[] buffer = new byte[1024];
            while (input.read(buffer) != -1) { /* Wait for the shell command to finish. */ }
        } catch (IOException error) {
            throw new AssertionError("Could not select the test IME", error);
        }
    }
}
