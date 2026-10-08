#!/usr/bin/env python3
"""Inspect APK/AAB artifacts for native .so libraries and verify 16 KB ELF LOAD alignment.

Usage: check_native_alignment.py <artifact> [<artifact> ...]
Prints a report; exits 1 if any .so has a LOAD segment aligned below 16 KB.
"""
import struct
import sys
import zipfile

PAGE = 16 * 1024


def load_alignments(data: bytes):
    if data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    is64 = data[4] == 2
    endian = "<" if data[5] == 1 else ">"
    if is64:
        phoff = struct.unpack_from(endian + "Q", data, 0x20)[0]
        phentsize, phnum = struct.unpack_from(endian + "HH", data, 0x36)
    else:
        phoff = struct.unpack_from(endian + "I", data, 0x1C)[0]
        phentsize, phnum = struct.unpack_from(endian + "HH", data, 0x2A)
    aligns = []
    for i in range(phnum):
        off = phoff + i * phentsize
        p_type = struct.unpack_from(endian + "I", data, off)[0]
        if p_type != 1:  # PT_LOAD
            continue
        p_align = struct.unpack_from(endian + ("Q" if is64 else "I"), data, off + (0x30 if is64 else 0x1C))[0]
        aligns.append(p_align)
    return aligns


failed = False
for artifact in sys.argv[1:]:
    with zipfile.ZipFile(artifact) as z:
        libs = [n for n in z.namelist() if n.endswith(".so")]
        if not libs:
            print(f"{artifact}: no native .so libraries present (16 KB ELF alignment not applicable)")
            continue
        for name in libs:
            aligns = load_alignments(z.read(name))
            ok = all(a >= PAGE for a in aligns)
            failed |= not ok
            print(f"{artifact}: {name}: LOAD p_align={[hex(a) for a in aligns]} {'OK' if ok else 'NOT 16KB-ALIGNED'}")
sys.exit(1 if failed else 0)
