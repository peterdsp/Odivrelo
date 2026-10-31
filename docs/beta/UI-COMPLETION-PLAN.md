# Plan: finish the interface, all three clients

Written 31 Oct 2026, 03:05. Deadline 15:00 the same day, so 11.9 hours.
Branch `redesign/bold-art-direction`, four commits already on it.

The art direction is settled and proven on one page. This plan carries it
across the rest of the product and closes the defects found along the way.

## Rules this session works under

1. **Measure, then speak.** Four times in the previous session a confident
   claim came before the measurement and was wrong. Every finding in this plan
   is a number from a tool, or it is marked as a guess.
2. **One commit per finished, verified piece.** Nothing is committed with its
   verification still running.
3. **A gate that cannot be met is reported, not bypassed.** No `--allow`, no
   widening a timeout to make a red test green.
4. House rules hold: no em-dash, English, minimal diffs, no destructive git
   without asking, no AI references anywhere in the repo.

## Phase 1: know the real scope (03:05 to 04:00)

Measure every web route, not the three looked at so far. Thirteen routes:
welcome, search, results, journey, stations, operators, coverage, saved,
wallet, settings, offline, licences, notfound.

Output: a table of route against failing rule. Group the failures by cause,
because the previous session showed that most are shared code (one token or
one component) rather than per page.

Gate: the table exists and the causes are grouped.

## Phase 2: the shared causes (04:00 to 06:30)

Fix what repeats, in the order the skill asks for: accessibility first, then
convention, then craft.

- `off-centre`, which appears on every inner page and is still not isolated.
  Read the inspector's own source to learn what it measures before touching
  anything else. This is the one open question from the previous session.
- `focal-point` on inner pages: the title is 60px against a preferred 8.4vw,
  and no element dominates the first screen.
- `still-page`: entrances exist but move three or four elements against a
  preferred sixteen.
- `mono` on the licences page.
- `hairline` wherever it remains.

Gate: each fix measured on at least two routes before it is committed.

## Phase 3: per route composition (06:30 to 10:00)

What is left after phase 2 is genuine design work, one route at a time, in
order of how many people see it: search, results, stations, operators,
journey, coverage, the rest.

For each: does the first screen say what this is, does the content fill the
frame, does a directory scan or a list read, is there a picture where a
picture helps. `few-pictures` is answered honestly or defended in one line,
never by decorating.

Gate: every route at 0 FAIL, or its remaining failure written down with the
reason it stands.

## Phase 4: iOS (10:00 to 12:30)

- The Critical defect: at `accessibility-extra-extra-extra-large` the tab bar
  takes a frame of 393 by 793 and swallows every tap on the search form, so a
  journey cannot be searched by finger or by VoiceOver. Try, in order: a
  constrained bar at accessibility sizes, `tabViewStyle(.sidebarAdaptable)`,
  then a custom bar above `accessibility3`. Verify with the controlled pair
  that proved it: passes at `large`, fails at XXXL.
- Screenshot every screen on the new palette, in all three languages, through
  the UI tests that now keep attachments.
- The duration row reads as two numbers running together.

Gate: the controlled pair passes at both text sizes, 171 tests still green.

## Phase 5: Android (12:30 to 13:30)

Build, install, screenshot the main screens on the new palette. The palette
compiles; nothing has looked at it.

Gate: screenshots exist and are read, not just captured.

## Phase 6: close (13:30 to 15:00)

Full suite on every client. Update `docs/DESIGN_SYSTEM.md` for the new
direction. Write the report: what changed, what is measured, what is left,
every found picture with its licence. Leave the branch ready to push.

## Known at the start

- `/welcome` is at 0 FAIL and is the reference for what the direction looks
  like when it has landed.
- `sources.test.ts` times out under machine load and says so in the error. It
  is not a regression. Check `uptime` before believing a failure there.
- Only 959 of 19,139 places in the release have any service, and no stop has a
  bay. The interface must not offer what the data does not carry.
- The contrast gate covers 79 pairings and runs inside
  `scripts/generate-design-tokens.sh`.
