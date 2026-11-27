#!/usr/bin/env python3
"""Verify that a class definition is present in an APK's DEX files."""

from __future__ import annotations

import re
import struct
import sys
import zipfile
from pathlib import Path

DEX_HEADER_SIZE = 0x70
CLASS_DEF_SIZE = 32
TARGET_CLASS = "Lcom/materialkolor/scheme/DynamicScheme$Platform;"


def read_u32(data: bytes, offset: int) -> int:
    if offset < 0 or offset + 4 > len(data):
        raise ValueError(f"DEX offset out of range: 0x{offset:x}")
    return struct.unpack_from("<I", data, offset)[0]


def read_uleb128(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    shift = 0
    while True:
        if offset >= len(data) or shift > 28:
            raise ValueError("invalid ULEB128 value")
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if byte & 0x80 == 0:
            return value, offset
        shift += 7


def descriptor_at(data: bytes, string_ids_off: int, string_ids_size: int, index: int) -> str:
    if index >= string_ids_size:
        raise ValueError(f"DEX string index out of range: {index}")
    string_data_off = read_u32(data, string_ids_off + index * 4)
    _, cursor = read_uleb128(data, string_data_off)
    end = data.find(b"\0", cursor)
    if end < 0:
        raise ValueError("unterminated DEX string")
    return data[cursor:end].decode("utf-8", errors="replace")


def dex_defines_class(data: bytes, target: str) -> bool:
    if len(data) < DEX_HEADER_SIZE or not data.startswith(b"dex\n"):
        raise ValueError("not a valid DEX file")

    string_ids_size = read_u32(data, 0x38)
    string_ids_off = read_u32(data, 0x3C)
    type_ids_size = read_u32(data, 0x40)
    type_ids_off = read_u32(data, 0x44)
    class_defs_size = read_u32(data, 0x60)
    class_defs_off = read_u32(data, 0x64)

    if string_ids_off + string_ids_size * 4 > len(data):
        raise ValueError("DEX string table is out of range")
    if type_ids_off + type_ids_size * 4 > len(data):
        raise ValueError("DEX type table is out of range")
    if class_defs_off + class_defs_size * CLASS_DEF_SIZE > len(data):
        raise ValueError("DEX class definitions are out of range")

    for item in range(class_defs_size):
        class_idx = read_u32(data, class_defs_off + item * CLASS_DEF_SIZE)
        if class_idx >= type_ids_size:
            raise ValueError(f"DEX class type index out of range: {class_idx}")
        descriptor_idx = read_u32(data, type_ids_off + class_idx * 4)
        if descriptor_at(data, string_ids_off, string_ids_size, descriptor_idx) == target:
            return True
    return False


def apk_defines_class(apk_path: Path, target: str) -> tuple[bool, list[str]]:
    dex_names: list[str] = []
    with zipfile.ZipFile(apk_path) as apk:
        dex_names = sorted(
            name for name in apk.namelist()
            if re.fullmatch(r"classes\d*\.dex", name)
        )
        if not dex_names:
            raise ValueError("APK contains no DEX files")
        for dex_name in dex_names:
            if dex_defines_class(apk.read(dex_name), target):
                return True, dex_names
    return False, dex_names


def main() -> int:
    if len(sys.argv) not in (2, 3):
        print(f"usage: {Path(sys.argv[0]).name} APK [DEX_CLASS]", file=sys.stderr)
        return 2

    apk_path = Path(sys.argv[1])
    target = sys.argv[2] if len(sys.argv) == 3 else TARGET_CLASS
    try:
        present, dex_names = apk_defines_class(apk_path, target)
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"::error title=APK DEX verification::{error}", file=sys.stderr)
        return 1

    if present:
        print(f"::notice title=APK DEX verification::{target} is defined in {apk_path}")
        return 0

    print(
        f"::error title=APK DEX verification::{target} is missing from {apk_path}; "
        f"searched {', '.join(dex_names)}",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
