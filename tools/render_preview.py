#!/usr/bin/env python3
"""Raster a keyboard preview SVG into the PNG we actually look at.

Two traps are baked into this script, both measured rather than assumed:

1. Chrome's headless `--screenshot` lays the page out in a viewport about 88 px SHORTER than the
   window size it is given, so a raster taken at exactly the board's height comes back with the
   bottom ~80 px unpainted - the board's lowest row of hexagons, cut off. So we always raster at
   `height + HEADROOM` and then crop the top `height` rows.
2. The picture has to CONTAIN the board. A hexagon hangs a point below its cell's bottom, so a
   height taken from the cells clips the bottom row's points. We take the height from the same
   shape geometry the renderer draws and then check the result: ink has to reach the lowest vertex.

Usage:  python3 render_preview.py            # both orientations, writes send-*.png
        python3 render_preview.py portrait   # one of them
"""
import re
import subprocess
import sys
from pathlib import Path

from PIL import Image
import numpy as np

CHROME = "/home/cascam/.hermes-seats/jadesoul/tools/chromium-1208/chrome-linux64/chrome"
BG = np.array([14, 20, 23])          # the plate, #0E1417
HEADROOM = 240                        # taller than any viewport shortfall we have measured
HERE = Path(__file__).resolve().parent


def svg_box(path):
    head = path.read_text(encoding="utf-8")[:400]
    w = float(re.search(r'width="([\d.]+)"', head).group(1))
    h = float(re.search(r'height="([\d.]+)"', head).group(1))
    return w, h


def lowest_point(svg_text):
    ys = [float(p.split(",")[1]) for m in re.findall(r'points="([^"]+)"', svg_text) for p in m.split()]
    return max(ys)


def raster(name):
    svg_path = HERE / f"{name}.svg"
    svg = svg_path.read_text(encoding="utf-8")
    w, h = svg_box(svg_path)
    low = lowest_point(svg)
    assert low < h - 2, f"{name}: the SVG clips its own bottom row ({low} vs {h})"
    html = (HERE / f"{name}.html")
    html.write_text(
        '<!doctype html><html><head><meta charset="utf-8"><style>'
        'html,body{margin:0;padding:0;background:#0E1417;}svg{display:block;}'
        f'</style></head><body>{svg}</body></html>', encoding="utf-8")
    shot = HERE / f"{name}-raw.png"
    subprocess.run([
        CHROME, "--headless", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
        "--force-device-scale-factor=1", f"--screenshot={shot}",
        f"--window-size={int(w + 0.5)},{int(h + 0.5) + HEADROOM}", f"file://{html}",
    ], check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    img = Image.open(shot).convert("RGB")
    img = img.crop((0, 0, img.size[0], int(h + 0.5)))
    out = HERE / f"{name}-final.png"
    img.save(out)

    # the check, on the pixels rather than the intent: ink has to reach the bottom row's points
    a = np.array(img)
    d = np.abs(a.astype(int) - BG).sum(axis=2)
    rows = np.where(d.max(axis=1) > 12)[0]
    reached = int(rows.max())
    assert reached >= low - 4, f"{name}: ink stops at {reached}, the hexagons go to {low} - clipped"
    print(f"{name}: {img.size} height={h} lowest_vertex={low} ink={rows.min()}..{reached} OK")
    return img


if __name__ == "__main__":
    names = sys.argv[1:] or ["portrait", "landscape"]
    for n in names:
        im = raster(n)
        if n == "portrait":
            im.save(HERE / "send-portrait.png")
        else:
            scale = 1500 / im.size[0]
            im.resize((1500, int(im.size[1] * scale)), Image.LANCZOS).save(HERE / "send-landscape.png")
    print("wrote", ", ".join(f"send-{n}.png" for n in names))
