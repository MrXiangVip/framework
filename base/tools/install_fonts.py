#!/usr/bin/env python3
"""Install one shared copy of font assets beside compiled framework classes."""
import argparse
from pathlib import Path
import shutil

FONT_FILES = ("NotoSansCJKsc-Regular.otf", "NotoEmoji-Regular.ttf", "OFL-CJK.txt", "OFL-Emoji.txt")

def install(classes):
    source = Path(__file__).resolve().parents[1] / "res/fonts"
    for name in FONT_FILES:
        if not (source / name).is_file():
            raise FileNotFoundError("Missing framework font asset: " + str(source / name))
    target = Path(classes) / "framework/fonts"
    target.mkdir(parents=True, exist_ok=True)
    for name in (*FONT_FILES, "README.md"):
        original = source / name
        destination = target / name
        if not destination.exists() or original.read_bytes() != destination.read_bytes():
            shutil.copyfile(original, destination)
    return target

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classes", type=Path, required=True)
    print("Framework fonts:", install(parser.parse_args().classes))
