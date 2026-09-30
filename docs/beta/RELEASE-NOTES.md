# Poravia 1.0.0 beta, release notes

Date: 30 September 2026 · Version `1.0.0` · Data release: see the artifact
manifest · Formerly released under the name HodoMap, which is retired.

---

## Read this first

**Every journey in this build is invented.** Poravia 1.0.0 ships a
demonstration dataset covering a fictional region called **Aloria**, which does
not exist. No departure, terminal, operator or fare shown is real. Do not use
this build to plan a trip.

That is deliberate. Poravia's whole premise is that a missing journey is better
than an invented one, so the moment the real data failed its own quality gate,
the honest move was to ship working software over data that announces what it
is. The evidence behind that decision is published in
[`docs/phase0/NAP-DATASET-EVIDENCE.md`](../phase0/NAP-DATASET-EVIDENCE.md).

---

## What Poravia does

Greece's intercity coach network is dozens of independent KTEL operators, each
with its own site, its own timetable format and its own terminal. Finding *a*
coach is easy. Being certain of *the right one* is not.

Poravia answers four questions and shows its working for each:

1. Does this journey run on the date I am travelling?
2. Where exactly do I board? Not the city. The terminal, and the bay.
3. How do I buy a ticket officially, or where is the verified ticket office?
4. How stale is this, and who said it?

**Poravia never sells or issues tickets.** It hands you to the operator.

## In this release

- **National search** across operators, with place disambiguation that
  distinguishes a terminal from a boarding point within it.
- **Service dates in `Europe/Athens`**, including journeys that cross midnight
  and both daylight-saving transitions, handled correctly and identically on
  every platform.
- **Journey detail** with the full ordered stop list, pickup and drop-off
  rules, the exact reviewed boarding point with its bay and step-free state,
  restrictions, and the complete source lineage behind every fact.
- **Freshness and confidence on screen.** Every result says when it was last
  checked and how old that is.
- **Official booking handoff** that returns you to exactly where you were, with
  a verified ticket-office, phone and opening-hours fallback where an operator
  does not sell online.
- **Offline packs** with integrity verification, atomic installation, recovery
  from an interrupted download, update, rollback and deletion. The app shows
  which data is genuinely available offline rather than implying more.
- **Saved trips and Trip Ready**, usable offline, showing which cached release
  they came from and how old it is.
- **A private travel wallet** for ticket files you import yourself. Stored only
  on your device, never uploaded, never logged, never put in a shared cache.
- **Opt-in reminders** that never carry a passenger name, booking reference,
  ticket image or barcode.
- **Greek, English and Albanian**, complete, with Greek as the default.
- **Accessibility as a release gate**: screen reader, focus order, large text,
  keyboard, contrast, reduced motion, and a complete non-map route to every
  piece of essential information.
- **Adaptive layouts** built from the first screen, not bolted on: iPhone Duo,
  Android foldables, tablets, split-screen and freely resized windows, with
  your query, date, filters, selected journey and scroll position preserved
  across every geometry change.
- **No account, no analytics, no trackers.**

## Not in this release, and not pretended

- **Real Greek coach data.** See above. All 62 federation operator directory
  sources have undocumented reuse rights, and the one rights-cleared national
  source is nearly six years stale and contains no boarding points at all.
- **Live coach tracking and live ETA.** There is no authorised feed. Where live
  tracking would be, Poravia shows the schedule and says live tracking is
  unavailable. It does not animate a coach that is not there.
- **Predicted or estimated times.** No defensible prediction input exists yet.
- **National coverage.** This is a limited-coverage demonstration beta.
- **Offline map tiles.** Offline packs carry timetable, stop and operator data.
  The map needs a connection, and the app says so.
- **Native desktop, watch, TV, automotive or spatial apps.** Desktop is covered
  by the web app and its installable PWA.
- **Validation by real travellers.** The five-traveller test has not been run.

## Known issues and limitations

| Area | Limitation |
|---|---|
| Data | Demonstration only. Aloria does not exist. |
| Web | Served as static hosting, which cannot set HTTP security headers. The content security policy is applied by meta element instead, which is weaker. Stated in `apps/web/README.md`. |
| Web | Browser storage can be evicted. The travel wallet is not a backup, and the app says so rather than implying durability. |
| Web | Scheduled background reminders are not deliverable in every browser. Poravia says so instead of claiming they work. |
| iOS | Not signed, so not installable outside a simulator. See EB-02. |
| Android | Not signed for release, so not installable from a store. A debug QA build is provided. See EB-03. |
| iPhone Duo | Runtime coverage is reported exactly as far as the installed runtime allowed. Synthetic geometry checks are supplementary evidence, not proof of physical-device support. |
| Name | Preliminary screening only. No trademark register could be searched. See EB-06. |
| Maps | Route geometry is ordered-stops-only in this release and is labelled as such, never as an exact road alignment. |

## Installing

Web is the primary target: open the site, and install it from your browser if
you want it on your home screen or dock.

iOS and Android beta installation, and the signing and upload steps that are
currently blocked, are documented in
[`BUILD-AND-RELEASE.md`](BUILD-AND-RELEASE.md).

## Corrections and takedown

Every public entity carries a correction path. Rights holders can request
review or takedown at `info@peterdsp.dev`.

## Independence

Poravia is an independent project. It is not affiliated with, endorsed by or
operated by the KTEL federation, any regional KTEL operator, TicketWeb or their
technology providers. Operator names and trademarks remain the property of
their respective owners.
