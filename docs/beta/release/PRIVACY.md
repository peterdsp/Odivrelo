# Poravia privacy policy

Version 1.0.0 · 30 September 2026 · Contact: `info@peterdsp.dev`

This is the canonical text. The web app, the iOS app and the Android app each
render it in Greek, English and Albanian.

## The short version

Poravia has no accounts, no analytics and no third-party trackers. Your
searches, saved trips and imported tickets stay on your device. Poravia does
not sell or issue tickets, and it never sees your booking.

## What Poravia stores, and where

Everything below is stored **on your device only**.

| Data | Where | Why | Deletion |
|---|---|---|---|
| Language, theme and accessibility preferences | local storage or platform preferences | to render the app the way you chose | Settings, or uninstalling |
| Recent searches | local database | so you do not retype them | Settings, individually or all |
| Favourites and saved trips | local database | so a trip is usable offline | in the app, per item |
| Downloaded offline packs | app storage | so timetables work without a connection | Settings, per pack |
| Imported ticket files | app-private encrypted storage | so you can show a ticket you already have | in the wallet, per file |
| Reminder schedules | platform notification store | so an opt-in reminder fires | switching the reminder off |

**Nothing in that table is transmitted to Poravia or to anyone else.** There is
no Poravia account and no server-side profile of you.

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
Poravia says so in the app rather than implying the wallet is a backup.

## Network requests Poravia makes

| Request | To | Contains |
|---|---|---|
| Release manifest and data packs | the Poravia origin | no personal data; a standard HTTP request |
| Map tiles, when you open a map | the configured map tile provider | your approximate map viewport, as any map requires |
| Booking handoff | the operator's own website, opened in a browser or Custom Tab | nothing Poravia adds; from that point the operator's own privacy policy applies |

Poravia adds no identifier, no advertising id and no fingerprint to any request.

## Permissions

Every permission is optional and requested only in the moment it is used.

| Permission | Used for | If you refuse |
|---|---|---|
| Location | centring the map and sorting nearby stops | everything still works; you search by name |
| Notifications | opt-in travel reminders you set yourself | reminders are simply unavailable, and the app says so |
| File access | importing a ticket you choose | the wallet stays empty |

Poravia never requests broad storage access, contacts, calendar, camera,
microphone or background location.

## Notification content

Reminder payloads contain the journey time and the boarding point only. They
**never** contain a passenger name, a booking reference, a ticket image or a
barcode.

## Children

Poravia is not directed at children and collects nothing from anyone.

## Third parties

Poravia embeds no analytics SDK, no advertising SDK, no crash-reporting SDK and
no social SDK. The complete list of third-party code is the open-source
dependency list shipped in Settings under Licences.

## Data about coach services

Poravia publishes public transport information with its source, retrieval time,
rights state and review state attached. That is information about services, not
about you. Corrections and takedown requests go to `info@peterdsp.dev`.

## Changes

Material changes will be published here and in the release notes, with a date.
