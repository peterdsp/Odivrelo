"""Frames the iOS App Store screenshots into marketing images.

Reads apps/ios/AppStore/screenshots/<lang>/<device>/*.png (from
scripts/ios-app-store-screenshots.sh) and writes
apps/ios/AppStore/marketing/<lang>/<device>/ at the exact App Store sizes:
iPhone 6.9" 1320x2868 and iPad 13" 2064x2752.

The frame is the departures board the product is built as: the night field,
board amber on the kicker, white type for the one headline. The screenshot
itself is untouched apart from rounded corners, so it still shows the running
app and its real data.

    uv run --with pillow python3 scripts/ios-marketing-shots.py
"""
import pathlib

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
STORE = ROOT / "apps" / "ios" / "AppStore"
SRC = STORE / "screenshots"
OUT = STORE / "marketing"
SANS = "/System/Library/Fonts/SFNS.ttf"
MONO = "/System/Library/Fonts/SFNSMono.ttf"

SIZES = {"iphone": (1320, 2868), "ipad": (2064, 2752)}

# design/tokens/odivrelo.tokens.json
NIGHT, NAVY, DEEP = (5, 7, 10), (15, 20, 36), (31, 43, 77)
AMBER = (247, 148, 29)
WHITE, MUTED = (244, 244, 242), (142, 151, 178)

# One headline per screen, keyed by the screenshot's file stem. The kicker is
# set like a column heading on a departures board.
CAPTIONS = {
    "en": {
        "1-results": ("DEPARTURES", "Every coach,\none search"),
        "2-journey": ("CHECKED", "See when each time\nwas last checked"),
        "3-route": ("ROUTE", "Know the stop\nyou board at"),
        "4-search": ("SEARCH", "Greece by coach,\nwith certainty"),
    },
    "el": {
        "1-results": ("ΑΝΑΧΩΡΗΣΕΙΣ", "Κάθε λεωφορείο,\nμία αναζήτηση"),
        "2-journey": ("ΕΛΕΓΧΟΣ", "Δες πότε ελέγχθηκε\nκάθε δρομολόγιο"),
        "3-route": ("ΔΙΑΔΡΟΜΗ", "Μάθε τη στάση\nόπου ανεβαίνεις"),
        "4-search": ("ΑΝΑΖΗΤΗΣΗ", "Η Ελλάδα με\nλεωφορείο, σίγουρα"),
    },
}


def font(path, size, weight=None):
    f = ImageFont.truetype(path, size)
    if weight:
        try:
            f.set_variation_by_name(weight)
        except Exception:
            pass
    return f


def background(w, h):
    # Night at the top, navy at the foot: a 1 px column, stretched.
    column = Image.new("RGB", (1, 256))
    for y in range(256):
        t = y / 255
        column.putpixel((0, y), tuple(round(NIGHT[i] + (NAVY[i] - NIGHT[i]) * t) for i in range(3)))
    img = column.resize((w, h), Image.BILINEAR)
    # One soft deep-blue glow behind the screenshot, so the card lifts off the field.
    glow = Image.new("RGB", (w, h), (0, 0, 0))
    ImageDraw.Draw(glow).ellipse((-w * 0.2, h * 0.3, w * 1.2, h * 1.1), fill=DEEP)
    small = glow.resize((w // 8, h // 8)).filter(ImageFilter.GaussianBlur(w * 0.2 / 8)).resize((w, h), Image.BILINEAR)
    img = ImageChops.add(img, small.point(lambda v: int(v * 0.6)))
    # Faint board rules across the head of the frame, the way a departures
    # board is ruled into rows.
    d = ImageDraw.Draw(img)
    step = round(w * 0.045)
    for y in range(0, round(h * 0.3), step):
        d.line((0, y, w, y), fill=(14, 18, 28), width=max(1, w // 900))
    return img


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, img.width - 1, img.height - 1), radius, fill=255)
    out = Image.new("RGBA", img.size)
    out.paste(img, (0, 0), mask)
    return out, mask


def compose(shot_path, device, kicker, title):
    w, h = SIZES[device]
    canvas = background(w, h).convert("RGBA")
    d = ImageDraw.Draw(canvas)
    phone = device == "iphone"

    # Greek runs longer than English, so a headline shrinks until its widest
    # line keeps a clear margin on both sides.
    size = round(w * (0.083 if phone else 0.058))
    while True:
        title_font = font(SANS, size, "Heavy")
        if max(d.textlength(line, font=title_font) for line in title.split("\n")) <= w * 0.84:
            break
        size -= 2
    kicker_font = font(MONO, round(w * (0.030 if phone else 0.021)), "Semibold")

    # Kicker: amber, letter-spaced, with a short amber bar before it.
    y = round(h * (0.055 if phone else 0.05))
    spaced = " ".join(kicker)
    kw = d.textlength(spaced, font=kicker_font)
    bar = round(w * 0.05)
    gap = round(w * 0.02)
    x = (w - (bar + gap + kw)) / 2
    bar_y = y + kicker_font.size * 0.55
    d.rounded_rectangle((x, bar_y - w * 0.004, x + bar, bar_y + w * 0.004), radius=3, fill=AMBER)
    d.text((x + bar + gap, y), spaced, font=kicker_font, fill=AMBER)
    y += round(kicker_font.size * 2.0)

    for line in title.split("\n"):
        tw = d.textlength(line, font=title_font)
        d.text(((w - tw) / 2, y), line, font=title_font, fill=WHITE)
        y += round(title_font.size * 1.13)
    y += round(title_font.size * 0.55)

    shot = Image.open(shot_path).convert("RGB")
    pad = round(w * (0.09 if phone else 0.08))
    scale = min((w - 2 * pad) / shot.width, (h - y - round(h * 0.035)) / shot.height)
    if not phone:
        # The iPad card runs off the foot of the frame, like Apple's own pages.
        scale = (w - 2 * pad) / shot.width
    shot = shot.resize((round(shot.width * scale), round(shot.height * scale)), Image.LANCZOS)
    radius = round(shot.width * (0.085 if phone else 0.03))
    card, mask = rounded(shot, radius)

    x = (w - card.width) // 2
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    shadow_mask = Image.new("L", canvas.size, 0)
    shadow_mask.paste(mask, (x, y + round(w * 0.02)))
    blurred = shadow_mask.resize((w // 4, h // 4)).filter(ImageFilter.GaussianBlur(w * 0.03 / 4)).resize((w, h), Image.BILINEAR)
    shadow.putalpha(blurred.point(lambda a: int(a * 0.8)))
    canvas = Image.alpha_composite(canvas, shadow)
    edge = Image.new("RGBA", card.size, (0, 0, 0, 0))
    ImageDraw.Draw(edge).rounded_rectangle((0, 0, card.width - 1, card.height - 1), radius,
                                           outline=(255, 255, 255, 60), width=max(2, w // 450))
    canvas.alpha_composite(card, (x, y))
    canvas.alpha_composite(edge, (x, y))
    # App Store screenshots must have no alpha channel.
    return canvas.convert("RGB")


def main():
    for lang, captions in CAPTIONS.items():
        for device in SIZES:
            src_dir, out_dir = SRC / lang / device, OUT / lang / device
            shots = sorted(src_dir.glob("*.png"))
            if not shots:
                continue
            out_dir.mkdir(parents=True, exist_ok=True)
            for old in out_dir.glob("*.png"):
                old.unlink()
            for shot in shots:
                kicker, title = captions[shot.stem]
                image = compose(shot, device, kicker, title)
                assert image.size == SIZES[device], image.size
                image.save(out_dir / shot.name, optimize=True)
                print(out_dir.relative_to(ROOT) / shot.name)


if __name__ == "__main__":
    main()
