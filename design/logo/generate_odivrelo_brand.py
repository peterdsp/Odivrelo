#!/usr/bin/env python3
"""Generates every Odivrelo brand asset from one piece of geometry.

The mark ("Route O") is a bold ring in deep teal blue with a road sweeping up
through it from the lower left, lane dashes down its middle, ending under a
warm orange node on the upper right of the ring. The owner's brand board fixes
the palette and the idea; this script fixes the geometry so every platform
ships the same shapes.

Everything is built as real vector geometry (shapely polygons), so the gaps
that separate the road from the ring and the node from the ring are true
knockouts rather than white paint. That is what lets one drawing work in full
colour on a light ground, in white on the deep blue app-icon ground, in a
single colour for Android's themed icon and iOS's tinted icon, and as
`currentColor` inline on the web.

Outputs (all committed, so builds need none of this script's dependencies):

  design/logo/*.svg                SVG masters (symbol, logo, app icon, favicon)
  design/logo/png/*.png            raster masters and previews
  design/Odivrelo-Brand-Board.svg  the brand board, plus design/logo/png/brand-board.png
  apps/web/public/favicon.svg      simplified favicon
  apps/web/public/icons/*          PWA icons, maskable icons, og image, mono mark
  apps/web/src/brand/markGeometry.generated.ts   path data for the inline marks
  apps/android/src/main/res/drawable/ic_launcher_*.xml, ic_notification.xml,
  ic_brand_mark.xml                vector drawables
  design/logo/png/ios/*            iOS app icon and in-app mark rasters, copied
                                   into the asset catalog by
                                   scripts/ios-generate-appicon.sh

Usage:

  python3 -m venv /tmp/odivrelo-brand && /tmp/odivrelo-brand/bin/pip install \
      -r design/logo/requirements.txt
  /tmp/odivrelo-brand/bin/python design/logo/generate_odivrelo_brand.py
  # after changing the wordmark font or text:
  /tmp/odivrelo-brand/bin/python design/logo/generate_odivrelo_brand.py \
      --font /path/to/Inter-Bold.ttf

The wordmark outline is cached in design/logo/src/wordmark.json so that a
normal run does not need the font file. Inter is licensed under the SIL Open
Font License 1.1; only the outlines of the eight letters are stored.
"""
from __future__ import annotations

import argparse
import io
import json
import math
import re
from pathlib import Path

from shapely import affinity
from shapely.geometry import MultiPolygon, Point, Polygon
from shapely.ops import unary_union

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
TOKENS = REPO / "design" / "tokens" / "odivrelo.tokens.json"
WORDMARK_CACHE = HERE / "src" / "wordmark.json"
WORDMARK_TEXT = "Odivrelo"


# --------------------------------------------------------------------------
# Palette. The brand board's values, read from the design tokens so the logo
# and the product themes cannot drift apart.
# --------------------------------------------------------------------------
def load_palette() -> dict[str, str]:
    tokens = json.loads(TOKENS.read_text(encoding="utf-8"))
    brand = tokens["color"]["brand"]
    return {key: value["$value"].upper() for key, value in brand.items()}


PALETTE = load_palette()
TEAL = PALETTE["deepTealBlue"]      # #0B4A6B, the ring and the icon ground
ORANGE = PALETTE["warmOrange"]      # #FF9F2E, the destination node
LIGHT = PALETTE["lightBackground"]  # #E6F1F7
NAVY = PALETTE["navy"]              # the road and the wordmark
NIGHT = PALETTE["night"]            # dark-appearance icon ground
WHITE = "#FFFFFF"
BLACK = "#000000"


# --------------------------------------------------------------------------
# Geometry, in a 100 unit square. The ring is centred at (50, 50).
# --------------------------------------------------------------------------
CENTRE = (50.0, 50.0)
RING_RADIUS = 31.5          # centreline
RING_WIDTH = 15.0
NODE_ANGLE = math.radians(-44.0)   # upper right, measured from +x, y down
NODE_RADIUS = 10.5
NODE_GAP = 3.2              # knockout between node and ring
EDGE_GAP = 2.6              # knockout between road and ring ("white edges")

# Road centreline: a cubic Bezier from beyond the ring's lower left, through
# the middle of the ring, to just under the node.
ROAD = ((4.0, 97.0), (30.0, 80.0), (44.0, 56.0), (0.0, 0.0))  # end set below
ROAD_WIDTH_START = 24.0     # near end, lower left
ROAD_WIDTH_END = 8.0        # far end, under the node: the road recedes
DASHES = (                  # (t0, t1) along the centreline
    (0.08, 0.20),
    (0.30, 0.40),
    (0.49, 0.57),
    (0.65, 0.71),
    (0.78, 0.82),
)
DASH_WIDTH_RATIO = 0.16     # of the local road width


def node_centre() -> tuple[float, float]:
    return (
        CENTRE[0] + RING_RADIUS * math.cos(NODE_ANGLE),
        CENTRE[1] + RING_RADIUS * math.sin(NODE_ANGLE),
    )


def bezier(points, t):
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = points
    u = 1 - t
    x = u**3 * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t**3 * x3
    y = u**3 * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t**3 * y3
    return x, y


def bezier_tangent(points, t):
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = points
    u = 1 - t
    dx = 3 * u * u * (x1 - x0) + 6 * u * t * (x2 - x1) + 3 * t * t * (x3 - x2)
    dy = 3 * u * u * (y1 - y0) + 6 * u * t * (y2 - y1) + 3 * t * t * (y3 - y2)
    n = math.hypot(dx, dy) or 1.0
    return dx / n, dy / n


def road_points():
    nx, ny = node_centre()
    # End a little inside the node so the road visibly arrives at it.
    return (ROAD[0], ROAD[1], ROAD[2], (nx - 3.0, ny + 3.0))


def road_width(t: float, extra: float = 0.0) -> float:
    return ROAD_WIDTH_START + (ROAD_WIDTH_END - ROAD_WIDTH_START) * t + 2 * extra


def band(t0: float, t1: float, width_fn, steps: int = 96) -> Polygon:
    pts = road_points()
    left, right = [], []
    for i in range(steps + 1):
        t = t0 + (t1 - t0) * i / steps
        x, y = bezier(pts, t)
        tx, ty = bezier_tangent(pts, t)
        nx, ny = -ty, tx
        half = width_fn(t) / 2
        left.append((x + nx * half, y + ny * half))
        right.append((x - nx * half, y - ny * half))
    return Polygon(left + right[::-1]).buffer(0)


def build_shapes(simplified: bool = False) -> dict[str, object]:
    """The three coloured parts of the mark, with the knockouts applied.

    ``simplified`` is the favicon cut: no lane dashes, a heavier ring and
    node, a narrower road and wider gaps, so the three shapes survive at 16
    pixels.
    """
    ring_width = RING_WIDTH + (5.0 if simplified else 0.0)
    ring = Point(CENTRE).buffer(RING_RADIUS + ring_width / 2, 128).difference(
        Point(CENTRE).buffer(RING_RADIUS - ring_width / 2, 128)
    )
    nx, ny = node_centre()
    node_r = NODE_RADIUS + (3.0 if simplified else 0.0)
    node = Point(nx, ny).buffer(node_r, 96)
    gap = NODE_GAP + (1.0 if simplified else 0.0)
    halo = Point(nx, ny).buffer(node_r + gap, 96)

    # The favicon cut keeps the road narrower and its edges wider, so the ring
    # still reads as an O when each unit is a fraction of a pixel.
    shrink = 0.8 if simplified else 1.0
    edge = EDGE_GAP + (2.0 if simplified else 0.0)
    road = band(0.0, 1.0, lambda t: road_width(t) * shrink)
    road_edge = band(0.0, 1.0, lambda t: road_width(t) * shrink + 2 * edge)

    dashes = []
    if not simplified:
        for t0, t1 in DASHES:
            dashes.append(band(t0, t1, lambda t: road_width(t) * DASH_WIDTH_RATIO, 24))
    dash_union = unary_union(dashes) if dashes else Polygon()

    ring_part = ring.difference(road_edge).difference(halo)
    road_part = road.difference(dash_union).difference(halo)
    return {"ring": ring_part, "road": road_part, "node": node, "silhouette": unary_union([ring_part, road_part, node])}


# --------------------------------------------------------------------------
# Wordmark
# --------------------------------------------------------------------------
def refresh_wordmark(font_path: Path) -> None:
    from fontTools.pens.svgPathPen import SVGPathPen
    from fontTools.pens.transformPen import TransformPen
    from fontTools.ttLib import TTFont

    font = TTFont(str(font_path))
    cmap = font.getBestCmap()
    glyphs = font.getGlyphSet()
    upm = font["head"].unitsPerEm
    hmtx = font["hmtx"]
    kern = {}
    tracking = -0.018 * upm  # slightly tight, as on the board
    x = 0.0
    pen = SVGPathPen(glyphs)
    names = [cmap[ord(ch)] for ch in WORDMARK_TEXT]
    for i, name in enumerate(names):
        tpen = TransformPen(pen, (1, 0, 0, -1, x, 0))
        glyphs[name].draw(tpen)
        x += hmtx[name][0] + tracking + kern.get((name, names[i + 1] if i + 1 < len(names) else None), 0)
    x -= tracking
    os2 = font["OS/2"]
    WORDMARK_CACHE.parent.mkdir(parents=True, exist_ok=True)
    WORDMARK_CACHE.write_text(
        json.dumps(
            {
                "text": WORDMARK_TEXT,
                "font": font["name"].getDebugName(4),
                "licence": "SIL Open Font License 1.1",
                "unitsPerEm": upm,
                "advance": round(x, 2),
                "capHeight": os2.sCapHeight,
                "xHeight": os2.sxHeight,
                "path": pen.getCommands(),
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print(f"wordmark outline cached from {font_path.name}")


def wordmark():
    return json.loads(WORDMARK_CACHE.read_text(encoding="utf-8"))


# --------------------------------------------------------------------------
# SVG helpers
# --------------------------------------------------------------------------
def fmt(v: float) -> str:
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def polys(geom):
    if geom.is_empty:
        return []
    if isinstance(geom, Polygon):
        return [geom]
    if isinstance(geom, MultiPolygon):
        return list(geom.geoms)
    return [g for g in getattr(geom, "geoms", []) if isinstance(g, Polygon)]


def path_data(geom, simplify: float = 0.03) -> str:
    out = []
    for poly in polys(geom.simplify(simplify, preserve_topology=True)):
        for ring in [poly.exterior, *poly.interiors]:
            coords = list(ring.coords)[:-1]
            if len(coords) < 3:
                continue
            out.append("M" + " ".join(f"{fmt(x)} {fmt(y)}" for x, y in coords[:1]))
            out.append("L" + " ".join(f"{fmt(x)} {fmt(y)}" for x, y in coords[1:]) + "Z")
    return "".join(out)


def svg(width, height, body, view=None, title="Odivrelo"):
    vb = view or f"0 0 {fmt(width)} {fmt(height)}"
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{fmt(width)}" height="{fmt(height)}" '
        f'viewBox="{vb}" role="img" aria-label="{title}">\n'
        f"<title>{title}</title>\n{body}\n</svg>\n"
    )


def symbol_body(shapes, ring, road, node, transform="") -> str:
    t = f' transform="{transform}"' if transform else ""
    return (
        f"<g{t}>"
        f'<path fill="{ring}" fill-rule="evenodd" d="{path_data(shapes["ring"])}"/>'
        f'<path fill="{road}" fill-rule="evenodd" d="{path_data(shapes["road"])}"/>'
        f'<path fill="{node}" d="{path_data(shapes["node"])}"/>'
        "</g>"
    )


def symbol_bounds(shapes):
    return shapes["silhouette"].bounds  # minx, miny, maxx, maxy


def optical_offset(shapes, size: float, scale: float) -> tuple[float, float]:
    """Translate that centres the mark in a square of `size`.

    The road's tail pulls the bounding box to the lower left, so centring the
    box leaves the ring visibly high and right. The optical centre used here is
    halfway between the box's centre and the ring's centre.
    """
    minx, miny, maxx, maxy = symbol_bounds(shapes)
    cx = ((minx + maxx) / 2 + CENTRE[0]) / 2
    cy = ((miny + maxy) / 2 + CENTRE[1]) / 2
    return size / 2 - cx * scale, size / 2 - cy * scale


# Colourways: (ring, road, node) for the symbol, and the wordmark colour.
COLOURWAYS = {
    "color": (TEAL, NAVY, ORANGE, NAVY),
    "on-dark": (WHITE, WHITE, ORANGE, WHITE),
    "black": (BLACK, BLACK, BLACK, BLACK),
    "white": (WHITE, WHITE, WHITE, WHITE),
}


def logo_horizontal(shapes, colourway: str, background: str | None = None) -> str:
    ring, road, node, text = COLOURWAYS[colourway]
    wm = wordmark()
    minx, miny, maxx, maxy = symbol_bounds(shapes)
    sym_h = maxy - miny
    # The wordmark's cap height is 44% of the symbol's height, centred on the ring.
    scale = (0.44 * sym_h) / wm["capHeight"]
    gap = 0.22 * sym_h
    clear = 0.25 * (2 * (RING_RADIUS + RING_WIDTH / 2))
    text_w = wm["advance"] * scale
    width = clear + (maxx - minx) + gap + text_w + clear
    height = clear + sym_h + clear
    ox, oy = clear - minx, clear - miny
    baseline = oy + CENTRE[1] + (wm["capHeight"] * scale) / 2
    tx = clear + (maxx - minx) + gap
    body = []
    if background:
        body.append(f'<rect width="{fmt(width)}" height="{fmt(height)}" fill="{background}"/>')
    body.append(symbol_body(shapes, ring, road, node, f"translate({fmt(ox)} {fmt(oy)})"))
    body.append(
        f'<path fill="{text}" transform="translate({fmt(tx)} {fmt(baseline)}) scale({scale:.5f})" d="{wm["path"]}"/>'
    )
    return svg(width, height, "".join(body))


def symbol_svg(shapes, colourway: str, pad: float = 0.0, background: str | None = None) -> str:
    ring, road, node, _ = COLOURWAYS[colourway]
    minx, miny, maxx, maxy = symbol_bounds(shapes)
    w, h = maxx - minx, maxy - miny
    side = max(w, h) + 2 * pad
    ox = (side - w) / 2 - minx
    oy = (side - h) / 2 - miny
    body = []
    if background:
        body.append(f'<rect width="{fmt(side)}" height="{fmt(side)}" fill="{background}"/>')
    body.append(symbol_body(shapes, ring, road, node, f"translate({fmt(ox)} {fmt(oy)})"))
    return svg(side, side, "".join(body))


def app_icon_svg(shapes, ground, ring, road, node, size=1024, symbol_fraction=0.64) -> str:
    """Square, opaque, no corner mask: the platform applies its own."""
    minx, miny, maxx, maxy = symbol_bounds(shapes)
    w, h = maxx - minx, maxy - miny
    scale = size * symbol_fraction / max(w, h)
    ox, oy = optical_offset(shapes, size, scale)
    body = f'<rect width="{size}" height="{size}" fill="{ground}"/>' + symbol_body(
        shapes, ring, road, node, f"translate({fmt(ox)} {fmt(oy)}) scale({scale:.5f})"
    )
    return svg(size, size, body)


def tile_svg(shapes, fraction: float = 0.80) -> str:
    """A rounded deep blue tile, so the mark reads on light and dark browser
    chrome alike. Used for the favicon and the PWA's "any" purpose icons."""
    minx, miny, maxx, maxy = symbol_bounds(shapes)
    w, h = maxx - minx, maxy - miny
    size = 64
    scale = size * fraction / max(w, h)
    ox, oy = optical_offset(shapes, size, scale)
    body = f'<rect width="64" height="64" rx="14" fill="{TEAL}"/>' + symbol_body(
        shapes, WHITE, WHITE, ORANGE, f"translate({fmt(ox)} {fmt(oy)}) scale({scale:.5f})"
    )
    return svg(size, size, body)


def favicon_svg(shapes_simple) -> str:
    return tile_svg(shapes_simple, 0.84)


def mono_svg(shapes) -> str:
    minx, miny, maxx, maxy = symbol_bounds(shapes)
    side = max(maxx - minx, maxy - miny)
    ox = (side - (maxx - minx)) / 2 - minx
    oy = (side - (maxy - miny)) / 2 - miny
    body = (
        f'<path fill="currentColor" fill-rule="evenodd" transform="translate({fmt(ox)} {fmt(oy)})" '
        f'd="{path_data(shapes["silhouette"])}"/>'
    )
    return svg(side, side, body)


# --------------------------------------------------------------------------
# Rasterising
# --------------------------------------------------------------------------
def render(svg_text: str, width: int, height: int | None = None, opaque: bool = False) -> bytes:
    import resvg_py
    from PIL import Image

    data = svg_text
    m = re.search(r'width="([\d.]+)" height="([\d.]+)"', data)
    src_w, src_h = float(m.group(1)), float(m.group(2))
    if height is None:
        height = round(width * src_h / src_w)
    data = re.sub(r'width="[\d.]+" height="[\d.]+"', f'width="{width}" height="{height}"', data, count=1)
    png = bytes(resvg_py.svg_to_bytes(svg_string=data))
    if not opaque:
        return png
    image = Image.open(io.BytesIO(png)).convert("RGBA")
    flat = Image.new("RGB", image.size, (0, 0, 0))
    flat.paste(image, mask=image.split()[3])
    out = io.BytesIO()
    flat.save(out, format="PNG", optimize=True)
    return out.getvalue()


def write(path: Path, content) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(content, str):
        path.write_text(content, encoding="utf-8")
    else:
        path.write_bytes(content)
    print(f"  {path.relative_to(REPO)}")


# --------------------------------------------------------------------------
# Android vector drawables
# --------------------------------------------------------------------------
def android_vector(parts, viewport: float, size_dp: float, comment: str, tint: str | None = None) -> str:
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!--",
        *[f"  {line}" if line else "" for line in comment.strip().splitlines()],
        "",
        "  Generated by design/logo/generate_odivrelo_brand.py. Do not edit by hand.",
        "-->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{fmt(size_dp)}dp"',
        f'    android:height="{fmt(size_dp)}dp"',
        f'    android:viewportWidth="{fmt(viewport)}"',
        f'    android:viewportHeight="{fmt(viewport)}"' + (">" if not tint else ""),
    ]
    if tint:
        lines.append(f'    android:tint="{tint}">')
    for colour, data in parts:
        lines += [
            "    <path",
            f'        android:fillColor="{colour}"',
            '        android:fillType="evenOdd"',
            f'        android:pathData="{data}" />',
        ]
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def placed(geom, shapes, target: float, offset: float):
    """Scale and translate a part so the whole mark fits `target` units at `offset`."""
    minx, miny, maxx, maxy = symbol_bounds(shapes)
    w, h = maxx - minx, maxy - miny
    scale = target / max(w, h)
    cx = ((minx + maxx) / 2 + CENTRE[0]) / 2  # the optical centre, see optical_offset
    cy = ((miny + maxy) / 2 + CENTRE[1]) / 2
    g = affinity.translate(geom, -cx, -cy)
    g = affinity.scale(g, scale, scale, origin=(0, 0))
    return affinity.translate(g, offset, offset)


# --------------------------------------------------------------------------
# Brand board
# --------------------------------------------------------------------------
def brand_board(shapes, shapes_simple) -> str:
    W, H = 1600, 1100
    wm = wordmark()
    out = [f'<rect width="{W}" height="{H}" fill="{WHITE}"/>']
    font = "font-family=\"Inter, 'SF Pro Text', Roboto, Helvetica, Arial, sans-serif\""

    def text(x, y, s, size=14, weight=500, fill=NAVY, anchor="start"):
        return (
            f'<text x="{x}" y="{y}" {font} font-size="{size}" font-weight="{weight}" '
            f'fill="{fill}" text-anchor="{anchor}">{s}</text>'
        )

    def embed(svg_text, x, y, w, h=None):
        m = re.search(r'viewBox="([^"]+)"', svg_text)
        vb = m.group(1)
        inner = re.sub(r"^.*?<title>[^<]*</title>\n", "", svg_text, flags=re.S).rsplit("</svg>", 1)[0]
        hh = h if h is not None else w
        return f'<svg x="{fmt(x)}" y="{fmt(y)}" width="{fmt(w)}" height="{fmt(hh)}" viewBox="{vb}">{inner}</svg>'

    out.append(text(60, 78, "Odivrelo", 40, 800))
    out.append(text(60, 108, "Brand board. Route O: a ring, a road through it, and the destination.", 16, 500, "#4A5F6D"))

    # Row 1: horizontal logos on four grounds.
    cells = [
        ("Full colour, light", "color", LIGHT),
        ("Full colour, dark", "on-dark", TEAL),
        ("Black", "black", WHITE),
        ("White", "white", NAVY),
    ]
    x = 60
    for label, way, ground in cells:
        out.append(f'<rect x="{x}" y="140" width="355" height="170" rx="16" fill="{ground}" stroke="#C9D8E1"/>')
        logo = logo_horizontal(shapes, way)
        m = re.search(r'width="([\d.]+)" height="([\d.]+)"', logo)
        lw, lh = float(m.group(1)), float(m.group(2))
        w = 315
        out.append(embed(logo, x + 20, 140 + (170 - w * lh / lw) / 2, w, w * lh / lw))
        out.append(text(x, 334, label, 13, 650))
        x += 375

    # Row 2: symbol, app icons, favicon sizes.
    y = 380
    out.append(text(60, y, "Symbol and app icon", 18, 750))
    out.append(embed(symbol_svg(shapes, "color"), 60, y + 20, 200))
    out.append(text(60, y + 245, "Symbol, 1024 master", 13, 650))
    out.append(embed(app_icon_svg(shapes, TEAL, WHITE, WHITE, ORANGE), 300, y + 20, 200))
    out.append(text(300, y + 245, "App icon, deep blue", 13, 650))
    out.append(embed(app_icon_svg(shapes, LIGHT, TEAL, NAVY, ORANGE), 540, y + 20, 200))
    out.append(text(540, y + 245, "App icon, light", 13, 650))
    out.append(text(540, y + 263, "Square master, no corner mask", 12, 500, "#4A5F6D"))

    # Minimum sizes.
    out.append(text(800, y, "Minimum sizes", 18, 750))
    fx = 800
    for px in (16, 24, 32, 60):
        out.append(embed(favicon_svg(shapes_simple) if px < 32 else symbol_svg(shapes, "color"), fx, y + 120 - px, px))
        out.append(text(fx, y + 140, f"{px} px", 12, 600))
        fx += px + 40
    out.append(text(800, y + 170, "16 px favicon uses the simplified cut: no dashes, heavier strokes.", 12, 500, "#4A5F6D"))
    out.append(text(800, y + 188, "60 px is the smallest mobile header size. 1024 px is the app icon master.", 12, 500, "#4A5F6D"))

    # Clear space diagram.
    cx0 = 1230
    out.append(text(cx0, y, "Clear space", 18, 750))
    d = 2 * (RING_RADIUS + RING_WIDTH / 2)
    s = 150 / d
    out.append(f'<rect x="{cx0}" y="{y + 20}" width="{fmt(150 + 2 * 0.25 * 150)}" height="{fmt(150 + 2 * 0.25 * 150)}" fill="none" stroke="{ORANGE}" stroke-dasharray="6 5"/>')
    out.append(embed(symbol_svg(shapes, "color"), cx0 + 0.25 * 150, y + 20 + 0.25 * 150, 150))
    out.append(text(cx0, y + 268, "Keep 1/4 of the symbol diameter clear on every side.", 12, 500, "#4A5F6D"))

    # Row 3: palette.
    y = 690
    out.append(text(60, y, "Colour", 18, 750))
    swatches = [
        ("Deep Teal Blue", TEAL, WHITE),
        ("Warm Orange", ORANGE, NAVY),
        ("Light Background", LIGHT, NAVY),
        ("Navy", NAVY, WHITE),
        ("White", WHITE, NAVY),
        ("Black", BLACK, WHITE),
    ]
    x = 60
    for name, hexv, ink in swatches:
        out.append(f'<rect x="{x}" y="{y + 20}" width="220" height="120" rx="14" fill="{hexv}" stroke="#C9D8E1"/>')
        out.append(text(x + 16, y + 110, name, 14, 700, ink))
        out.append(text(x + 16, y + 128, hexv, 13, 500, ink))
        x += 240
    out.append(text(60, y + 170, "Orange is an accent for the node and highlights. It is never body text on white.", 12, 500, "#4A5F6D"))

    # Row 4: typography and rules.
    y = 900
    out.append(text(60, y, "Typography", 18, 750))
    scale = 44 / wm["capHeight"]
    out.append(f'<path fill="{NAVY}" transform="translate(60 {y + 70}) scale({scale:.5f})" d="{wm["path"]}"/>')
    out.append(text(60, y + 110, "Wordmark: Inter Bold, outlined. Interface: Inter, then the platform system font.", 12, 500, "#4A5F6D"))
    out.append(text(800, y, "Rules", 18, 750))
    rules = [
        "Do not recolour the node away from orange in colour versions.",
        "Do not stretch, rotate, outline or add effects to the mark.",
        "Use the white or black one-colour version where contrast needs it.",
        "Odivrelo in running text; ODIVRELO only where a layout calls for capitals.",
    ]
    for i, r in enumerate(rules):
        out.append(text(800, y + 30 + i * 22, r, 13, 500))
    return svg(W, H, "".join(out))


# --------------------------------------------------------------------------
def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--font", type=Path, help="Inter Bold TTF, to refresh the cached wordmark outline")
    parser.add_argument("--no-png", action="store_true", help="write SVG and vector outputs only")
    args = parser.parse_args()
    if args.font:
        refresh_wordmark(args.font)

    shapes = build_shapes()
    simple = build_shapes(simplified=True)
    logo = HERE
    png = HERE / "png"

    print("SVG masters")
    masters = {
        "odivrelo-symbol.svg": symbol_svg(shapes, "color"),
        "odivrelo-symbol-on-dark.svg": symbol_svg(shapes, "on-dark"),
        "odivrelo-symbol-black.svg": symbol_svg(shapes, "black"),
        "odivrelo-symbol-white.svg": symbol_svg(shapes, "white"),
        "odivrelo-symbol-mono.svg": mono_svg(shapes),
        "odivrelo-favicon.svg": favicon_svg(simple),
        "odivrelo-logo-horizontal.svg": logo_horizontal(shapes, "color"),
        "odivrelo-logo-horizontal-on-dark.svg": logo_horizontal(shapes, "on-dark", TEAL),
        "odivrelo-logo-horizontal-black.svg": logo_horizontal(shapes, "black"),
        "odivrelo-logo-horizontal-white.svg": logo_horizontal(shapes, "white"),
        "odivrelo-app-icon.svg": app_icon_svg(shapes, TEAL, WHITE, WHITE, ORANGE),
        "odivrelo-app-icon-light.svg": app_icon_svg(shapes, LIGHT, TEAL, NAVY, ORANGE),
        "odivrelo-app-icon-dark.svg": app_icon_svg(shapes, NIGHT, WHITE, WHITE, ORANGE),
        "odivrelo-app-icon-tinted.svg": app_icon_svg(shapes, "#000000", "#E8E8E8", "#E8E8E8", "#FFFFFF"),
    }
    for name, text in masters.items():
        write(logo / name, text)
    board = brand_board(shapes, simple)
    write(REPO / "design" / "Odivrelo-Brand-Board.svg", board)

    print("Web")
    web = REPO / "apps" / "web"
    write(web / "public" / "favicon.svg", masters["odivrelo-favicon.svg"])
    write(web / "public" / "icons" / "mark-mono.svg", masters["odivrelo-symbol-mono.svg"])
    write(web / "src" / "brand" / "markGeometry.generated.ts", web_geometry_module(shapes))

    print("Android")
    res = REPO / "apps" / "android" / "src" / "main" / "res" / "drawable"
    # Adaptive icons are 108 units with a 66 unit safe circle; the mark fills 60.
    fg = [
        (WHITE, path_data(placed(shapes["ring"], shapes, 60, 54))),
        (WHITE, path_data(placed(shapes["road"], shapes, 60, 54))),
        (ORANGE, path_data(placed(shapes["node"], shapes, 60, 54))),
    ]
    write(res / "ic_launcher_foreground.xml", android_vector(fg, 108, 108, """
The Odivrelo mark for the adaptive launcher icon: a white ring crossed by a
white road with lane dashes, and the orange destination node. The mark spans
60 of the 108 unit viewport, inside the 66 unit safe zone, so no mask shape
clips it. The ground is ic_launcher_background.xml.
"""))
    write(res / "ic_launcher_background.xml", android_vector([(TEAL, "M0 0H108V108H0Z")], 108, 108, """
The deep teal blue ground of the adaptive launcher icon, color.brand.deepTealBlue
in design/tokens/odivrelo.tokens.json. The launcher's mask supplies the shape.
"""))
    mono = [("#000000", path_data(placed(shapes["silhouette"], shapes, 60, 54)))]
    write(res / "ic_launcher_monochrome.xml", android_vector(mono, 108, 108, """
The single colour layer Android 13 and later tint for a themed icon. It is the
whole mark as one silhouette; the road's edges, the lane dashes and the gap
around the node are real holes, so the mark still reads in one colour.
"""))
    note = [("#FFFFFFFF", path_data(placed(simple["silhouette"], simple, 22, 12)))]
    write(res / "ic_notification.xml", android_vector(note, 24, 24, """
The status bar mark. Android draws a notification's small icon as a single
tinted silhouette, so this is the simplified cut of the mark (no lane dashes,
heavier strokes) at 24dp.
""", tint="#FFFFFFFF"))
    brand_mark = [
        ("@color/odivrelo_mark_ring", path_data(placed(shapes["ring"], shapes, 100, 50))),
        ("@color/odivrelo_mark_road", path_data(placed(shapes["road"], shapes, 100, 50))),
        (ORANGE, path_data(placed(shapes["node"], shapes, 100, 50))),
    ]
    write(res / "ic_brand_mark.xml", android_vector(brand_mark, 100, 48, """
The in-app Odivrelo mark. Ring and road colours come from colors.xml, so the
mark follows the light and dark themes; the node is always orange.
"""))

    if args.no_png:
        return

    print("PNG masters")
    write(png / "odivrelo-app-icon-1024.png", render(masters["odivrelo-app-icon.svg"], 1024, opaque=True))
    write(png / "odivrelo-app-icon-light-1024.png", render(masters["odivrelo-app-icon-light.svg"], 1024, opaque=True))
    write(png / "odivrelo-symbol-1024.png", render(symbol_svg(shapes, "color", pad=0), 1024))
    write(png / "odivrelo-symbol-white-1024.png", render(symbol_svg(shapes, "white", pad=0), 1024))
    write(png / "odivrelo-logo-horizontal.png", render(masters["odivrelo-logo-horizontal.svg"], 1600))
    write(png / "odivrelo-logo-horizontal-on-dark.png", render(masters["odivrelo-logo-horizontal-on-dark.svg"], 1600))
    write(png / "odivrelo-logo-horizontal-black.png", render(masters["odivrelo-logo-horizontal-black.svg"], 1600))
    write(png / "odivrelo-logo-horizontal-white.png", render(masters["odivrelo-logo-horizontal-white.svg"], 1600))
    for px in (16, 32, 48):
        write(png / f"odivrelo-favicon-{px}.png", render(masters["odivrelo-favicon.svg"], px))
    write(png / "brand-board.png", render(board, 1600))

    print("iOS")
    ios = png / "ios"
    write(ios / "AppIcon-1024.png", render(masters["odivrelo-app-icon.svg"], 1024, opaque=True))
    write(ios / "AppIcon-1024-Dark.png", render(masters["odivrelo-app-icon-dark.svg"], 1024, opaque=True))
    write(ios / "AppIcon-1024-Tinted.png", render(masters["odivrelo-app-icon-tinted.svg"], 1024, opaque=True))
    light_mark = symbol_svg(shapes, "color", pad=6)
    dark_mark = symbol_svg(shapes, "on-dark", pad=6)
    for suffix, px in (("", 171), ("@2x", 341), ("@3x", 512)):
        write(ios / f"BrandMark{suffix}.png", render(light_mark, px))
        write(ios / f"BrandMark-Dark{suffix}.png", render(dark_mark, px))
    # The launch screen shows the mark at its natural size, 96 points.
    for suffix, px in (("", 96), ("@2x", 192), ("@3x", 288)):
        write(ios / f"LaunchMark{suffix}.png", render(light_mark, px))
        write(ios / f"LaunchMark-Dark{suffix}.png", render(dark_mark, px))

    print("Web rasters")
    icons = web / "public" / "icons"
    # Below 96 px the lane dashes turn to noise, so the small sizes use the
    # favicon cut. Every "any" icon is a rounded tile with transparent corners.
    small, large = masters["odivrelo-favicon.svg"], tile_svg(shapes, 0.74)
    for px in (32, 48, 64, 96, 128, 180, 192, 256, 384, 512):
        write(icons / f"icon-{px}.png", render(small if px < 96 else large, px))
    # iOS rounds the touch icon itself, so it is the square, opaque master.
    write(icons / "apple-touch-icon.png", render(masters["odivrelo-app-icon.svg"], 180, opaque=True))
    maskable = app_icon_svg(shapes, TEAL, WHITE, WHITE, ORANGE, symbol_fraction=0.50)
    for px in (192, 512):
        write(icons / f"maskable-{px}.png", render(maskable, px, opaque=True))
    write(icons / "og-image.png", render(og_image(shapes), 1200, opaque=True))


def og_image(shapes) -> str:
    W, H = 1200, 630
    logo = logo_horizontal(shapes, "color")
    m = re.search(r'width="([\d.]+)" height="([\d.]+)"', logo)
    lw, lh = float(m.group(1)), float(m.group(2))
    w = 760
    h = w * lh / lw
    inner = re.sub(r"^.*?<title>[^<]*</title>\n", "", logo, flags=re.S).rsplit("</svg>", 1)[0]
    vb = re.search(r'viewBox="([^"]+)"', logo).group(1)
    body = (
        f'<rect width="{W}" height="{H}" fill="{LIGHT}"/>'
        f'<svg x="{fmt((W - w) / 2)}" y="{fmt((H - h) / 2)}" width="{fmt(w)}" height="{fmt(h)}" viewBox="{vb}">{inner}</svg>'
    )
    return svg(W, H, body)


def web_geometry_module(shapes) -> str:
    minx, miny, maxx, maxy = symbol_bounds(shapes)
    side = max(maxx - minx, maxy - miny)
    ox = (side - (maxx - minx)) / 2 - minx
    oy = (side - (maxy - miny)) / 2 - miny

    def moved(geom):
        return path_data(affinity.translate(geom, ox, oy))

    return (
        "// GENERATED FILE. Do not edit by hand.\n"
        "//\n"
        "// Source: design/logo/generate_odivrelo_brand.py. The Odivrelo mark as path\n"
        "// data, so the inline marks in Marks.tsx draw exactly the shapes every other\n"
        "// platform ships. The gaps between the parts are real holes, not paint.\n"
        "\n"
        f"export const MARK_VIEWBOX = '0 0 {fmt(side)} {fmt(side)}';\n"
        f"export const MARK_RING = '{moved(shapes['ring'])}';\n"
        f"export const MARK_ROAD = '{moved(shapes['road'])}';\n"
        f"export const MARK_NODE = '{moved(shapes['node'])}';\n"
        f"export const MARK_SILHOUETTE = '{moved(shapes['silhouette'])}';\n"
    )


if __name__ == "__main__":
    main()
