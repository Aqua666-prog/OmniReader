# OmniReader 0.1.0 — source release notes

This archive is the first complete **source release** of OmniReader's offline-first reader foundation.
It is intended to be built with the included Gradle project and build instructions.

## Format coverage

### Books / text
- EPUB / EPUB3
- FB2 / FB2.ZIP
- TXT
- HTML / HTM / XHTML
- Markdown / MD
- RTF
- DOCX
- ODT
- MOBI / AZW3 (DRM-free PalmDOC/uncompressed text path)

### Documents / scans
- PDF
- DJVU / DJV

### Manga / comics / image archives
- CBZ / ZIP
- CBR / RAR
- CB7 / 7Z
- CBT / TAR
- image folders exposed through SAF

### Images
- JPG / JPEG
- PNG
- WEBP
- AVIF
- GIF
- BMP
- TIFF / TIF, including multi-page TIFF

## Core behaviour

- multiple persistent SAF roots;
- recursive asynchronous scanning;
- extension + MIME + magic bytes + container inspection;
- Room as source of truth;
- scan-generation reconciliation and move/rename fingerprints;
- cover/thumbnail cache;
- independent ReaderProvider architecture;
- text, comic, PDF, DJVU, TIFF and image reader paths;
- true RTL comic paging;
- zoom and page resume;
- reading progress preserved across rescans/restarts;
- GitHub Actions build/test/lint/APK workflow.

## Explicit limits

- DRM-protected MOBI/AZW3 is not supported.
- HUFF/CDIC-compressed MOBI/KF8 is detected and rejected with a readable error rather than decoded incorrectly.
- DOCX/ODT/RTF are semantic reading views, not pixel-perfect Office renderers.
- EPUB fixed-layout/CSS fidelity is intentionally secondary to deterministic offline text extraction in 0.1.0.
- Advanced notes/quotes UI, statistics, Webtoon/two-page mode, rich series editing and PDF full-text search remain later UX work.

## Validation status

The generation container has no Android SDK/AAPT2 environment, so no APK is claimed from this archive.
Pure Kotlin smoke checks, shell/XML/YAML/static checks are recorded in `BUILD_STATUS.md`.
Run the release gate in `BUILD.md` on a machine with Android SDK 37 before treating an APK as validated.
