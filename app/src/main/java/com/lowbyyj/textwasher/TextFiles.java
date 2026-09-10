package com.lowbyyj.textwasher;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class TextFiles {
    static final int MAX_FILE_BYTES = 4 * 1024 * 1024;

    static boolean isTxt(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".txt");
    }

    static String displayName(ContentResolver resolver, Uri uri) throws IOException {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        }
        throw new IOException("No filename");
    }

    static DocumentState open(ContentResolver resolver, Uri uri) throws IOException {
        String name = displayName(resolver, uri);
        if (!isTxt(name)) throw new FileProblem(R.string.txt_only);
        byte[] bytes;
        try (InputStream input = resolver.openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("No input stream");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > MAX_FILE_BYTES) throw new FileProblem(R.string.too_large);
                output.write(buffer, 0, count);
            }
            bytes = output.toByteArray();
        }
        DocumentState state = decode(bytes);
        state.uri = uri.toString();
        state.name = name;
        return state;
    }

    static DocumentState decode(byte[] bytes) throws IOException {
        DocumentState state = new DocumentState();
        int offset = 0;
        if (bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf) {
            offset = 3;
        } else if (bytes.length >= 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe) {
            state.encoding = "UTF-16LE";
            offset = 2;
        } else if (bytes.length >= 2 && bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff) {
            state.encoding = "UTF-16BE";
            offset = 2;
        }
        state.bom = offset > 0;
        try {
            state.text = Charset.forName(state.encoding).newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
            if (state.text.indexOf('\0') >= 0) throw new FileProblem(R.string.unsupported_encoding);
        } catch (CharacterCodingException error) {
            throw new FileProblem(R.string.unsupported_encoding);
        }
        return state;
    }

    static String timestampName() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss", Locale.ROOT)) + ".txt";
    }

    static DocumentState save(ContentResolver resolver, DocumentState state) throws IOException {
        DocumentState saved = state.copy();
        boolean creating = state.uri.isEmpty();
        Uri uri;
        if (creating) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, timestampName());
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Text Washer");
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
            if (uri == null) throw new IOException("Could not create file");
        } else {
            uri = Uri.parse(state.uri);
            if (!isTxt(displayName(resolver, uri))) throw new FileProblem(R.string.txt_only);
        }
        try {
            // 'wt' explicitly truncates; 'w' alone is provider-dependent and may leave a tail.
            try (OutputStream output = resolver.openOutputStream(uri, "wt")) {
                if (output == null) throw new IOException("No output stream");
                if (state.bom) {
                    switch (state.encoding) {
                        case "UTF-16LE" -> output.write(new byte[]{(byte) 0xff, (byte) 0xfe});
                        case "UTF-16BE" -> output.write(new byte[]{(byte) 0xfe, (byte) 0xff});
                        default -> output.write(new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
                    }
                }
                output.write(state.text.getBytes(Charset.forName(state.encoding)));
                output.flush();
            }
            if (creating) {
                ContentValues published = new ContentValues();
                published.put(MediaStore.Downloads.IS_PENDING, 0);
                if (resolver.update(uri, published, null, null) != 1) throw new IOException("Could not publish file");
            }
            saved.uri = uri.toString();
            saved.name = displayName(resolver, uri);
            saved.dirty = false;
            return saved;
        } catch (IOException | RuntimeException error) {
            if (creating) {
                try { resolver.delete(uri, null, null); }
                catch (RuntimeException ignored) { /* Original failure is more useful. */ }
            }
            throw error;
        }
    }

    static final class FileProblem extends IOException {
        final int message;
        FileProblem(int message) { this.message = message; }
    }
}
