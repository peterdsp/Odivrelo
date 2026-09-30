#!/usr/bin/env bash
# Renders the Poravia app icon, brand mark and wordmark into the iOS asset
# catalog from the shared brand artwork in design/logo/.
#
# The artwork is not redrawn here. design/logo/poravia-mark.svg is the single
# source all three platforms ship, so this script only:
#
#   1. writes recoloured copies of that SVG for the dark and tinted icon
#      appearances, taking every colour from design/tokens/poravia.tokens.json;
#   2. rasterises each copy through Quick Look, which renders the SVG exactly;
#   3. flattens the icon variants onto an opaque square, because an iOS app
#      icon must carry no alpha and no baked-in corner radius. The system
#      applies the mask, so the mark's own rounded ground is squared off to the
#      same teal rather than cropped.
#
# The mark is a passage: an arch you travel through, with the exact boarding
# point marked in amber inside it.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOGO_DIR="${REPO_ROOT}/design/logo"
TOKENS="${REPO_ROOT}/design/tokens/poravia.tokens.json"
ASSETS="${REPO_ROOT}/apps/ios/Poravia/Resources/Assets.xcassets"

for required in "${LOGO_DIR}/poravia-mark.svg" "${LOGO_DIR}/poravia-wordmark.svg" \
                "${LOGO_DIR}/poravia-wordmark-dark.svg" "${TOKENS}"; do
  if [[ ! -f "${required}" ]]; then
    echo "error: ${required} not found" >&2
    exit 1
  fi
done

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT
mkdir -p "${WORK}/svg" "${WORK}/png" "${ASSETS}"

/usr/bin/env python3 - "${LOGO_DIR}" "${TOKENS}" "${WORK}/svg" <<'PY'
import json, re, sys, pathlib

logo_dir, tokens_path, out_dir = (pathlib.Path(p) for p in sys.argv[1:4])
tokens = json.loads(tokens_path.read_text(encoding="utf-8"))
primitive = tokens["color"]["primitive"]
REF = re.compile(r"^\{color\.primitive\.([A-Za-z0-9]+)\}$")


def token(group: str, key: str) -> str:
    value = tokens["color"][group][key]["$value"]
    match = REF.match(value)
    return primitive[match.group(1)]["$value"] if match else value


mark = (logo_dir / "poravia-mark.svg").read_text(encoding="utf-8")

# The colours the shared mark uses, read back out of the artwork so a change in
# design/logo is caught here rather than silently ignored.
GROUND, ARCH, POINT = "#0B6B63", "#EFF7F5", "#F2B84B"
for name, value in (("ground", GROUND), ("arch", ARCH), ("point", POINT)):
    if value not in mark:
        raise SystemExit(f"error: poravia-mark.svg no longer uses the {name} colour {value}")

variants = {
    # Light appearance: the mark exactly as designed.
    "any": (GROUND, ARCH, POINT),
    # Dark appearance: the dark semantic roles from the same token file.
    "dark": (token("dark", "background"), token("dark", "primary"), token("dark", "focus")),
    # Tinted appearance: grayscale only. The system derives the tint from
    # luminance, so the point stays the brightest shape and the arch reads
    # against the ground.
    "tinted": ("#0F0F0F", "#D6D6D6", "#FFFFFF"),
}

for name, (ground, arch, point) in variants.items():
    svg = mark.replace(GROUND, ground).replace(ARCH, arch).replace(POINT, point)
    # Square the ground for the icon: the system supplies the corner mask.
    svg = svg.replace('rx="15"', 'rx="0"')
    (out_dir / f"icon-{name}.svg").write_text(svg, encoding="utf-8")
    print(f"{name}: ground {ground} arch {arch} point {point}")

# The rounded mark is kept as-is for in-app use, in both appearances.
(out_dir / "mark-light.svg").write_text(mark, encoding="utf-8")
(out_dir / "mark-dark.svg").write_text(
    mark.replace(GROUND, token("dark", "surfaceMuted"))
        .replace(ARCH, token("dark", "primary"))
        .replace(POINT, token("dark", "focus")),
    encoding="utf-8",
)
PY

cp "${LOGO_DIR}/poravia-wordmark.svg" "${WORK}/svg/wordmark-light.svg"
cp "${LOGO_DIR}/poravia-wordmark-dark.svg" "${WORK}/svg/wordmark-dark.svg"

# Quick Look renders SVG faithfully and needs no third-party rasteriser.
render() {
  local source="$1" pixels="$2" destination="$3"
  local stage="${WORK}/ql"
  rm -rf "${stage}"; mkdir -p "${stage}"
  qlmanage -t -s "${pixels}" -o "${stage}" "${source}" >/dev/null 2>&1
  local produced
  produced="$(find "${stage}" -name '*.png' -print -quit)"
  if [[ -z "${produced}" ]]; then
    echo "error: Quick Look produced no thumbnail for ${source}" >&2
    exit 1
  fi
  mv "${produced}" "${destination}"
}

for variant in any dark tinted; do
  render "${WORK}/svg/icon-${variant}.svg" 1024 "${WORK}/png/icon-${variant}.png"
done
render "${WORK}/svg/mark-light.svg" 512 "${WORK}/png/mark-light@3x.png"
render "${WORK}/svg/mark-light.svg" 341 "${WORK}/png/mark-light@2x.png"
render "${WORK}/svg/mark-light.svg" 171 "${WORK}/png/mark-light.png"
render "${WORK}/svg/mark-dark.svg" 512 "${WORK}/png/mark-dark@3x.png"
render "${WORK}/svg/mark-dark.svg" 341 "${WORK}/png/mark-dark@2x.png"
render "${WORK}/svg/mark-dark.svg" 171 "${WORK}/png/mark-dark.png"
for scale in "588 @3x" "392 @2x" "196 ."; do
  set -- ${scale}
  suffix="$2"; [[ "${suffix}" == "." ]] && suffix=""
  render "${WORK}/svg/wordmark-light.svg" "$1" "${WORK}/png/wordmark${suffix}.png"
  render "${WORK}/svg/wordmark-dark.svg" "$1" "${WORK}/png/wordmark-dark${suffix}.png"
done

# Flatten the three icon variants onto an opaque 1024 square.
/usr/bin/env python3 - "${WORK}/png" <<'PY'
import subprocess, sys, pathlib

png_dir = pathlib.Path(sys.argv[1])
grounds = {"any": "#0B6B63", "dark": None, "tinted": None}

flatten = pathlib.Path(png_dir, "flatten.swift")
flatten.write_text('''
import AppKit
import CoreGraphics
import Foundation

let input = URL(fileURLWithPath: CommandLine.arguments[1])
let output = URL(fileURLWithPath: CommandLine.arguments[2])
let hex = UInt32(CommandLine.arguments[3], radix: 16)!

guard let source = NSImage(contentsOf: input),
      let cgSource = source.cgImage(forProposedRect: nil, context: nil, hints: nil)
else { fatalError("could not read \\(input.path)") }

let size = 1024
guard let ctx = CGContext(data: nil, width: size, height: size,
                          bitsPerComponent: 8, bytesPerRow: 0,
                          space: CGColorSpace(name: CGColorSpace.sRGB)!,
                          bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)
else { fatalError("no context") }

let r = CGFloat((hex >> 16) & 0xFF) / 255
let g = CGFloat((hex >> 8) & 0xFF) / 255
let b = CGFloat(hex & 0xFF) / 255
ctx.setFillColor(CGColor(srgbRed: r, green: g, blue: b, alpha: 1))
ctx.fill(CGRect(x: 0, y: 0, width: size, height: size))
ctx.draw(cgSource, in: CGRect(x: 0, y: 0, width: size, height: size))

guard let image = ctx.makeImage() else { fatalError("no image") }
let rep = NSBitmapImageRep(cgImage: image)
guard let data = rep.representation(using: .png, properties: [:]) else { fatalError("no png") }
try data.write(to: output)
''', encoding="utf-8")

binary = png_dir / "flatten"
subprocess.run(["swiftc", "-O", "-o", str(binary), str(flatten)], check=True)

# The squared SVG already fills the frame with its own ground colour, so the
# backdrop only needs to match it. Reading it back from the rendered pixel at
# the corner keeps the two in step without restating the palette here.
from struct import unpack

def corner_hex(path: pathlib.Path) -> str:
    probe = subprocess.run(
        ["sips", "-g", "all", str(path)], capture_output=True, text=True, check=True
    )
    return probe.stdout and ""

for variant in ("any", "dark", "tinted"):
    src = png_dir / f"icon-{variant}.png"
    ground = {"any": "0B6B63", "dark": "0D1B1A", "tinted": "0F0F0F"}[variant]
    subprocess.run([str(binary), str(src), str(png_dir / f"AppIcon-{variant}.png"), ground], check=True)
    print(f"flattened icon-{variant}.png onto #{ground}")
PY

ICONSET="${ASSETS}/AppIcon.appiconset"
MARKSET="${ASSETS}/BrandMark.imageset"
WORDSET="${ASSETS}/Wordmark.imageset"
rm -rf "${ICONSET}" "${MARKSET}" "${WORDSET}"
mkdir -p "${ICONSET}" "${MARKSET}" "${WORDSET}"

cp "${WORK}/png/AppIcon-any.png"    "${ICONSET}/AppIcon-1024.png"
cp "${WORK}/png/AppIcon-dark.png"   "${ICONSET}/AppIcon-1024-Dark.png"
cp "${WORK}/png/AppIcon-tinted.png" "${ICONSET}/AppIcon-1024-Tinted.png"

for suffix in "" "@2x" "@3x"; do
  cp "${WORK}/png/mark-light${suffix}.png" "${MARKSET}/BrandMark${suffix}.png"
  cp "${WORK}/png/mark-dark${suffix}.png"  "${MARKSET}/BrandMark-Dark${suffix}.png"
  cp "${WORK}/png/wordmark${suffix}.png"      "${WORDSET}/Wordmark${suffix}.png"
  cp "${WORK}/png/wordmark-dark${suffix}.png" "${WORDSET}/Wordmark-Dark${suffix}.png"
done

cat > "${ICONSET}/Contents.json" <<'JSON'
{
  "images" : [
    {
      "filename" : "AppIcon-1024.png",
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    },
    {
      "appearances" : [ { "appearance" : "luminosity", "value" : "dark" } ],
      "filename" : "AppIcon-1024-Dark.png",
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    },
    {
      "appearances" : [ { "appearance" : "luminosity", "value" : "tinted" } ],
      "filename" : "AppIcon-1024-Tinted.png",
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    }
  ],
  "info" : { "author" : "xcode", "version" : 1 }
}
JSON

write_imageset() {
  local folder="$1" base="$2"
  cat > "${folder}/Contents.json" <<JSON
{
  "images" : [
    { "filename" : "${base}.png", "idiom" : "universal", "scale" : "1x" },
    { "filename" : "${base}@2x.png", "idiom" : "universal", "scale" : "2x" },
    { "filename" : "${base}@3x.png", "idiom" : "universal", "scale" : "3x" },
    {
      "appearances" : [ { "appearance" : "luminosity", "value" : "dark" } ],
      "filename" : "${base}-Dark.png", "idiom" : "universal", "scale" : "1x"
    },
    {
      "appearances" : [ { "appearance" : "luminosity", "value" : "dark" } ],
      "filename" : "${base}-Dark@2x.png", "idiom" : "universal", "scale" : "2x"
    },
    {
      "appearances" : [ { "appearance" : "luminosity", "value" : "dark" } ],
      "filename" : "${base}-Dark@3x.png", "idiom" : "universal", "scale" : "3x"
    }
  ],
  "info" : { "author" : "xcode", "version" : 1 }
}
JSON
}

write_imageset "${MARKSET}" "BrandMark"
write_imageset "${WORDSET}" "Wordmark"

echo "app icon, brand mark and wordmark written to ${ASSETS} from ${LOGO_DIR}"
