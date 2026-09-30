# Odivrelo support

Version 1.0.0 beta · Contact: `info@peterdsp.dev`

## Before anything else: this beta shows invented data

Every journey, terminal, operator and fare in Odivrelo 1.0.0 belongs to a
fictional region called **Aloria**, which does not exist. **Do not use this
build to plan a trip.** The app says so on every screen; that notice is not a
bug.

## Common questions

**Why can I not find my journey?**
Because this beta contains no real Greek coach data. See above.

**Why does Odivrelo not sell tickets?**
It is not a ticket seller and never will be without written authority from an
operator. It sends you to the operator's own booking page, or to the operator's
verified ticket office where there is no online sale.

**What does the freshness label mean?**
When that fact was last checked against its source, and how old that is.
`Fresh`, `ageing` and `stale` are thresholds, not guarantees. A stale label
means verify before you travel.

**Why does the map look approximate?**
Route geometry in this release is ordered-stops-only and is labelled as such.
It is not an exact road alignment, and Odivrelo does not pretend otherwise.

**Where is live coach tracking?**
Not in this release. There is no authorised live feed, so Odivrelo shows the
schedule and says live tracking is unavailable rather than animating a coach
that is not there.

**My offline pack will not install.**
Odivrelo verifies a pack's SHA-256 before using it and refuses a corrupt one.
Delete it and download again. If it keeps failing, the release may have been
replaced mid-download; pull to refresh and retry.

**I lost my saved trips on the web.**
Browsers can evict site storage. Odivrelo warns about this because it cannot
prevent it. The mobile apps do not have this limitation.

**Can I get my ticket files back after deleting them?**
No. Deletion is real and Odivrelo holds no copy anywhere.

## Reporting a problem

Send to `info@peterdsp.dev`:

- what you expected and what happened,
- the platform and version, from Settings, which also shows the build commit
  and the data release id,
- the screen, and the journey or operator id if relevant.

**Do not send ticket files, booking references or passenger names.** They are
never needed to diagnose a problem.

## Corrections and takedown

Every operator, station and journey screen has a correction path. Rights
holders can request review or takedown at `info@peterdsp.dev`.

## Accessibility

Accessibility is a release gate, not a feature request queue. If something is
unusable with VoiceOver, TalkBack, a screen reader, a keyboard, large text or
reduced motion, report it as a defect and it will be treated as one.
