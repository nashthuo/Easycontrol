"""Reject CJK characters in source and resources outside res/values-zh."""

import codecs
from pathlib import Path
import re
import sys
import unicodedata


SOURCE = Path(__file__).resolve().parents[1] / "easycontrol"
CJK_NAME_PREFIXES = ("CJK", "KANGXI", "HIRAGANA", "KATAKANA", "HANGUL", "BOPOMOFO", "IDEOGRAPHIC")
XML_CHAR_REFERENCE = re.compile(r"&#(?:x0*([0-9A-Fa-f]{1,6})|0*([0-9]{1,7}));")


def read_text(path):
    data = path.read_bytes()
    if data.startswith((codecs.BOM_UTF32_LE, codecs.BOM_UTF32_BE)):
        return data.decode("utf-32")
    if data.startswith((codecs.BOM_UTF16_LE, codecs.BOM_UTF16_BE)):
        return data.decode("utf-16")
    try:
        return data.decode("utf-8-sig")
    except UnicodeDecodeError:
        if b"\x00" in data:
            return ""  # Binary data such as a JAR or a compiled resource.
        raise


def is_cjk(char):
    return (
        "\u3000" <= char <= "\u303f"
        or "\uff01" <= char <= "\uffdc"
        or unicodedata.name(char, "").startswith(CJK_NAME_PREFIXES)
    )


def first_cjk(line, is_xml):
    for column, char in enumerate(line, 1):
        if not char.isascii() and is_cjk(char):
            return column, ord(char)
    if is_xml:
        for match in XML_CHAR_REFERENCE.finditer(line):
            code_point = int(match[1], 16) if match[1] else int(match[2])
            if code_point <= 0x10ffff and is_cjk(chr(code_point)):
                return match.start() + 1, code_point
    return None


def main():
    if not SOURCE.is_dir():
        print(f"CJK check failed: missing {SOURCE}", file=sys.stderr)
        return 1

    failed = False
    for path in sorted(SOURCE.rglob("*")):
        relative = path.relative_to(SOURCE)
        inside_chinese_resources = any(
            parent.name == "values-zh" and parent.parent.name == "res"
            for parent in relative.parents
        )
        if not path.is_file() or inside_chinese_resources:
            continue
        try:
            text = read_text(path)
        except (OSError, UnicodeError) as error:
            print(f"{relative.as_posix()}: {error}", file=sys.stderr)
            failed = True
            continue
        for line_number, line in enumerate(text.splitlines(), 1):
            issue = first_cjk(line, path.suffix.lower() == ".xml")
            if issue:
                column, code_point = issue
                print(f"{relative.as_posix()}:{line_number}:{column}: U+{code_point:04X}", file=sys.stderr)
                failed = True
    if failed:
        return 1
    print("CJK check passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
