# OmniReader 0.1.0 — implementation status

This tree is the **OmniReader 0.1.0 source release**. All file families requested in the original specification now have a scanner/detector path and ReaderProvider or equivalent opening path. Android Gradle compilation still must be run by the user; see `BUILD_STATUS.md`.

## Implemented reader paths

- EPUB / EPUB3.
- FB2 / FB2.ZIP.
- TXT with encoding detection.
- HTML / HTM / XHTML / Markdown.
- RTF.
- DOCX.
- ODT.
- DRM-free MOBI / AZW3 for uncompressed and PalmDOC-compressed text records.
- PDF via Android PdfRenderer.
- DJVU / DJV through an isolated DjVuLibre-based decoder.
- CBZ / ZIP image archives.
- CBR / RAR image archives.
- CB7 / 7Z image archives.
- CBT / TAR image archives.
- JPG / JPEG / PNG / WEBP / AVIF / GIF / BMP.
- TIFF / TIF including multi-page TIFF.
- Folders containing image sequences as virtual manga/comic items.

## Important explicit format boundaries

- MOBI/AZW3 DRM: unsupported by design.
- MOBI/AZW3 HUFF/CDIC/KF8 reconstruction: parser detects and reports this variant but does not decode it yet.
- AVIF: uses Android platform ImageDecoder; older/unsupported devices return a readable error.
- DOCX/ODT/RTF: semantic reading view rather than pixel-perfect office rendering.
- GIF is used as a page image; comic reading does not implement animation playback as a reading feature.

## Core infrastructure retained

- Room source of truth.
- Multiple persistable SAF roots.
- Recursive cancellable generation-token scanner.
- Move/rename reconciliation that preserves progress.
- Cover/thumbnail cache.
- Versioned bounded staging cache.
- ReaderProvider/ReaderSession abstraction.
- Paged bitmap abstraction shared by PDF/DJVU/TIFF/images.
- Natural sorting and real RTL comic direction.
- Progress persistence and restoration.
- GitHub Actions build gate.
- Unit/Robolectric tests for core logic plus new format/parser coverage.

## UX still outside this pass

- advanced typography and text page-turn pagination;
- Webtoon and two-page comic modes;
- full bookmarks/notes/quotes UI;
- rich series/collections editing UI;
- statistics UI;
- PDF full-text search/outline UI;
- manual text-encoding chooser UI (schema field already exists).
