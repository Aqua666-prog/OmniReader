# Third-party notices

OmniReader 0.5.0 uses third-party libraries. Review their licenses before redistributing binaries.

- Android DjVuLibre / DjVuLibre backend — fork `Kazzenkatt/android-djvulibre`, pinned at commit `5b9bd591befc528268cf00c28e8fb81bc75d664b`, based on Alexey Kuznetsov's Android DjVuLibre wrapper; GNU GPL 2.0 or later. The selected fork adds Android 16 / 16 KB page-size support. The build downloads the prebuilt AAR from that exact commit and verifies Git blob SHA-1 `bde3f2e2cbe693343e5180b69e80b5580b40ecd4`. Source: `https://github.com/Kazzenkatt/android-djvulibre`.
- jchmlib — Chen Zhongguo; Apache License 2.0.
- Junrar 8.1.1 — RAR reading/extraction library; UnRAR License (see the dependency's bundled license).
- Apache Commons Compress — Apache License 2.0.
- XZ for Java (`org.tukaani:xz`) — 0BSD; used for common LZMA/LZMA2-compressed CB7 archives.
- AndroidX, Jetpack Compose, Room, DataStore and Kotlin/coroutines — their respective upstream licenses.

The application does not bypass DRM. MOBI/AZW/AZW3 encrypted content is rejected.
