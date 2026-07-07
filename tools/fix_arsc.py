#!/usr/bin/env python3
"""Repack an APK so resources.arsc is stored uncompressed (required for targetSdk>=30).

zipalign then handles the 4-byte alignment of stored entries.
"""
import shutil, sys, zipfile

src = sys.argv[1]
tmp = src + ".tmp"

with zipfile.ZipFile(src) as zin:
    needs_fix = any(i.filename == "resources.arsc" and i.compress_type != zipfile.ZIP_STORED
                    for i in zin.infolist())
    if not needs_fix:
        print("resources.arsc already stored uncompressed")
        sys.exit(0)
    with zipfile.ZipFile(tmp, "w") as zout:
        for info in zin.infolist():
            data = zin.read(info.filename)
            method = zipfile.ZIP_STORED if info.filename == "resources.arsc" else info.compress_type
            out = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            out.external_attr = info.external_attr
            zout.writestr(out, data, compress_type=method)

shutil.move(tmp, src)
print("repacked resources.arsc as STORED")
