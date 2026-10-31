# Prompt: a bold new art direction for Odivrelo

Hand this whole file to an agent session opened in the Odivrelo repo. It mirrors the
"Greek light" redesign done for the sibling product Nostavela, adapted to Odivrelo's
stack, brand and subject. Keep the house rules: no em-dash anywhere, no AI references
or attribution in commits, PRs or docs, English as the base language, minimal diffs,
and no autonomous destructive git actions.

---

You are redesigning Odivrelo with a completely new, bold visual identity. Odivrelo is a
certainty layer over Greece's intercity coach (KTEL) network: it finds the right journey,
says exactly where to board (terminal and bay), shows when each fact was checked and which
source backs it, and sends the traveller to the correct operator's own ticket page. The
data mode is demonstration only, every value is labelled as such, and nothing is presented
as a live fare, a booking or an operator partnership. Three languages ship: Greek (el),
English (en), Albanian (sq). Tagline: "Travel with certainty."

Stack: `apps/web` is React 19 + Vite + TypeScript; `apps/ios` and `apps/android` are the
native apps; `server/` is the Python API. The single source of identity is `brand.json`;
platform constants are regenerated from it (see `scripts/`), so a brand change starts there.
Design tokens live in `design/tokens/odivrelo.css` and `design/tokens/odivrelo.tokens.json`.
The current palette is a deep blue `--od-primary: #0b4a6b` with an orange accent
`--od-accent: #ff9f2e` on cool blue grounds (`--od-background: #f4f8fb`).

## Use these skills, in this order

1. `/design-taste-frontend` is the engine. Odivrelo's web is a product that is read and
   scrolled (a landing and results experience) plus app-like screens worked in (the journey
   result, the board-here detail). Treat the marketing and overview pages as **pages** (the
   main run) and the interactive result and detail surfaces as **app screens**
   (`--app`, `references/app-screens.md`). Deal directions, do not accept the category
   default, and write one line on why the chosen direction carries Odivrelo's own idea.
2. `/apple-hig` and `/apple-design` are review lenses for the native iOS app and for the
   web's touch ergonomics: 44pt iOS and 48dp Android targets, Dynamic Type, clarity,
   deference and depth, and colour that never carries meaning alone.
3. `/app-intents-specialist` applies here, because Odivrelo has a real native iOS app:
   propose and, where the native target supports it, implement App Intents and Shortcuts
   for the core action (for example "next coach from <terminal>"), with a complete
   non-Siri fallback. Skip it only if the iOS target cannot yet host a native module.

## Find the idea from the subject, not the category

Do the inventory the skill asks for, drawn from coaches and KTEL, not from generic travel.
Strong hooks for the art direction:

- **The bay and the board.** A terminal departures board, the bay number, the platform
  edge: Odivrelo's whole promise is "the right one, and exactly where to stand." Let the
  departures board or the bay marker become the page's motif.
- **The route line.** The intercity road across Greece, the line from terminal to terminal,
  the ticket stub. A real map of one corridor (for example Athens to Ioannina) as a
  section, not a decorative world map.
- **Greek light and distance.** The view from a coach window, the mountains and the sea the
  route passes. Real archival or openly-licensed photography of Greek terminals, roads and
  landscapes (use `assets.mjs`, commercial-use licences only, credit in the footer).
- **Certainty.** Each fact shows its source and the date it was checked. Make that visible
  provenance a designed element, not fine print.

Fill this sentence before building: "On this page, **[something only Odivrelo has]** becomes
**[the device the direction calls for]**." Put a competitor coach-search site in the sentence;
if it still works, the idea is the category and you must dig again.

## The palette and type

Deal a bold new palette rather than keeping `#0b4a6b`/`#ff9f2e`. The subject's own colours
are a strong starting point: the deep blue of a highway sign, the amber of a departures
board, Greek light. Whatever you choose, set it in `brand.json` and regenerate, then define
the full ramp in `design/tokens/odivrelo.css` and `.tokens.json`, and prove every pairing
against the project's own contrast check: body and muted text on each ground, action text on
the action colour, and any status ramp distinguishable from its neighbours and visible on
every surface it is drawn on, in both light and dark, to WCAG AA. Push the display type
larger with slight negative tracking on the big sizes only; keep body text at zero tracking.
Lead the hero with a real photograph of a Greek terminal, road or landscape, not with chrome.

## Honesty invariants (do not break)

- Demonstration data is always labelled as demonstration data. No value may imply a live
  fare, real availability, a booking or an operator partnership.
- A missing figure stays missing; it never becomes zero.
- Every displayed fact keeps its source and its checked-on date. The redesign makes that
  more visible, never less.
- Required map and data attributions stay visible.

## Deliverable and verification

Apply the new direction across the web pages and app screens, and carry the tokens into the
native iOS and Android apps through `brand.json` and the generated constants. Then verify,
and report passed, failed and not-run separately:

- `cd apps/web && npm run typecheck && npm run lint && npm run test && npm run build`
- `npm run test:e2e` for the Playwright journeys, and the `budget` check for bundle size.
- Drive the built site in a real browser at phone, tablet and desktop widths. Capture
  screenshots of the hero, the results and the board-here detail, in all three languages,
  and confirm no console errors and no English leaking into el or sq.
- Run the `/apple-hig` and `/apple-design` lenses over the iOS screens and record the
  touch-target, Dynamic Type and colour-alone findings.

Publication: deploy only to the authorised Cloudflare Pages preview
(`odivrelo.peterdsp.dev`) after the web checks pass. Do not register a domain, open a store
record, contact any operator, or present anything as live. Commit with plain messages, no
em-dash and no AI attribution, and branch rather than committing to the default branch
without approval.
