#!/usr/bin/env python3
"""Render the console tile catalog from its bundled vector logos."""

import base64
import io
import json
from pathlib import Path
import shutil
import subprocess

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]


def build():
    renderer = shutil.which('rsvg-convert')
    if not renderer:
        raise SystemExit('Install librsvg to provide rsvg-convert before rendering icons.')
    catalog = json.loads((ROOT / 'icons/catalog.json').read_text())
    for entry in catalog:
        source = ROOT / entry['source']
        artwork = source.read_bytes()
        if entry.get('remove_background'):
            background = b'<path fill="#e60012" d="M0 0h261.88v261.88H0z"/>'
            if artwork.count(background) != 1:
                raise ValueError('The Switch 2 background anchor changed.')
            artwork = artwork.replace(background, b'', 1)
        preview = Image.open(io.BytesIO(subprocess.check_output(
            [renderer, '--width', '1920'], input=artwork))).convert('RGBA')
        bounds = preview.getchannel('A').getbbox()
        if not bounds:
            raise ValueError('The source logo is empty: ' + entry['name'])
        left, top, right, bottom = bounds
        scale = min(1320 / (right - left), 660 / (bottom - top))
        width, height = preview.width * scale, preview.height * scale
        x = (1920 - (right - left) * scale) / 2 - left * scale
        y = (1080 - (bottom - top) * scale) / 2 - top * scale
        data = base64.b64encode(artwork).decode('ascii')
        colored = entry['id'] == 'playstation-1' or entry.get('preserve_color')
        filter_value = '' if colored else 'filter="url(#white)"'
        svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="1920" height="1080" viewBox="0 0 1920 1080">
<defs>
  <linearGradient id="background" x2="1" y2="1">
    <stop stop-color="#1d293c"/><stop offset="1" stop-color="#090f19"/>
  </linearGradient>
  <radialGradient id="glow">
    <stop stop-color="{entry['accent']}" stop-opacity=".25"/>
    <stop offset="1" stop-color="{entry['accent']}" stop-opacity="0"/>
  </radialGradient>
  <filter id="white" color-interpolation-filters="sRGB">
    <feColorMatrix type="matrix" values="0 0 0 0 1 0 0 0 0 1 0 0 0 0 1 0 0 0 1 0"/>
  </filter>
</defs>
<rect width="1920" height="1080" fill="url(#background)"/>
<ellipse cx="960" cy="540" rx="1100" ry="800" fill="url(#glow)"/>
<image x="{x:.4f}" y="{y:.4f}" width="{width:.4f}" height="{height:.4f}" href="data:image/svg+xml;base64,{data}" {filter_value}/>
</svg>'''
        output = ROOT / 'icons' / entry['file']
        subprocess.run([renderer, '--output', str(output)], input=svg.encode(), check=True)
        print(entry['name'])


if __name__ == '__main__':
    build()
