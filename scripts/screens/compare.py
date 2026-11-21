#!/usr/bin/env python3
"""Compares two capture.sh runs pixel by pixel (T13.2, 16-PHASE13-PLAN §7). Standard library only (R2).

    python3 -I scripts/screens/compare.py build/screens/<baseline> build/screens/<result>

Every PNG of the baseline must exist in the result and be identical: one different pixel is a failure.
The status bar rows (status_bar_px in each width's device.txt) are skipped: some ROMs ignore demo mode, so
the clock changes. Prints the number of different pixels and their bounding box; exit code 1 when anything
differs or is missing.
"""
import struct
import sys
import zlib
from pathlib import Path

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
BYTES_PER_PIXEL = {2: 3, 6: 4}  # PNG colour types RGB and RGBA, 8 bits per channel (what screencap writes)


def decode(path):
    """Returns (width, height, bytes per pixel, rows of raw pixel bytes) of an 8-bit RGB/RGBA PNG."""
    data = path.read_bytes()
    if not data.startswith(PNG_SIGNATURE):
        raise ValueError(f"{path}: not a PNG")
    pos, idat, header = len(PNG_SIGNATURE), bytearray(), None
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        kind, body = data[pos + 4:pos + 8], data[pos + 8:pos + 8 + length]
        if kind == b"IHDR":
            header = struct.unpack(">IIBBBBB", body)
        elif kind == b"IDAT":
            idat += body
        pos += length + 12
    width, height, depth, colour, _, _, interlace = header
    if depth != 8 or colour not in BYTES_PER_PIXEL or interlace:
        raise ValueError(f"{path}: unsupported PNG (depth {depth}, colour {colour}, interlace {interlace})")
    bpp = BYTES_PER_PIXEL[colour]
    raw, stride = zlib.decompress(bytes(idat)), width * bpp
    rows, previous = [], bytearray(stride)
    for y in range(height):
        start = y * (stride + 1)
        kind, row = raw[start], bytearray(raw[start + 1:start + 1 + stride])
        unfilter(kind, row, previous, bpp)
        rows.append(row)
        previous = row
    return width, height, bpp, rows


def unfilter(kind, row, previous, bpp):
    if kind == 1:
        for i in range(bpp, len(row)):
            row[i] = (row[i] + row[i - bpp]) & 0xFF
    elif kind == 2:
        for i in range(len(row)):
            row[i] = (row[i] + previous[i]) & 0xFF
    elif kind == 3:
        for i in range(len(row)):
            left = row[i - bpp] if i >= bpp else 0
            row[i] = (row[i] + ((left + previous[i]) >> 1)) & 0xFF
    elif kind == 4:
        for i in range(len(row)):
            a = row[i - bpp] if i >= bpp else 0
            b = previous[i]
            c = previous[i - bpp] if i >= bpp else 0
            p = a + b - c
            pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
            predictor = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
            row[i] = (row[i] + predictor) & 0xFF


def status_bar_rows(folder):
    """Height of the status bar in pixels recorded by capture.sh, 0 when unknown."""
    device = folder / "device.txt"
    if not device.exists():
        return 0
    values = dict(line.split("=", 1) for line in device.read_text().split())
    return int(values.get("status_bar_px") or 0)


def difference(first, second, skip_rows=0):
    """None when identical (below the first skip_rows rows), else a description of the different pixels."""
    if first.read_bytes() == second.read_bytes():
        return None
    w1, h1, bpp1, rows1 = decode(first)
    w2, h2, bpp2, rows2 = decode(second)
    if (w1, h1) != (w2, h2):
        return f"size {w1}x{h1} vs {w2}x{h2}"
    count, box = 0, None
    for y, (a, b) in enumerate(zip(rows1, rows2)):
        if y < skip_rows or a == b:
            continue
        for x in range(w1):
            if a[x * bpp1:x * bpp1 + 3] != b[x * bpp2:x * bpp2 + 3]:
                count += 1
                box = (x, y, x, y) if box is None else (min(box[0], x), min(box[1], y), max(box[2], x), max(box[3], y))
    if count == 0:
        return None  # same pixels, different encoding
    return f"{count} pixels differ in x {box[0]}..{box[2]}, y {box[1]}..{box[3]}"


def main(baseline, result):
    failures = 0
    for expected in sorted(baseline.rglob("*.png")):
        name = expected.relative_to(baseline)
        actual = result / name
        if not actual.exists():
            print(f"MISSING   {name}")
            failures += 1
            continue
        skip = max(status_bar_rows(expected.parent), status_bar_rows(actual.parent))
        diff = difference(expected, actual, skip)
        print(f"SAME      {name}" if diff is None else f"DIFFERENT {name}: {diff}")
        failures += diff is not None
    print(f"{failures} screen(s) differ or are missing" if failures else "All screens identical")
    return 1 if failures else 0


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    sys.exit(main(Path(sys.argv[1]), Path(sys.argv[2])))
