package com.lowbyyj.textwasher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Test APK only: a read-only provider that labels every extension text/plain. */
public class FixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "text/plain"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[]{uri.getLastPathSegment(), 12});
        return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!mode.equals("r")) throw new FileNotFoundException("Read-only test file");
        File file = new File(getContext().getCacheDir(), "fixture.txt");
        try (FileOutputStream output = new FileOutputStream(file)) { output.write("**original**".getBytes()); }
        catch (IOException error) { throw new FileNotFoundException(error.getMessage()); }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
