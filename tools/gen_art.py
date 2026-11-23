#!/usr/bin/env python3
"""Generates FloatPic launcher art + sample photo. Run: python3 tools/gen_art.py"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
ASSETS = ROOT / "app" / "src" / "main" / "assets"
DOCS = ROOT / "docs" / "mockups"

CREAM = (246, 231, 228, 255)
SUGAR = (255, 249, 247, 255)
MAROON = (122, 12, 46, 255)
MAROON_HI = (152, 26, 58, 255)
MAROON_DEEP = (74, 8, 29, 255)
PLUM = (42, 11, 18, 255)
GOLD = (206, 158, 106, 255)
GOLD_HI = (232, 192, 148, 255)
TEAL = (26, 82, 82, 255)
PINK = (196, 78, 104, 255)
PINK_HI = (226, 128, 148, 255)

SZ = 1024


def cloud_mask(size=SZ):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    u = size / 512.0
    cx = cy = size / 2

    def circ(x, y, r):
        d.ellipse([cx + (x - r) * u, cy + (y - r) * u,
                   cx + (x + r) * u, cy + (y + r) * u], fill=255)

    for x, y, r in [(-112, -4, 56), (-66, -48, 62), (-2, -64, 62), (66, -50, 58),
                    (112, -8, 58), (96, 46, 56), (34, 60, 62), (-40, 58, 56),
                    (-100, 34, 50)]:
        circ(x, y, r)
    p0, p1, p2 = (-96, 38), (-190, 86), (-266, 6)
    for i in range(71):
        t = i / 70
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t ** 2 * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t ** 2 * p2[1]
        circ(x, y, 46 * (1 - t) ** 1.35 + 2)

    solid = m.copy()
    ImageDraw.floodfill(solid, (0, 0), 128)
    holes = solid.point(lambda v: 255 if v == 0 else 0)
    return Image.composite(Image.new("L", (size, size), 255), m, holes)


def curl_mask(size, cx, cy, r, a0, a1, w0, w1, steps=120):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    for i in range(steps + 1):
        t = i / steps
        ang = math.radians(a0 + (a1 - a0) * t)
        rad = r + r * 0.18 * t
        w = w0 + (w1 - w0) * t
        x, y = cx + rad * math.cos(ang), cy + rad * math.sin(ang)
        d.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=255)
    return m


def cloud_layer(size):
    m = cloud_mask(size)
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    layer.paste(SUGAR, (0, 0), m.filter(ImageFilter.MaxFilter(17)))
    layer.paste(MAROON, (0, 0), m)
    hi = Image.composite(m, Image.new("L", (size, size), 0),
                         m.filter(ImageFilter.MinFilter(23)))
    layer.paste(MAROON_HI, (0, 0), hi)
    curls = [(590, 405, 74, -170, 105, 30, 9),
             (760, 585, 64, -215, 60, 26, 8),
             (455, 610, 58, -155, 30, 24, 8)]
    cm = Image.new("L", (size, size), 0)
    for c in curls:
        cm = Image.composite(Image.new("L", (size, size), 255), cm, curl_mask(size, *c))
    cm = Image.composite(cm, Image.new("L", (size, size), 0), m.filter(ImageFilter.MinFilter(17)))
    layer.paste(CREAM, (0, 0), cm)
    return layer


def draw_icon(px=SZ):
    S = px / 1024.0

    def s(v):
        return v * S

    img = Image.new("RGBA", (px, px), (0, 0, 0, 0))

    # ---------- cloud (upper right) ----------
    cs = int(px * 0.78)
    cloud = cloud_layer(SZ).resize((cs, cs), Image.LANCZOS)
    cloud = cloud.rotate(-8, resample=Image.BICUBIC, expand=False)
    img.alpha_composite(cloud, (int(s(300)), int(s(74))))

    d = ImageDraw.Draw(img)

    # ---------- bowl (paint pot, lower left) ----------
    bx, by = s(392), s(806)
    bw, bh = s(286), s(186)
    d.chord([bx - bw, by - bh, bx + bw, by + bh], 0, 180, fill=PLUM)
    d.ellipse([bx - bw, by - s(52), bx + bw, by + s(30)], fill=MAROON)
    d.ellipse([bx - bw * 0.9, by - s(38), bx + bw * 0.9, by + s(14)], fill=MAROON_DEEP)
    d.ellipse([bx - bw * 0.74, by - s(26), bx + bw * 0.74, by + s(4)], fill=PINK)
    d.ellipse([bx - bw * 0.44, by - s(20), bx + s(8), by - s(2)], fill=PINK_HI)
    d.arc([bx - bw, by - s(52), bx + bw, by + s(30)], 196, 344,
          fill=(168, 46, 80, 255), width=int(s(10)))

    # ---------- brush ----------
    ang = math.radians(-36)
    hx, hy = s(486), s(672)

    def rot(x, y):
        x, y = s(x), s(y)
        return (hx + x * math.cos(ang) - y * math.sin(ang),
                hy + x * math.sin(ang) + y * math.cos(ang))

    d.polygon([rot(-250, -38), rot(66, -38), rot(66, 38), rot(-250, 38)], fill=GOLD)
    d.polygon([rot(-240, -30), rot(52, -30), rot(52, -17), rot(-240, -17)], fill=GOLD_HI)
    d.polygon([rot(-40, -38), rot(80, -38), rot(80, 38), rot(-40, 38)], fill=MAROON)
    d.polygon([rot(80, -42), rot(114, -42), rot(114, 42), rot(80, 42)], fill=TEAL)
    d.polygon([rot(114, -50), rot(184, -30), rot(222, 0), rot(184, 30), rot(114, 50)],
              fill=MAROON_DEEP)
    d.polygon([rot(152, -18), rot(212, -2), rot(212, 2), rot(152, 18)], fill=MAROON_HI)

    # ---------- sparkle ----------
    sx, sy = s(214), s(268)
    r1, r2 = s(44), s(12)
    d.polygon([(sx, sy - r1), (sx + r2, sy - r2), (sx + r1, sy), (sx + r2, sy + r2),
               (sx, sy + r1), (sx - r2, sy + r2), (sx - r1, sy), (sx - r2, sy - r2)],
              fill=MAROON)
    return img


def save_densities(img, name, sizes):
    for dpi, size in sizes.items():
        folder = RES / f"mipmap-{dpi}"
        folder.mkdir(parents=True, exist_ok=True)
        img.resize((size, size), Image.LANCZOS).save(folder / f"{name}.png")


def main():
    print("generating icon art...")
    fg = draw_icon(SZ)

    canvas = Image.new("RGBA", (1080, 1080), (0, 0, 0, 0))
    art = fg.resize((int(1080 * 0.78), int(1080 * 0.78)), Image.LANCZOS)
    canvas.paste(art, ((1080 - art.width) // 2, (1080 - art.height) // 2), art)
    save_densities(canvas, "ic_launcher_foreground",
                   {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432})

    legacy = Image.new("RGBA", (1080, 1080), (0, 0, 0, 0))
    ImageDraw.Draw(legacy).rounded_rectangle([0, 0, 1080, 1080], radius=240, fill=CREAM)
    art2 = fg.resize((int(1080 * 0.95), int(1080 * 0.95)), Image.LANCZOS)
    legacy.paste(art2, ((1080 - art2.width) // 2, (1080 - art2.height) // 2), art2)
    legacy_sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    save_densities(legacy, "ic_launcher", legacy_sizes)

    rnd = Image.new("RGBA", (1080, 1080), (0, 0, 0, 0))
    mask = Image.new("L", (1080, 1080), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, 1080, 1080], fill=255)
    rnd.paste(legacy, (0, 0), mask)
    save_densities(rnd, "ic_launcher_round", legacy_sizes)

    DOCS.mkdir(parents=True, exist_ok=True)
    legacy.resize((512, 512), Image.LANCZOS).save(DOCS / "app-icon-512.png")

    ASSETS.mkdir(parents=True, exist_ok=True)
    sample = Image.new("RGB", (1080, 1440))
    sd = ImageDraw.Draw(sample)
    for y in range(1440):
        t = y / 1440
        sd.line([(0, y), (1080, y)],
                fill=(int(24 + 40 * t), int(18 + 26 * t), int(30 + 44 * t)))
    sd.ellipse([120, 360, 960, 1220], fill=(96, 26, 48))
    sd.ellipse([210, 450, 870, 1130], fill=(146, 52, 78))
    sd.ellipse([330, 560, 750, 1000], fill=(232, 190, 168))
    sd.ellipse([400, 640, 700, 930], fill=(74, 14, 32))
    sample.save(ASSETS / "sample_photo.jpg", quality=92)
    print("done ->", RES)


if __name__ == "__main__":
    main()
