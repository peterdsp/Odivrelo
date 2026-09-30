# Odivrelo privacy policy

Version 1.0.0 · 30 September 2026 · Contact: `info@peterdsp.dev`

This is the canonical text. The web app, the iOS app and the Android app each
render it in Greek, English and Albanian.

## The short version

Odivrelo has no accounts, no analytics and no third-party trackers. Your
searches, saved trips and imported tickets stay on your device. Odivrelo does
not sell or issue tickets, and it never sees your booking.

## What Odivrelo stores, and where

Everything below is stored **on your device only**.

| Data | Where | Why | Deletion |
|---|---|---|---|
| Language, theme and accessibility preferences | local storage or platform preferences | to render the app the way you chose | Settings, or uninstalling |
| Recent searches | local database | so you do not retype them | Settings, individually or all |
| Favourites and saved trips | local database | so a trip is usable offline | in the app, per item |
| Downloaded offline packs | app storage | so timetables work without a connection | Settings, per pack |
| Imported ticket files | app-private encrypted storage | so you can show a ticket you already have | in the wallet, per file |
| Reminder schedules | platform notification store | so an opt-in reminder fires | switching the reminder off |

**Nothing in that table is transmitted to Odivrelo or to anyone else.** There is
no Odivrelo account and no server-side profile of you.

## The travel wallet

Ticket files you import are treated as sensitive documents.

- They are stored in app-private storage with platform file protection, and on
  Android they are excluded from device backup.
- They are **never uploaded**, never sent to a notification service, never
  written to a log, never included in diagnostics, and never placed in a shared
  or service-worker cache.
- Barcodes are never logged and never put in a URL.
- Deleting a ticket deletes the file.

On the web, browser storage can be cleared or evicted by the browser itself.
Odivrelo says so in the app rather than implying the wallet is a backup.

## Network requests Odivrelo makes

| Request | To | Contains |
|---|---|---|
| Release manifest and data packs | the Odivrelo origin | no personal data; a standard HTTP request |
| Booking handoff | the operator's own website, opened in a browser, a Custom Tab or Safari | nothing Odivrelo adds; from that point the operator's own privacy policy applies |

Odivrelo adds no identifier, no advertising id and no fingerprint to any request.

**There is no map tile provider in the 1.0.0 Web release.** The map draws route
geometry and stop markers on a plain styled background and loads no basemap, so
the Web app makes **no third-party request at all**. That is asserted by an
automated test, not just stated here. It is a consequence of not holding a tile
licence, and it is why the map is deliberately schematic rather than a street
map.

The native apps render with the platform's own map component, MapKit on iOS and
the reviewed native map SDK on Android. Those are operating-system services
governed by Apple's and Google's own privacy terms, not a Odivrelo request, and
they receive the map viewport as any map necessarily does.

## Permissions

Every permission is optional and requested only in the moment it is used.

| Permission | Used for | If you refuse |
|---|---|---|
| Location | centring the map and sorting nearby stops | everything still works; you search by name |
| Notifications | opt-in travel reminders you set yourself | reminders are simply unavailable, and the app says so |
| File access | importing a ticket you choose | the wallet stays empty |

Odivrelo never requests broad storage access, contacts, calendar, camera,
microphone or background location.

## Notification content

Reminder payloads contain the journey time and the boarding point only. They
**never** contain a passenger name, a booking reference, a ticket image or a
barcode.

## Children

Odivrelo is not directed at children and collects nothing from anyone.

## Third parties

Odivrelo embeds no analytics SDK, no advertising SDK, no crash-reporting SDK and
no social SDK. The complete list of third-party code is the open-source
dependency list shipped in Settings under Licences.

## Data about coach services

Odivrelo publishes public transport information with its source, retrieval time,
rights state and review state attached. That is information about services, not
about you. Corrections and takedown requests go to `info@peterdsp.dev`.

## Changes

Material changes will be published here and in the release notes, with a date.
