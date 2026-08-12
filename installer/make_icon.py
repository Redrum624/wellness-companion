"""Generate installer/icon/wellness.ico from windows/resources/icon.png.

Inno Setup's SetupIconFile needs a real multi-resolution .ico; the repo only
carries PNG/SVG art (electron-builder accepted the PNG directly). Run from
build_installer.bat, which skips it when the .ico already exists.

Usage:
    python make_icon.py <source.png> <dest.ico>

Requires Pillow.
"""

import sys
from pathlib import Path

# Windows picks the closest size per context: 16/32 for the title bar and
# Explorer list views, 48/64 for medium icons, 128/256 for large + the
# Alt-Tab / installer banner.
SIZES = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]


def main(argv) -> int:
    if len(argv) != 3:
        sys.stderr.write("usage: make_icon.py <source.png> <dest.ico>\n")
        return 2

    src, dest = Path(argv[1]), Path(argv[2])
    if not src.is_file():
        sys.stderr.write(f"[ERROR] Source image not found: {src}\n")
        return 1

    try:
        from PIL import Image
    except ImportError:
        sys.stderr.write("[ERROR] Pillow is required: pip install Pillow\n")
        return 1

    dest.parent.mkdir(parents=True, exist_ok=True)

    # RGBA keeps the transparent corners; without it Pillow writes a black box.
    img = Image.open(src).convert("RGBA")
    img.save(dest, format="ICO", sizes=SIZES)
    print(f"  [OK] Icon written: {dest} ({', '.join(f'{w}x{h}' for w, h in SIZES)})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
