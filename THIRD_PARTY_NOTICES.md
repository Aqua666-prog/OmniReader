# Third-party notices

OmniReader 0.8.0 uses third-party libraries. Review their licenses before redistributing binaries.

- Android DjVuLibre / DjVuLibre backend — fork `Kazzenkatt/android-djvulibre`, pinned at commit `5b9bd591befc528268cf00c28e8fb81bc75d664b`, based on Alexey Kuznetsov's Android DjVuLibre wrapper; GNU GPL 2.0 or later. The selected fork adds Android 16 / 16 KB page-size support. The build downloads the prebuilt AAR from that exact commit and verifies Git blob SHA-1 `bde3f2e2cbe693343e5180b69e80b5580b40ecd4`.
- `io.legere:pdfiumandroid:2.0.3` — PdfiumAndroidKt / PDFium; Apache License 2.0 plus upstream PDFium BSD-style notices. A notice copy is retained in `licenses/PdfiumAndroidKt-2.0.3.txt`.
- `cz.adaptech.tesseract4android:tesseract4android:4.9.0` — Tesseract4Android; Apache License 2.0. Version 4.9.0 includes Tesseract 5.5.1. The 4.8.0 line introduced Android 15 / 16 KB page-size support; the packaged binary is still audited by this project's CI.
- Tesseract `tessdata_fast` language models (`eng`, `rus`, `heb`, `yid`, `ara`) — Apache License 2.0. Models are pinned to commit `87416418657359cb625c412a48b6e1d6d41c29bd`, downloaded only at build time and verified against pinned Git blob SHA-1 values. OCR is offline at runtime.
- jchmlib — Apache License 2.0.
- Junrar 8.1.1 — UnRAR License (see upstream package).
- Apache Commons Compress — Apache License 2.0.
- XZ for Java (`org.tukaani:xz`) — 0BSD.
- AndroidX, Jetpack Compose, Room, DataStore and Kotlin/coroutines — their respective upstream licenses.

The application does not bypass DRM. MOBI/AZW/AZW3 encrypted content is rejected. Compatibility/size review and remaining binary verification are documented in `docs/DOCUMENT_VIEWER.md`.
