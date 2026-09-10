# Release signing

Public releases use a dedicated Text Washer signing key, beginning with 1.0.4.
`release-certificate.pem` is the public certificate. `release-lineage.bin` is the
public proof that the previous development certificate authorizes this key.
Neither file contains a private key or a password.

The APK uses signature scheme v3 with rotation enabled from API 28. Text Washer
supports API 29 and newer, so all supported Android versions use the release key.
The lineage preserves installed app data during updates from 1.0.1–1.0.3 signed
with the original local development key. Rollback to that key is not authorized.
Apps compiled and signed independently use a different identity.

The Windows packaging entry point is `scripts/package-release.ps1`. It builds,
signs, verifies the certificate against the pinned public certificate, and writes
the versioned APK plus SHA-256 checksum into `artifacts`. It fails when signing
material is absent; it never creates another key or falls back to debug signing.
The ordinary Gradle release output remains unsigned.

Private signing material is stored outside this repository in
`%LOCALAPPDATA%\TextWasher\signing`, protected by Windows file permissions.
The keystore password is protected with Windows DPAPI for the current account.
The maintainer's separate private backup contains the keystore and its recovery
password; keep that backup private and preserve it before replacing the computer.
On a replacement Windows account, restore the keystore and protect the recovered
password with `ConvertFrom-SecureString` into `password.dpapi` in the signing
directory. Keep the public certificate and lineage from this repository.

Signing establishes update identity. It does not certify the app with Google or
exempt GitHub downloads from Play Protect scans.
