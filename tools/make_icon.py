#!/usr/bin/env python3
"""Generate the launcher icon (pixel-art printing press) as a PNG without PIL."""
import struct, sys, zlib

# 16x16 pixel-art press on paper-white; scaled x8 -> 128x128
P = {
    '.': (58, 66, 74, 255),      # background dark slate
    'g': (106, 138, 106, 255),   # machine green
    'G': (74, 104, 74, 255),     # machine green dark
    'l': (140, 168, 140, 255),   # machine green light
    'k': (36, 40, 44, 255),      # near-black detail
    'w': (238, 234, 222, 255),   # paper
    'r': (178, 82, 74, 255),     # red roller/button
    's': (150, 156, 162, 255),   # steel
}
ART = [
    "................",
    "................",
    "..llllllllllll..",
    "..lggggggggggG..",
    "..lgkkkkkkkkgG..",
    "..lgkwwwwwwkgG..",
    "..lggggggggggG..",
    "..lgrsgggggsgG..",
    "..lggggggggggG..",
    "..GGGGGGGGGGGG..",
    "...ss..ww..ss...",
    "...ss.wwww.ss...",
    "...ss..ww..ss...",
    "..ssss....ssss..",
    "................",
    "................",
]
SCALE = 8
size = 16 * SCALE

rows = []
for y in range(size):
    row = bytearray([0])  # filter type 0
    src = ART[y // SCALE]
    for x in range(size):
        r, g, b, a = P[src[x // SCALE]]
        row += bytes((r, g, b, a))
    rows.append(bytes(row))

def chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)

png = b"\x89PNG\r\n\x1a\n"
png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
png += chunk(b"IDAT", zlib.compress(b"".join(rows), 9))
png += chunk(b"IEND", b"")

with open(sys.argv[1], "wb") as f:
    f.write(png)
print("wrote", sys.argv[1], len(png), "bytes")
