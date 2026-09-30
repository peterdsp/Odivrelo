# Store asset requirements, verified 30 September 2026

Checked against the platform documentation on **30 September 2026**, not from
memory. Re-verify before any submission, because both platforms change these.

## Apple App Store

Source: <https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications/>

| Asset | Requirement |
|---|---|
| Screenshot count | 1 to 10 per display size |
| Format | `.png`, `.jpg` or `.jpeg`, **no alpha channel or transparency** |
| iPhone, required | 6.5-inch display, **1284 × 2778** portrait or 2778 × 1284 landscape, required if the app runs on iPhone and 6.9-inch screenshots are not supplied |
| iPhone, preferred | 6.9-inch display, **1290 × 2796** portrait (also accepted: 1260 × 2736, 1320 × 2868) |
| iPad, required | 13-inch display, **2064 × 2752** or 2048 × 2732 portrait, required if the app runs on iPad |
| Optional | 6.3-inch, 6.1-inch, 5.5-inch, 4.7-inch, 11-inch, 10.5-inch and 9.7-inch sizes; smaller sizes are scaled from the required ones if omitted |

Text limits: app name 30, subtitle 30, promotional text 170, keywords 100,
description 4000, What's New 4000.

Capture targets on this machine: `iPhone 18 Pro Max` for 6.9-inch,
`iPad Pro 13-inch (M5)` for 13-inch.

## Google Play

Source: <https://support.google.com/googleplay/android-developer/answer/9866151>

| Asset | Requirement |
|---|---|
| App icon | **512 × 512**, 32-bit PNG **with** alpha, at most 1024 KB |
| Feature graphic | **1024 × 500**, JPEG or 24-bit PNG, **no** alpha |
| Phone screenshots | at least 2; JPEG or 24-bit PNG without alpha; each side between 320 px and 3840 px, and the longer side at most twice the shorter |
| Phone, recommended | at least 4 at 1080 px or more, 9:16 portrait (1080 × 1920) or 16:9 landscape (1920 × 1080) |
| Tablet and Chromebook | 4 recommended, each side between 1080 px and 7680 px, 9:16 or 16:9 |
| Short description | 80 characters |
| Full description | 4000 characters |

Note the alpha rule differs between the two: the Play **icon** requires alpha,
the Play **feature graphic and screenshots** forbid it, and Apple forbids it
everywhere. Exporting one file for all three will be rejected somewhere.

## Rules for Poravia's own screenshots

- Screenshots must come from the **running rebranded build**. No mockups, no
  composited marketing frames pretending to be the app, no fabricated data
  beyond what the app genuinely shows.
- **While `dataMode` is `demo`, the demonstration notice must be visible in the
  screenshot.** A screenshot cropped to hide it would imply real coverage,
  which is exactly what the product must not do.
- Capture at the device's native resolution with `xcrun simctl io <udid>
  screenshot` and `adb exec-out screencap -p`, then verify the pixel dimensions
  before upload rather than assuming the simulator produced the required size.
- Keep device, runtime, scenario, build commit and date in the filename.

## Do not submit yet

**No store submission should be made while `dataMode` is `demo`.** A listing
for a Greek intercity coach app whose every departure is invented would
misrepresent the product regardless of what the screenshots say. These
materials exist so submission is a short step once EB-04 clears, not so it can
happen now.
