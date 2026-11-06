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
  (Outcome: the pair was measuring the fixture, see below.)

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

---

# Outcome, 14:40

The session ran from 03:05 to the 15:00 deadline. What follows is what was
measured, not what was intended.

## Done and measured

| Surface | At the start | Now |
| --- | --- | --- |
| six app screens | judged by the wrong ruleset | 0 FAIL each, under `--app` |
| settings | 5 FAIL | 0 |
| welcome | 0 FAIL | 0 |
| operators, coverage, licences | 4 FAIL each | 2 each |
| stations | 6 FAIL | 3 |

Cleared across the product: `focal-point`, `still-page`, `mono`, `hairline`,
`emoji`, `shadow`, `body-size`, and five text sizes that sat below the readable
floor in shared code.

Also done in the same branch, before this plan was written: the art direction
itself on the welcome page, the palette and the two self-hosted faces across
web, iOS and Android, the Apple HIG review, and an App Intent for the next
coach from a terminal.

## Open, with the reason

**`few-pictures` on four pages.** The floor is four real pictures. These are
directories of real data. A terminal photograph on the station directory is
meaningful; padding a licences page with stock photography is decoration,
which the same guidance forbids. Answer it per page or defend it per page, but
do not pad it.

**`off-centre` on four pages.** Seven attempts, each measured. The rule reads
the 98th percentile of text extents, so on a 129 box page roughly 125 boxes
must reach the right margin. This product's pages are built from short strings:
operator names, "266 stops", "Covered". The threshold is calibrated on
marketing pages with wide prose and does not fit a data directory. The footer
defect it uncovered was real and is fixed.

**`empty-screens` on stations.** Four of seven desktop screens are mostly
ground. That is a composition question about what a station directory should
show, and it is genuinely open.

**iOS at the largest text size: withdrawn, there is no defect.** This section
previously recorded, as the product's one Critical finding, that a journey
could not be searched at `accessibility-extra-extra-extra-large`. Walking the
whole flow on the real release on 5 Nov disproves it: both pickers open, both
accept a place, and the search button ends up enabled.
`testJourneyIsSearchableAtLargestTextSize` keeps that honest.

Two reading errors produced the finding, and both are worth keeping.

The first is the indicator. "Did the picker open" was answered with
`app.sheets.count`, but the picker is a navigation push, so that count is zero
either way. The navigation bars say it plainly: `From | Search` is an open
picker.

The second is the subject. Every UI test launches with `-OdivreloUseFixture`,
so all of it measured `FixtureCoreClient` and Aloria's invented names, never
the app. The real release carries Greek names of a different length, and length
is the one variable that genuinely changes at this text size.

Four candidate fixes were tried against this and each was measured rather than
assumed: capping the bar's label size, the adaptable tab style, an explicit hit
shape, and limiting the field's height. None moved the symptom because there
was no symptom. **A cause that keeps evaporating under measurement is evidence
the symptom is mismeasured.** That reading was available four eliminations
earlier than it was taken.

**Android** has the new palette compiled but nothing has looked at it.

## Correction: the place offer was fixed on two clients, not four

Commit 513076d says "one wrong order, four broken lists". That is wrong, and
the mistake was reasoning from the architecture instead of reading the call
sites.

**iOS picker and the App Intent: fixed.** Both go through the Kotlin core's
`searchPlaces`, and the picker was looked at: Tripoli 75, Kerkyra 54, Kiato 49.

**Web: already solved, differently, and the paragraph that stood here was
wrong twice over.** It is true that `apps/web/src/data/packQuery.ts` implements
`searchPlaces` in TypeScript and never calls the Kotlin core, and true that its
empty-query branch filters nothing by coverage. What does not follow, and what
was asserted anyway, is that the picker shows that list. `PlaceField.tsx:86`
reads `query.trim().length === 0 && popular ? await popular() : await
search(query)`, so an empty box calls `popularPlaces`, a curated set of seven
station names sorted so covered records lead and deduplicated by name.

The same mistake twice in one day, in opposite directions: first claiming the
core fix reached four clients, then claiming web still carried the bug. Both
came from reading one layer and inferring the next. The call site is one grep
away and settles it.

**Android: never had this bug and still does not get the better list.**
`OdivreloViewModel.openPlacePicker` sets `places = Loadable.Idle` and queries
nothing, so the picker opens on "type to search" rather than on a list. Taking
the core's improved offer would mean calling `setPlaceQuery("")` when the
picker opens. Not done here: it cannot be built or seen on this machine, and
shipping a change to a client nobody can look at is how a fixture build
reached a phone earlier in this session.

That leaves a real product question for a person, and it is bigger than it
looked. Three clients now answer "what do I offer before anyone types" three
ways:

- **iOS** ranks covered terminals by boarding points, from the release, so the
  list follows the data: Tripoli, Kerkyra, Kiato, Trikala.
- **Web** shows seven station names written into `Search.tsx`, chosen by hand
  because they read well: Athens, Agrinio, Patras, Tripoli, Astros, Paralio
  Astros, Megalopoli.
- **Android** shows nothing and asks for typing.

Each is defensible alone. Together they mean a traveller meets a different
product on each, and the web list cannot follow the data: when a release adds
or drops service, seven hardcoded names stay exactly as they were. That is a
decision to make deliberately, not three accidents to leave standing.

## A traveller can be told no coach runs when sixteen do

This is the sharp end of the duplicate-terminal question below, and it is not
cosmetic. Found on 7 Nov while trying to photograph a journey detail.

Searching **ΑΘΗΝΑ (ΣΤΑΘΜΟΣ ΚΗΦΙΣΟΥ) to ΑΓΙΑ ΣΟΦΙΑ (ΠΑΤΡΑ)** on 7 Nov returns
"This corridor is not covered by the current data release." That day's journey
pack carries **sixteen departures** on exactly that named pair, more than any
other corridor in the release.

The app is not wrong; it is answering about the stop it was given:

    ΑΘΗΝΑ (ΣΤΑΘΜΟΣ ΚΗΦΙΣΟΥ)   8 stop records, 7 covered, 1 not covered
    ΑΓΙΑ ΣΟΦΙΑ (ΠΑΤΡΑ)        1 record, covered
    the sixteen journeys run   ks_981ad5612757337fb6654902 -> ks_9aa9fbd42f9c171ee8b90642

Eight records share one name. The picker lists them identically: same text,
and seven of them carry a green "Covered" badge. Only one has these
departures. Pick any of the other seven and the product says nothing runs.

Two things follow that are worth separating.

**The badge does not mean what a reader will take it to mean.** "Covered" on a
place says that place has some service somewhere, not that it has service on
the corridor being asked about, and not that this is the record the timetable
uses. Seven stops can be truthfully badged covered while six of them answer
"nothing runs" for a route that runs sixteen times.

**Picking by name is a lottery.** Nothing on the row distinguishes the eight,
so a traveller cannot choose the right one even in principle, and the odds of
landing on it here are one in eight.

Possible answers, none applied because each is a product decision:

- Collapse stops sharing a name and parent in the release pipeline, so one
  record carries the union of the departures.
- Expand a search to every stop sharing the chosen one's name and parent,
  which keeps the data as it is and makes the question mean what a person
  intends by it.
- Show what distinguishes them on the row, which means admitting the model is
  an operator's view of a stop rather than a stop.

The first is the only one that also fixes the station directory and the
pickers on every client, and that is checked rather than assumed: web and iOS
are served the same file, `places-0a57c80efd12a60c.json`, same content hash,
carrying the same eight records with the same seven covered badges. The defect
is in the published release, not in a client, so no client can fix it alone and
each would have to work around it separately.

Until one answer is chosen, the product's central question can return the wrong
answer to a reasonable search, on whichever client a traveller opens.

## The same station, several times: a decision nobody has made

The station directory shows ATHENS six times in its first forty entries. It is
not a rendering fault, and it is not safe to collapse by name.

The release carries **17 terminals called ATHENS**, one per KTEL operator, and
they are not all the same place:

- Six sit at exactly 37.9986, 23.6989: arta, thesprotia, kastoria, zakynthos,
  imathia and kavala have each imported the same physical terminal.
- Others are up to fifteen kilometres apart. ktel-fthiotida's is at 38.0663,
  23.7660 and ktel-kefalonia's at 38.0234, 23.6175. Those are different
  terminals in Athens, both named ATHENS, which is what the source data says.

Nothing distinguishes them on the page: `municipality` is null on all of them,
so each row reads ATHENS, "not known", and a list of boarding points.

Across the release, collapsing only records that share a name **and** a
coordinate to within a metre would remove 85 of 4,222 terminals: PATRA six
times at one point, THESSALONIKI six, GREVENA and SOFADES four each. That merge
is provably safe in the sense that the records describe one place. It is not
free: each carries its own `operatorIds` and its own children, so merging has
to decide what the union means, and a terminal that loses its operator list
loses the answer to "whose coach leaves from here".

The alternative is to keep them and tell them apart, which means showing the
operator where the municipality is empty: "ATHENS, KTEL Fthiotida". That is
honest about what the data models, which is not a station but an operator's
view of a station.

Both are product decisions, so neither was applied. The same question sits
behind the boarding points that appear twice fifteen metres apart, which are
most likely the two sides of a road.

## The simulator was not flaky, and the scroll was not needed

Both were settled on 7 Nov, and both had been reported wrongly before that.

**The UI test failures had one cause.** The iOS 27.0 simulator runtime ships
`UIAccessibilityLoaderWebShared` in two accessibility bundles, so the
accessibility server never finishes starting:

    Failed to initialize for UI testing: XCTDaemonErrorDomain Code=18
    "Timed out waiting for AX loaded notification"

Everything that looked like flakiness came from that: `kAXErrorServerNotFound`,
`Mach error -308`, runners crashing before connecting, roughly a dozen failures
over two days through service kills, device erases and reboots. The advice
recorded here previously, that it wanted a machine restart, was wrong and would
not have helped. Create a device on another runtime, `iOS-26-5`, and the same
test runs in sixteen seconds.

**The answer was never hidden.** `revealAnswer` is removed. The measurement
that ended it:

    answer   538.3 to 743.0
    tab bar  top at 791.0
    screen   874.0

The answer arrives whole, 48 points clear of the tab bar, directly under the
button pressed. Three rounds of fixing the scroll produced the same number
because the scroll was already doing everything it could; a scroll view cannot
lift the last thing in its content to the top of a screen.

That makes two defects chased in this branch that measurement did not support,
the other being the largest-text-size one above. Both began as an inference
from a screenshot, found plausible, and built on before being measured. The
screenshot here showed text passing under a translucent bar, which is what
scrolling content does.

What survives is `testTheAnswerArrivesOnScreen`, asserting that the answer
begins somewhere visible. It asserts the beginning and not the end on purpose:
a list of departures is meant to run past the fold, and an earlier version of
the assertion failed for a long answer, which would have made the test punish
the product for working.

## For whoever continues

`sources.test.ts` times out above roughly load average 100 and says
`Test timed out in 60000ms`. Check `uptime` before believing a failure there.

When the gate disagrees with you, instrument the gate. Seven attempts at
`off-centre` failed by reasoning about the page; the eighth succeeded by
replicating the rule's own arithmetic in the browser and reading which
elements set its numbers. It was the footer, on every route, the whole time.
