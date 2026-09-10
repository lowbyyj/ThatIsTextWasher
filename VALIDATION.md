# Validation

## 1.0.3 Editor typography

- Replaced the editor's monospace typeface with the system sans-serif typeface so ordinary Korean and English word spaces use proportional font metrics. Text content and intentional repeated spaces are preserved.
- Increased editor text from 17 to 19 sp.
- Visually checked Korean, English, and single/double spaces on API 35, including the optimized, development-signed 1.0.3 APK after an update installation.
- All eight existing Korean IME regression tests passed. Lint: zero errors, three dependency-version advisories.

## 1.0.2 Korean composition correction

- Reproduced the original bug through the native IME protocol: `setComposingText("ㅋ")` followed by `setComposingText("키")` produced `ㅋ키` instead of `키`.
- Removed the editor-wide span-stripping filter. It removed the composing span needed to replace the previous syllable, contrary to Android's [InputFilter span contract](https://developer.android.com/reference/android/text/InputFilter).
- Removed clipboard matching from `commitText`. Normal keyboard commits now preserve Android's composing range and cursor behavior, including when the text happens to match the clipboard. Explicit paste still goes through the washer.
- 20 Android instrumentation tests and 16 JVM tests passed. Eight new IME regressions cover Hangul updates, a complete Korean sentence, composing-region replacement, deletion, composing spans, clipboard-match commits, paste between composed words, and recovery debounce during composition.
- The same eight IME tests also passed against the optimized, development-signed **1.0.2 APK** delivered here. Lint: zero errors, three dependency-version advisories.
- A passive input method exists only in the test APK so an unrelated keyboard cannot cancel synthetic composition during tests. It is absent from the application APK.
- These tests exercise Android's real editor and input connection on API 35. A physical Bluetooth keyboard and the user's manufacturer IME were not available for hardware validation.

## 1.0.1 UI correction

- Open and Save use content-sized, single-line buttons, with a 64 dp minimum width.
- Header text increased from 14 to 15 sp; editor text increased from 16 to 17 sp.
- Visually verified on API 35 at font scales 1.0 and 1.3, and at 2.0 with a narrow 320 dp screen. Both button labels remain on one line without clipping.
- Updated APK installed successfully. Lint: zero errors, three dependency-version advisories.

## Initial 1.0 verification

2026-09-10. Android 15 / API 35, x86_64 emulator. No physical device was connected.

- 16 JVM tests passed.
- 12 Android instrumentation tests passed.
- Lint: no errors. Advisory warnings concern pinned dependency versions and a removed resource directory.
- Optimized APK: 103,322 bytes, installed and launched successfully with a development signing key.

| Check | Result |
| --- | --- |
| Cold start | Native editor immediately follows the OS launch window. Observed process cold launches: approximately 0.3–0.6 s on this emulator. |
| Typing | Physical key events, selection replacement, Unicode, focus after Open and Save verified. |
| Plain / Markdown paste | Context menu, Paste as plain text, Ctrl+V, receive-content path and current-clipboard IME commit verified. |
| HTML / styled paste | Visible text, paragraphs, entities and line breaks retained; style spans dropped. |
| Markdown structure | Lists, quotes, arrows, blank lines, nesting and literal code covered by tests. |
| Ctrl+A/C/V/X/Z/S | Selection, copy, wash, cut, one-step paste undo and overwrite save verified. |
| Auto-copy ON | Clipboard contains only the cleaned inserted chunk. |
| Auto-copy OFF | Paste remains washed; original clipboard remains unchanged. Menu preference survives restart. |
| New save | Save creates a timestamped .txt in Downloads/Text Washer without a name or location prompt. |
| Existing file | Opened through the real Android document picker; a shorter Save truncates and overwrites the same file. |
| Recovery | Debounced write and background write verified. Home → force-stop → cold launch restores text. |
| Recovery vs. original | Unsaved changes survive restart while original bytes remain unchanged; Ctrl+S then updates the original, including after a persisted document grant is restored. |
| Rejection / failure | Non-.txt names rejected even from a text/plain provider. Read-only save failure retains working text. |
| Theme / rotation | Light and dark screens inspected; rotation preserves text. |
| Privacy | Packaged manifest requests zero permissions. No networking code or components. No app UID traffic entries observed in Android network accounting during the release smoke test. |

## Boundaries

- Android 10 or newer. Only API 35 was exercised on a running device; physical Bluetooth keyboards and manufacturer keyboards still need hardware validation.
- Files: UTF-8, UTF-8 with BOM, or UTF-16 with BOM. Encoding and existing line endings are retained. Opening is capped at 4 MiB. Legacy encodings and binary content are rejected.
- Android's file picker filters by MIME type rather than extension. The app independently checks the actual display name and rejects anything other than `.txt`.
- CommonMark plus strikethrough is supported. Extended dialects such as tables or custom Markdown directives may retain syntax. HTML conversion uses Android's native parser; images and complex document layouts are not retained.
- Android does not label every keyboard insertion as a paste. Standard paste paths are washed. Keyboard clipboard-history chips that send ordinary `commitText` are treated as typing; use Ctrl+V or the editor's Paste action for guaranteed washing. Clipboard contents are never used to guess whether normal typing is a paste.
- Android 12+ supplies its own system launch window. The app has no additional splash activity or startup screen.
- Recovery is one internal state, written after 700 ms idle and immediately on pause. A process killed before either write can lose the last in-flight edit. External files are written only on explicit Save.
- The supplied `artifacts/Text-Washer.apk` is optimized and signed with the local Android development key for installation/testing. The normal release Gradle output remains unsigned; no publishing key is included.

CommonMark's license is bundled in the APK at `assets/THIRD_PARTY_NOTICES.txt`.
