#!/usr/bin/env node
/**
 * Renders the Poravia mark into every PNG size the web app needs.
 *
 * There is no SVG rasteriser on the build machine and none is added as a
 * dependency, so the mark's geometry is redrawn here directly from
 * design/logo/poravia-mark.svg and encoded as PNG with Node's own zlib. The
 * shapes are the same three the SVG uses, in the same places, at the same
 * colours, so Web, iOS and Android ship one mark:
 *
 *   rect   0,0 64x64 rx 15                  fill   #0B6B63  (Aegean teal ground)
 *   path   M16 53 V30 a16 16 0 0 1 32 0 V53 stroke #EFF7F5  (limestone arch)
 *   circle 32,34 r 5.5                      fill   #F2B84B  (sun amber point)
 *
 * Coverage is computed by 4x4 supersampling of exact distance fields rather than
 * by scanline filling, which gives clean edges at 32 px where the arch and the
 * point still have to read as two distinct shapes.
 *
 * The maskable icon draws the same artwork at 72% scale on a full-bleed ground,
 * so nothing is clipped by a platform mask however aggressive its safe zone is.
 */
import { deflateSync } from 'node:zlib';
import { copyFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const webRoot = join(here, '..');
const repoRoot = join(webRoot, '..', '..');
const logoDir = join(repoRoot, 'design', 'logo');
const iconsDir = join(webRoot, 'public', 'icons');

const TEAL = [0x0b, 0x6b, 0x63];
const LIMESTONE = [0xef, 0xf7, 0xf5];
const AMBER = [0xf2, 0xb8, 0x4b];

// --- geometry, in the SVG's own 64-unit space ------------------------------
const CORNER = 15;
const ARCH_HALF_WIDTH = 7.5 / 2;
const ARCH_LEFT_X = 16;
const ARCH_RIGHT_X = 48;
const ARCH_BOTTOM_Y = 53;
const ARCH_CENTRE = { x: 32, y: 30 };
const ARCH_RADIUS = 16;
const POINT = { x: 32, y: 34, r: 5.5 };

/** Distance from a point to a vertical segment, giving the stroke its round caps. */
function distanceToVerticalSegment(px, py, x, y0, y1) {
  const clampedY = Math.min(Math.max(py, Math.min(y0, y1)), Math.max(y0, y1));
  return Math.hypot(px - x, py - clampedY);
}

/** Distance to the upper half of the arch circle. */
function distanceToArc(px, py) {
  const dx = px - ARCH_CENTRE.x;
  const dy = py - ARCH_CENTRE.y;
  if (dy > 0) {
    // Below the arc's springing line: the nearest point on the top half is an end.
    return Math.min(
      Math.hypot(px - (ARCH_CENTRE.x - ARCH_RADIUS), py - ARCH_CENTRE.y),
      Math.hypot(px - (ARCH_CENTRE.x + ARCH_RADIUS), py - ARCH_CENTRE.y),
    );
  }
  return Math.abs(Math.hypot(dx, dy) - ARCH_RADIUS);
}

function distanceToArch(px, py) {
  return Math.min(
    distanceToVerticalSegment(px, py, ARCH_LEFT_X, ARCH_CENTRE.y, ARCH_BOTTOM_Y),
    distanceToVerticalSegment(px, py, ARCH_RIGHT_X, ARCH_CENTRE.y, ARCH_BOTTOM_Y),
    distanceToArc(px, py),
  );
}

/** Inside the rounded square? Exact, including the corner arcs. */
function insideRoundedRect(px, py) {
  if (px < 0 || py < 0 || px > 64 || py > 64) return false;
  const cx = Math.min(Math.max(px, CORNER), 64 - CORNER);
  const cy = Math.min(Math.max(py, CORNER), 64 - CORNER);
  const dx = px - cx;
  const dy = py - cy;
  if (dx === 0 || dy === 0) return true;
  return Math.hypot(dx, dy) <= CORNER;
}

const SAMPLES = 4;

/**
 * Colour and alpha for one output pixel.
 *
 * `scale` shrinks the artwork about the centre for the maskable variant, and
 * `fullBleed` keeps the teal ground covering the whole canvas rather than
 * following the rounded corners.
 */
function samplePixel(x, y, size, { scale, fullBleed }) {
  let r = 0;
  let g = 0;
  let b = 0;
  let a = 0;
  const step = 1 / SAMPLES;
  for (let sy = 0; sy < SAMPLES; sy += 1) {
    for (let sx = 0; sx < SAMPLES; sx += 1) {
      // Sample position in the 64-unit design space.
      const u = ((x + (sx + 0.5) * step) / size) * 64;
      const v = ((y + (sy + 0.5) * step) / size) * 64;
      // Undo the artwork scaling so the distance fields stay exact.
      const ax = (u - 32) / scale + 32;
      const ay = (v - 32) / scale + 32;

      let colour = null;
      let alpha = 0;

      const onGround = fullBleed ? true : insideRoundedRect(u, v);
      if (onGround) {
        colour = TEAL;
        alpha = 1;
        if (distanceToArch(ax, ay) <= ARCH_HALF_WIDTH) colour = LIMESTONE;
        if (Math.hypot(ax - POINT.x, ay - POINT.y) <= POINT.r) colour = AMBER;
      }

      if (colour) {
        r += colour[0] * alpha;
        g += colour[1] * alpha;
        b += colour[2] * alpha;
        a += alpha;
      }
    }
  }
  const total = SAMPLES * SAMPLES;
  if (a === 0) return [0, 0, 0, 0];
  // Un-premultiply so partially covered edge pixels keep their true hue.
  return [Math.round(r / a), Math.round(g / a), Math.round(b / a), Math.round((a / total) * 255)];
}

// --- PNG encoding ----------------------------------------------------------
let crcTable = null;
function crc32(buffer) {
  if (!crcTable) {
    crcTable = new Int32Array(256);
    for (let n = 0; n < 256; n += 1) {
      let c = n;
      for (let k = 0; k < 8; k += 1) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      crcTable[n] = c;
    }
  }
  let crc = -1;
  for (let i = 0; i < buffer.length; i += 1) crc = (crc >>> 8) ^ crcTable[(crc ^ buffer[i]) & 0xff];
  return (crc ^ -1) >>> 0;
}

function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length, 0);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body), 0);
  return Buffer.concat([length, body, crc]);
}

function encodePng(size, options) {
  // RGBA, 8 bits per channel, one filter byte (0 = none) per scanline.
  const raw = Buffer.alloc(size * (size * 4 + 1));
  let offset = 0;
  for (let y = 0; y < size; y += 1) {
    raw[offset] = 0;
    offset += 1;
    for (let x = 0; x < size; x += 1) {
      const [r, g, b, a] = samplePixel(x, y, size, options);
      raw[offset] = r;
      raw[offset + 1] = g;
      raw[offset + 2] = b;
      raw[offset + 3] = a;
      offset += 4;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // colour type: truecolour with alpha
  ihdr[10] = 0; // deflate
  ihdr[11] = 0; // adaptive filtering
  ihdr[12] = 0; // no interlace
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// --- output ----------------------------------------------------------------
mkdirSync(iconsDir, { recursive: true });

const ANY_SIZES = [32, 48, 64, 96, 128, 180, 192, 256, 384, 512];
const written = [];

for (const size of ANY_SIZES) {
  const png = encodePng(size, { scale: 1, fullBleed: false });
  writeFileSync(join(iconsDir, `icon-${size}.png`), png);
  written.push([`icons/icon-${size}.png`, png.length]);
}

// Apple wants an opaque square with no transparency around it.
{
  const png = encodePng(180, { scale: 1, fullBleed: true });
  writeFileSync(join(iconsDir, 'apple-touch-icon.png'), png);
  written.push(['icons/apple-touch-icon.png', png.length]);
}

// Maskable: full bleed, artwork inside the safe zone.
{
  const png = encodePng(512, { scale: 0.72, fullBleed: true });
  writeFileSync(join(iconsDir, 'maskable-512.png'), png);
  written.push(['icons/maskable-512.png', png.length]);
}
{
  const png = encodePng(192, { scale: 0.72, fullBleed: true });
  writeFileSync(join(iconsDir, 'maskable-192.png'), png);
  written.push(['icons/maskable-192.png', png.length]);
}

// The SVG favicon is the design file itself, copied rather than redrawn.
copyFileSync(join(logoDir, 'poravia-mark.svg'), join(webRoot, 'public', 'favicon.svg'));
written.push(['favicon.svg', 0]);
copyFileSync(join(logoDir, 'poravia-mark-mono.svg'), join(iconsDir, 'mark-mono.svg'));
written.push(['icons/mark-mono.svg', 0]);

process.stdout.write(`Wrote ${written.length} icon files:\n${written.map(([name, bytes]) => `  ${name}${bytes ? ` (${bytes} B)` : ''}`).join('\n')}\n`);
