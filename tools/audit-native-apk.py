"""Fail CI if shipped 64-bit native libraries cannot run on 16 KB pages."""
import struct
import sys
import zipfile
from pathlib import Path


def load_alignments(data):
    if data[:4] != b'\x7fELF' or data[4] != 2:
        raise ValueError('Expected ELF64')
    endian = '<' if data[5] == 1 else '>'
    phoff = struct.unpack_from(endian + 'Q', data, 32)[0]
    entsize, count = struct.unpack_from(endian + 'HH', data, 54)
    result = []
    for i in range(count):
        fields = struct.unpack_from(endian + 'IIQQQQQQ', data, phoff + i * entsize)
        if fields[0] == 1:
            result.append((fields[2], fields[3], fields[7]))
    if not result:
        raise ValueError('No PT_LOAD segments')
    return result


def audit(path):
    errors = []
    with zipfile.ZipFile(path) as apk, open(path, 'rb') as raw:
        libs = [i for i in apk.infolist() if i.filename.endswith('.so') and
                i.filename.startswith(('lib/arm64-v8a/', 'lib/x86_64/'))]
        arm = [i.filename for i in libs if '/arm64-v8a/' in i.filename]
        if not any('pdfium' in n for n in arm) or not any('djvu' in n for n in arm):
            errors.append('Both PDFium and DjVu arm64-v8a backends must be packaged')
        for info in libs:
            segments = load_alignments(apk.read(info))
            for offset, address, alignment in segments:
                if alignment < 16384 or offset % 16384 != address % 16384:
                    errors.append(f'{info.filename}: incompatible PT_LOAD alignment {alignment}')
            if info.compress_type == zipfile.ZIP_STORED:
                raw.seek(info.header_offset)
                header = raw.read(30)
                name, extra = struct.unpack_from('<HH', header, 26)
                if (info.header_offset + 30 + name + extra) % 16384:
                    errors.append(f'{info.filename}: uncompressed ZIP entry is not 16 KB aligned')
            print(info.filename, 'bytes=', info.file_size, 'PT_LOAD=', [x[2] for x in segments])
    if errors:
        raise SystemExit('\n'.join(errors))
    print('PASS: packaged 64-bit libraries support 16 KB alignment; APK bytes=', Path(path).stat().st_size)


if __name__ == '__main__':
    audit(sys.argv[1])
