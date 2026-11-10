"""Draws the Forgeroom application icon and writes it in the formats the packagers and windows need.

Run from the repository root with Pillow installed:  python tools/make_icons.py

Outputs (all generated; the script is the source):
  editor-desktop/packaging/icons/forgeroom.ico   Windows executables and installer (16 to 256 px)
  editor-desktop/packaging/icons/forgeroom.png   Linux package (512 px)
  editor-desktop/packaging/icons/forgeroom.icns  macOS app bundle
  editor-desktop/src/main/resources/forgeroom-icon-{32,64,128,256}.png   editor window and taskbar icon
  engine-desktop/src/main/resources/forgeroom-icon-{32,64,128,256}.png   default window icon of a game
"""
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SUPERSAMPLE = 1024
CORNER = 0.22
TOP = (30, 42, 68)
BOTTOM = (59, 91, 219)
WHITE = (250, 250, 252)
SPARK = (255, 150, 40)
WINDOW_SIZES = (32, 64, 128, 256)
ICO_SIZES = (16, 24, 32, 48, 64, 128, 256)


def draw(size=SUPERSAMPLE):
    """A rounded square with a vertical gradient, a bold F and an ember."""
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gradient = Image.new("RGBA", (size, size))
    pixels = gradient.load()
    for y in range(size):
        t = y / (size - 1)
        color = tuple(int(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3)) + (255,)
        for x in range(size):
            pixels[x, y] = color
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=int(size * CORNER), fill=255)
    image.paste(gradient, (0, 0), mask)

    d = ImageDraw.Draw(image)
    stem_left, stem_right = size * 0.30, size * 0.43
    top, bottom = size * 0.20, size * 0.80
    d.rectangle((stem_left, top, stem_right, bottom), fill=WHITE)
    d.rectangle((stem_left, top, size * 0.72, top + size * 0.13), fill=WHITE)
    d.rectangle((stem_left, size * 0.44, size * 0.62, size * 0.44 + size * 0.12), fill=WHITE)
    # The ember above the arm of the F
    cx, cy, r = size * 0.74, size * 0.30, size * 0.065
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=SPARK)
    return image


def main():
    master = draw()

    icons = os.path.join(ROOT, "editor-desktop", "packaging", "icons")
    os.makedirs(icons, exist_ok=True)
    master.resize((256, 256), Image.LANCZOS).save(
        os.path.join(icons, "forgeroom.ico"), format="ICO", sizes=[(s, s) for s in ICO_SIZES])
    master.resize((512, 512), Image.LANCZOS).save(os.path.join(icons, "forgeroom.png"))
    master.resize((1024, 1024), Image.LANCZOS).save(os.path.join(icons, "forgeroom.icns"), format="ICNS")

    for module in ("editor-desktop", "engine-desktop"):
        resources = os.path.join(ROOT, module, "src", "main", "resources")
        os.makedirs(resources, exist_ok=True)
        for size in WINDOW_SIZES:
            master.resize((size, size), Image.LANCZOS).save(os.path.join(resources, "forgeroom-icon-%d.png" % size))
    print("icons written")


if __name__ == "__main__":
    sys.exit(main())
