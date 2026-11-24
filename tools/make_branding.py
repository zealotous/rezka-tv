#!/usr/bin/env python3
"""Generates the Rezka TV TV banner and launcher icons (no external assets)."""
import math
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

ACCENT = (139, 92, 246)
ACCENT_DARK = (74, 45, 160)
BG = (9, 9, 13)
WHITE = (255, 255, 255)


def font(size, bold=True):
    candidates = [
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/System/Library/Fonts/Helvetica.ttc",
        "/Library/Fonts/Arial.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                pass
    return ImageFont.load_default()


def gradient(size, top, bottom):
    width, height = size
    image = Image.new("RGB", size, top)
    draw = ImageDraw.Draw(image)
    for y in range(height):
        t = y / max(1, height - 1)
        color = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        draw.line([(0, y), (width, y)], fill=color)
    return image


def rounded_mask(size, radius):
    mask = Image.new("L", size, 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius=radius, fill=255)
    return mask


def play_triangle(draw, cx, cy, r, color=WHITE):
    points = [
        (cx - r * 0.45, cy - r * 0.62),
        (cx - r * 0.45, cy + r * 0.62),
        (cx + r * 0.68, cy),
    ]
    draw.polygon(points, fill=color)


def make_banner():
    size = (320, 180)
    image = gradient(size, (14, 12, 22), BG)
    draw = ImageDraw.Draw(image)

    # subtle purple glow on the left
    glow = Image.new("RGBA", size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([-120, -60, 180, 240], fill=(*ACCENT, 52))
    image = Image.alpha_composite(image.convert("RGBA"), glow)
    draw = ImageDraw.Draw(image)

    # logo mark
    cx, cy, r = 62, 90, 34
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=ACCENT, width=7)
    play_triangle(draw, cx + 3, cy, r * 0.72)

    # wordmark
    draw.text((112, 48), "REZKA", font=font(46, True), fill=WHITE)
    draw.text((113, 99), "TV  •  КИНО И СЕРИАЛЫ", font=font(15, False), fill=(196, 181, 253))

    # film perforation strip along the bottom
    for x in range(0, 320, 22):
        draw.rounded_rectangle([x + 4, 158, x + 14, 170], radius=2, fill=(40, 33, 60))

    out = os.path.join(RES, "drawable-xhdpi", "banner.png")
    image.convert("RGB").save(out, "PNG")
    print("wrote", out)


def make_icon(size, out):
    scale = 4
    big = size * scale
    image = gradient((big, big), (24, 18, 44), BG)
    mask = rounded_mask((big, big), int(big * 0.22))
    rounded = Image.new("RGB", (big, big), BG)
    rounded.paste(image, (0, 0), mask)
    draw = ImageDraw.Draw(rounded)
    r = int(big * 0.33)
    draw.ellipse([big // 2 - r, big // 2 - r, big // 2 + r, big // 2 + r],
                 outline=ACCENT, width=max(2, int(big * 0.045)))
    play_triangle(draw, big // 2 + int(big * 0.02), big // 2, r * 0.78)
    rounded.resize((size, size), Image.LANCZOS).save(out, "PNG")
    print("wrote", out)


if __name__ == "__main__":
    make_banner()
    for density, px in [("mipmap-xxxhdpi", 192), ("mipmap-xxhdpi", 144),
                        ("mipmap-xhdpi", 96), ("mipmap-hdpi", 72)]:
        make_icon(px, os.path.join(RES, density, "ic_launcher.png"))
