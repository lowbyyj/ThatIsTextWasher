package com.lowbyyj.textwasher;

import android.content.Context;
import android.util.AtomicFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** One atomically replaced state, never a history and never an external document write. */
final class RecoveryStore {
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor();
    private static final AtomicLong ISSUED = new AtomicLong();
    private static final Object LOCK = new Object();
    private static long written;
    private final AtomicFile file;

    RecoveryStore(Context context) {
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), "recovery"));
    }

    DocumentState read() throws IOException {
        synchronized (LOCK) {
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(file.openRead()))) {
                if (input.readInt() != 1) throw new IOException("Recovery format");
                DocumentState state = new DocumentState();
                state.text = readString(input);
                state.uri = readString(input);
                state.name = readString(input);
                state.encoding = readString(input);
                state.bom = input.readBoolean();
                state.dirty = input.readBoolean();
                state.selectionStart = input.readInt();
                state.selectionEnd = input.readInt();
                state.scrollY = input.readInt();
                return state;
            } catch (FileNotFoundException absent) {
                return new DocumentState();
            }
        }
    }

    void saveLater(DocumentState state, Runnable onFailure) {
        long revision = ISSUED.incrementAndGet();
        WRITER.execute(() -> {
            try { write(state, revision); }
            catch (IOException error) { onFailure.run(); }
        });
    }

    void saveNow(DocumentState state) throws IOException {
        write(state, ISSUED.incrementAndGet());
    }

    private void write(DocumentState state, long revision) throws IOException {
        synchronized (LOCK) {
            if (revision < written) return;
            FileOutputStream stream = null;
            try {
                stream = file.startWrite();
                DataOutputStream output = new DataOutputStream(new BufferedOutputStream(stream));
                output.writeInt(1);
                writeString(output, state.text);
                writeString(output, state.uri);
                writeString(output, state.name);
                writeString(output, state.encoding);
                output.writeBoolean(state.bom);
                output.writeBoolean(state.dirty);
                output.writeInt(state.selectionStart);
                output.writeInt(state.selectionEnd);
                output.writeInt(state.scrollY);
                output.flush();
                file.finishWrite(stream);
                written = revision;
            } catch (IOException error) {
                file.failWrite(stream);
                throw error;
            }
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int size = input.readInt();
        if (size < 0 || size > 32 * 1024 * 1024) throw new IOException("Recovery length");
        byte[] bytes = new byte[size];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
