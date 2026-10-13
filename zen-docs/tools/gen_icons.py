#!/usr/bin/env python3
"""Generate ZenPipe launcher/notification icons (enso ring motif). Run: python3 zen-docs/tools/gen_icons.py"""
import math, os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
RES = os.path.join(ROOT, "app/src/main/res")
BG = (0x2E, 0x4A, 0x45, 255)      # calm deep sage/teal
FG = (0xF2, 0xEF, 0xE6, 255)      # warm off-white
SS = 8                            # supersampling


def ring(size, radius, thick, color, dot=False):
    """Open ring (enso) on a transparent square canvas; radius/thick as fraction of size."""
    big = size * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = cy = big / 2
    r = radius * big
    start, end = math.radians(-40), math.radians(262)  # gap upper right
    n = 240
    outer, inner = [], []
    for i in range(n + 1):
        t = i / n
        a = start + (end - start) * t
        w = thick * big * (0.55 + 0.9 * (1 - t) ** 0.8) / 2  # thick start, thinner end
        outer.append((cx + (r + w) * math.cos(a), cy + (r + w) * math.sin(a)))
        inner.append((cx + (r - w) * math.cos(a), cy + (r - w) * math.sin(a)))
    d.polygon(outer + inner[::-1], fill=color)
    for i in (0, n):  # round caps
        a = start + (end - start) * i / n
        t = i / n
        w = thick * big * (0.55 + 0.9 * (1 - t) ** 0.8) / 2
        x, y = cx + r * math.cos(a), cy + r * math.sin(a)
        d.ellipse([x - w, y - w, x + w, y + w], fill=color)
    if dot:
        k = 0.05 * big
        d.ellipse([cx - k, cy - k, cx + k, cy + k], fill=color)
    return img.resize((size, size), Image.LANCZOS)


def save(img, rel):
    p = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
for name, f in dens.items():
    # adaptive foreground (108dp canvas, ring inside 66dp safe zone)
    fs = round(108 * f)
    save(ring(fs, 0.205, 0.06, FG), f"mipmap-{name}/ic_launcher_foreground.png")
    # legacy launcher icon (rounded square, 48dp-ish)
    ls = round(48 * f) if name != "mdpi" else 48
    big = ls * SS
    bg = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    ImageDraw.Draw(bg).rounded_rectangle([0, 0, big - 1, big - 1], radius=big * 0.22, fill=BG)
    bg = bg.resize((ls, ls), Image.LANCZOS)
    bg.alpha_composite(ring(ls, 0.30, 0.085, FG))
    save(bg, f"mipmap-{name}/ic_launcher.png")
    # notification small icons (24dp, white, alpha only matters)
    ns = round(24 * f)
    save(ring(ns, 0.36, 0.11, (255, 255, 255, 255)), f"drawable-{name}/ic_newpipe_triangle_white.png")
    save(ring(ns, 0.36, 0.11, (255, 255, 255, 255), dot=True), f"drawable-{name}/ic_newpipe_update.png")
    # splash screen ring (96dp, off-white on the sage splash background)
    save(ring(round(96 * f), 0.36, 0.07, FG), f"drawable-{name}/zen_splash_ring.png")

# Android TV banner 320x180
banner = Image.new("RGBA", (320, 180), BG)
banner.alpha_composite(ring(180, 0.27, 0.06, FG), (0, 0))
d = ImageDraw.Draw(banner)
for fp in ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"):
    if os.path.exists(fp):
        d.text((175, 92), "ZenPipe", fill=FG, font=ImageFont.truetype(fp, 34), anchor="lm")
        break
save(banner.convert("RGB"), "mipmap-xhdpi/newpipe_tv_banner.png")

# store icon 512
store = Image.new("RGBA", (512, 512), BG)
store.alpha_composite(ring(512, 0.28, 0.075, FG))
os.makedirs(os.path.join(ROOT, "fastlane/metadata/android/en-US/images"), exist_ok=True)
store.convert("RGB").save(os.path.join(ROOT, "fastlane/metadata/android/en-US/images/icon.png"))

# vector for the shared About page (470x464 viewport, white stroke)
cx, cy, r = 235, 232, 135
a0, a1 = math.radians(-40), math.radians(262)
p0 = (cx + r * math.cos(a0), cy + r * math.sin(a0))
p1 = (cx + r * math.cos(a1), cy + r * math.sin(a1))
print('M%.1f,%.1fA%d,%d 0 1,1 %.1f,%.1f' % (p0[0], p0[1], r, r, p1[0], p1[1]))
