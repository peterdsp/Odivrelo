# Poravia Design System

Status: proposed design language, version 0.1

## Design idea

Poravia should feel like a calm, trustworthy journey companion, not a ticketing
portal and not a government form.

The visual language combines:

- Aegean teal for movement and orientation.
- Warm limestone backgrounds inspired by Greek stations and road signs.
- Sun amber for focus and important travel moments.
- Generous white space and clear route structure.
- Visible source and freshness states.

The product should never borrow operator logos or federation visual language in
a way that suggests official affiliation.

## Brand mark

The proposed mark is a rounded map pin containing a folded road that forms a
subtle `H`.

Rules:

- The mark must work at 24 pixels.
- Use one color at small sizes.
- Do not put operator logos inside the mark.
- Do not use a bus silhouette as the only identifying idea.
- Keep a square app-icon version and a horizontal wordmark version.
- Render the product as `Poravia`, with a capital H and M.

## Color

### Core palette

| Token | Value | Purpose |
|---|---|---|
| Aegean Ink | `#142E2C` | Primary text and dark brand field |
| Route Teal | `#0B6B63` | Primary actions and selected routes |
| Deep Route | `#064B47` | Pressed actions and dark emphasis |
| Sea Glass | `#DCEBE7` | Selected and supporting surfaces |
| Limestone | `#F6F7F2` | App background |
| White | `#FFFFFF` | Cards and elevated surfaces |
| Sun Amber | `#F2B84B` | Focus and journey highlights |
| Stone | `#4E615D` | Secondary text |
| Border | `#CBD8D4` | Dividers and control outlines |

### Semantic palette

| State | Surface | Text |
|---|---|---|
| Verified or success | `#E3F3E9` | `#185C38` |
| Information | `#E5F0FA` | `#174F83` |
| Warning or stale | `#FFF1C9` | `#4A2E00` |
| Error or cancelled | `#FCE8E5` | `#8F2F25` |

Important states always include text and an icon. Color is never the only
signal.

### Dark mode

Dark mode uses Aegean black rather than pure black:

- Background: `#0D1B1A`
- Surface: `#142522`
- Raised surface: `#1C302D`
- Primary text: `#F4F8F6`
- Secondary text: `#B8C9C5`
- Primary action: `#52C8BC`
- Action text: `#08201E`

## Typography

Use platform-native typography with matching metrics:

- Web: Inter with a system fallback.
- iOS: SF Pro.
- Android: Roboto.

All chosen families must support Greek, English and Albanian.

Scale:

| Style | Size | Weight | Use |
|---|---:|---:|---|
| Display | 40 | 720 | Marketing and large destination headers |
| Title | 28 | 700 | Screen titles |
| Heading | 20 | 680 | Cards and sections |
| Body | 16 | 450 | Primary reading |
| Label | 14 | 650 | Controls and route metadata |
| Caption | 12 | 520 | Freshness and source details |

Support Dynamic Type and browser zoom. Do not truncate place names merely to
preserve a decorative layout.

## Layout

- Base spacing unit: 4 points.
- Main screen side padding: 20 points on phones.
- Card internal padding: 16 points.
- Minimum touch target: 44 by 44 points.
- Standard control height: 52 points.
- Web content maximum: 1200 pixels.
- Search results use one readable column until wide desktop layouts can show a
  synchronized map.

## Shape and elevation

- Small radius: 8 points.
- Control radius: 12 points.
- Card radius: 18 points.
- Search and map panel radius: 24 points.
- Pills are reserved for short filters and status labels.
- Prefer borders and tonal surfaces over heavy shadows.
- Use a floating shadow only for sheets, search panels and map controls.

## Iconography

Use rounded, two-dimensional system icons.

Core metaphors:

- Pin for place.
- Road line for route.
- Clock for scheduled time.
- Calendar for service date.
- Shield check for verified information.
- Refresh clock for freshness.
- External arrow for official booking.
- Download for offline pack.

Icons require accessible labels. Avoid flag icons for language selection.

## Map language

The map is useful evidence, not decoration.

- Default map is quiet and low-saturation.
- Coach routes use Route Teal unless multiple routes need differentiation.
- Selected route is 6 pixels wide with a contrasting casing.
- Unselected routes are 3 pixels wide.
- Stops are white circles with a teal border.
- Terminals use a larger filled marker.
- Unverified geometry uses a dotted line and an explicit label.
- Ferry segments use a blue dashed line.
- User location is visually distinct from stops.
- Map attribution stays visible.

Never draw a straight line and label it an exact coach route.

## Components

### Journey search card

- Two large place fields connected by a route line.
- Swap control between origin and destination.
- Service date is explicit.
- Main action says `Search journeys`, not `Go`.
- Recent and nearby places appear below the fields.

### Journey result card

Information order:

1. Departure and arrival time.
2. Origin and destination.
3. Operator and route.
4. Duration and transfers.
5. Source freshness and service state.
6. Price only when source-backed.

The official booking action appears after journey details and names the
operator.

### Official purchase and contact card

When a verified official online store exists:

- Primary action says `Buy from KTEL <operator>`.
- An external-link icon and official operator domain are visible.
- The action opens the device's external default browser with one tap.
- Journey details are prefilled only through a tested official deep link.
- Supporting text explains that the operator manages payment, tickets, changes,
  and refunds.

When electronic ticketing is unavailable:

- Replace the purchase action with `How to buy this ticket`.
- Show the relevant origin ticket office first.
- Provide verified call, email, address, opening hours, directions, website,
  and copy-details actions when available.
- Show a verification date and source status.
- Omit unpublished fields instead of guessing.

When availability has not been investigated, say
`Electronic ticketing unknown`, not `Electronic tickets unavailable`.

### Map journey bottom sheet

Tapping a coach, predicted coach, or journey opens a bottom sheet containing:

- Journey status, next stop, ETA, and final destination.
- `Save trip`.
- `Buy from KTEL <operator>` or `How to buy`.
- `View saved ticket` after explicit ticket import.
- `Contact KTEL`.
- `Journey details`.

The bottom sheet stays inside Poravia. Official-store checkout opens outside
Poravia in the device browser.

### Saved trip card

Shows journey date, origin, destination, operator, departure, current status,
ticket status, notification state, and offline-data freshness.

Primary action changes with state:

- `Buy ticket`
- `Add ticket`
- `View ticket`
- `View live journey`
- `View completed trip`

### Saved ticket card

- Shows the original imported ticket or Wallet status.
- Clearly distinguishes a boarding ticket from a booking confirmation.
- Provides `Open original`, `Share`, `Export`, and `Delete`.
- Supports optional biometric protection.
- Never displays a ticket preview in the app switcher.

### Operator badge

Operator badges are neutral text components. Do not reproduce an operator logo
without permission.

### Data confidence badge

Required labels:

- `Verified schedule`
- `Operator source`
- `Booking observed`
- `Changed, review pending`
- `Stale`
- `Limited coverage`

The badge opens a source sheet explaining retrieval time, effective period and
coverage.

### Route timeline

- Vertical on phones.
- Shows every reviewed intermediate stop.
- Clearly separates arrival and departure.
- Marks request stops and ferry segments.
- Keeps times aligned for scanning.

### Offline pack card

Shows geography or operator, release date, size, freshness and update action.

## Fifteen core screens

1. Coach home and journey search.
2. Place and stop picker.
3. Journey results.
4. Journey detail.
5. Route map.
6. My trips.
7. Saved trip detail and notification settings.
8. Saved ticket import and protected viewer.
9. Operator directory.
10. Operator detail and official contacts.
11. Station and stop detail.
12. Offline pack management.
13. Coverage and source transparency.
14. Service alerts and seasonal changes.
15. Settings, language, accessibility and independence statement.

## Screen direction

### Home

Use an Aegean Ink header with the Poravia mark, a plain-language promise and a
white journey search panel overlapping the lower edge. Below it, show recent
journeys, nearby terminals and national coverage status.

### Results

Keep the service date and route summary pinned. Results use white cards on
Limestone. A Web desktop layout can pair the list with a map, but the list
remains complete without the map.

### Journey detail

Lead with times and terminals. Follow with route timeline, map, source status,
service notes and the official booking handoff. If electronic booking is not
available, the handoff becomes a complete KTEL contact and physical
ticket-office card.

## Motion

- Fast feedback: 120 milliseconds.
- Standard transitions: 220 milliseconds.
- Large panel transitions: 360 milliseconds.
- Use a calm ease-out curve.
- Never animate map position without user context.
- Respect reduced-motion settings by removing nonessential movement.

## Voice and writing

Poravia is direct, calm and honest.

Use:

- `Schedule verified 2 days ago`
- `Limited timetable coverage`
- `Continue to KTEL Kavala`
- `This route is not available offline`

Avoid:

- `Guaranteed`
- `Official Poravia ticket`
- `Live` when the value is scheduled or cached
- Technical provider names in primary passenger copy

## Accessibility gates

- WCAG AA contrast for text and controls.
- 44-point minimum targets.
- Logical reading and focus order.
- Screen-reader summaries for journey cards.
- Text alternatives for map-only information.
- Full keyboard operation on Web.
- Dynamic Type and 200 percent browser zoom.
- Status conveyed through label, icon and color.
- Greek and Albanian strings tested with realistic lengths.

## Implementation

The canonical tokens live in
`design/tokens/poravia.tokens.json`.

The Web variables live in
`design/tokens/poravia.css`.

Platform themes should be generated from the semantic token layer. Features
must not import primitive color values directly.
