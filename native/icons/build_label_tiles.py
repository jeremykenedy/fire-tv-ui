#!/usr/bin/env python3
"""Render original text tiles for catalog names whose legacy artwork is held."""

import io
import subprocess
from pathlib import Path
from xml.sax.saxutils import escape

from PIL import Image


ROOT = Path(__file__).resolve().parent
TILES = [
    ("nes", "NES", "#d83a40"),
    ("snes", "SNES", "#7766c3"),
    ("sega-genesis", "Sega Genesis", "#2873cf"),
    ("sega-master-system", "Sega Master System", "#d84040"),
    ("playstation-1", "PlayStation 1", "#2d69ce"),
    ("playstation-2", "PlayStation 2", "#2d69ce"),
    ("playstation-3", "PlayStation 3", "#2d69ce"),
    ("playstation-4", "PlayStation 4", "#2d69ce"),
    ("playstation-5", "PlayStation 5", "#2d69ce"),
    ("xbox", "Xbox", "#299448"),
    ("xbox-360", "Xbox 360", "#299448"),
    ("xbox-one", "Xbox One", "#299448"),
    ("wii", "Wii", "#31a6bf"),
    ("wii-u", "Wii U", "#31a6bf"),
    ("switch", "Nintendo Switch", "#df3445"),
    ("nintendo-64", "Nintendo 64", "#299448"),
    ("gamecube", "Nintendo GameCube", "#7766c3"),
    ("dreamcast", "Dreamcast", "#ec7234"),
    ("sega-saturn", "Sega Saturn", "#2873cf"),
    ("yamaha", "Yamaha", "#9258bd"),
    ("onkyo", "Onkyo", "#446da7"),
    ("marantz", "Marantz", "#a48a53"),
    ("bose", "Bose", "#738799"),
    ("vizio", "Vizio", "#bc743d"),
    ("klipsch", "Klipsch", "#98754b"),
]


def run_renderer(source):
    return subprocess.check_output(["rsvg-convert"], input=source.encode())


def render_tile(icon_id, name, accent):
    label = escape(name)
    measurement = (f'<svg xmlns="http://www.w3.org/2000/svg" width="4096" height="1080">'
                   f'<text x="2048" y="540" fill="white" font-family="sans-serif" '
                   f'font-size="260" text-anchor="middle" dominant-baseline="central">{label}</text></svg>')
    image = Image.open(io.BytesIO(run_renderer(measurement))).convert("RGBA")
    bounds = image.getchannel("A").getbbox()
    if not bounds:
        raise ValueError(f"Empty tile label: {name}")
    width = bounds[2] - bounds[0]
    scale = min(1, 1320 / width)
    source = f'''<svg xmlns="http://www.w3.org/2000/svg" width="1920" height="1080" viewBox="0 0 1920 1080">
  <defs>
    <linearGradient id="background" x2="1" y2="1">
      <stop stop-color="#1d293c"/>
      <stop offset="1" stop-color="#090f19"/>
    </linearGradient>
    <radialGradient id="glow">
      <stop stop-color="{accent}" stop-opacity=".25"/>
      <stop offset="1" stop-color="{accent}" stop-opacity="0"/>
    </radialGradient>
  </defs>
  <rect width="1920" height="1080" fill="url(#background)"/>
  <ellipse cx="960" cy="540" rx="1100" ry="800" fill="url(#glow)"/>
  <g transform="translate(960 540) scale({scale:.6f})">
    <text x="0" y="0" fill="white" font-family="sans-serif" font-size="260"
          text-anchor="middle" dominant-baseline="central">{label}</text>
  </g>
</svg>
'''
    source_path = ROOT / "sources" / f"{icon_id}.svg"
    image_path = ROOT.parent / "res" / "drawable-nodpi" / f"icon_{icon_id.replace('-', '_')}.png"
    source_path.write_text(source)
    image_path.write_bytes(run_renderer(source))


if __name__ == "__main__":
    for tile in TILES:
        render_tile(*tile)
