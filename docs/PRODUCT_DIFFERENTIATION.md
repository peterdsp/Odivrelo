# HodoMap Product Differentiation Strategy

> **Historical document.** This records a decision made under the product's
> former name, HodoMap, which was rejected on 30 September 2026 and replaced
> by **Poravia**. The text below is preserved as written, including the old
> name, because rewriting a dated decision would falsify the record. See
> [the brand decision](../docs/beta/BRAND-DECISION.md).


Status: recommended product strategy

Prepared: 30 July 2026

Decision method: independent market research followed by three adversarial
product-strategy rounds and a conditional equilibrium check.

## 1. Strategic conclusion

HodoMap should not position itself as another map, timetable list, ticket
marketplace, or generic multimodal planner.

It should create and own a more specific category:

> Greece's intercity coach certainty layer.

The passenger promise is:

> Find the verified journey. Know exactly where to board. Continue to the
> official seller. Keep the trip ready offline.

A shorter expression is:

> Know when. Know where. Know how. Keep it offline.

This fits the existing public line:

> Greece by bus, clearly.

HodoMap becomes unique through the combination of:

1. National KTEL specialization.
2. Exact boarding certainty.
3. Visible evidence and uncertainty.
4. Complete official purchase or contact action.
5. Offline journey continuity.
6. Private saved trips and tickets.
7. Conservative connection and recovery help.
8. Greek, English, and Albanian action guidance.

Maps, tickets, alerts, favorites, and live positions are not individually
unique. The unique product is an evidence-backed journey companion that still
works when operator systems, mobile signal, live feeds, or electronic ticketing
do not.

## 2. Current market reality

HodoMap must differentiate against capabilities that already exist:

| Product category | Existing strengths | HodoMap implication |
|---|---|---|
| Google Maps | Broad place search, transit departures, some realtime, ticket-provider links | Do not compete on generic mapping |
| Moovit | Multimodal planning, live guidance, alerts, favorites, some ticketing, offline map PDFs | A live map and notifications are not enough |
| Omio and Busbud | Cross-provider search, transactions, booking management, offline ticket access | Do not compete as a marketplace without inventory relationships |
| Rome2Rio | Broad multimodal discovery and long-distance route ideas | Do not become a shallow global planner |
| OASA Telematics | Official urban bus positions and stop arrivals in Athens | Live vehicle location is an expected capability where feeds exist |
| Individual KTEL apps | Regional timetables, e-tickets, announcements, favorites, and sometimes live tracking | HodoMap must solve cross-operator fragmentation |

Official feature evidence available during this analysis:

- OASA describes real-time locations and stop arrivals in its official
  telematics app.
- Moovit describes planning, live arrivals, alerts, favorites, live guidance,
  some ticketing, and offline map PDFs.
- Omio describes cross-mode booking and offline access to booking and ticket
  details.
- The KTEL Prevezas App Store listing describes electronic tickets, schedules,
  bus locations, favorites, announcements, and real-time tracking.
- Google Maps supports transit departures and some real-time states, but its
  official offline documentation says public transport directions are
  unavailable offline.

This comparison produces a hard rule:

> HodoMap cannot claim uniqueness from any one conventional travel-app
> feature.

It must own the difficult combination that broad platforms and individual
operators do not naturally provide: national KTEL continuity plus local
boarding detail plus transparent evidence plus offline readiness.

## 3. The user problem HodoMap should own

The primary user question is not merely:

> Which bus goes there?

It is:

> Can I trust this journey for this date, where exactly do I wait, how do I buy
> the ticket, what happens if there is no signal, and what should I do if
> something changes?

This is especially important for:

- Visitors who cannot interpret Greek-only operator pages.
- Residents traveling through an unfamiliar regional operator.
- Albanian-speaking residents and visitors.
- Island and seasonal travelers.
- Passengers transferring between different KTEL operators.
- Passengers connecting to ferries, trains, airports, or another terminal.
- People who need accessibility assistance.
- Travelers whose operator does not sell electronic tickets.
- Travelers using rural stops with weak signs or weak mobile coverage.

## 4. The product stack

HodoMap's differentiation should be designed as four layers.

### 4.1 Visible hero: Trip Ready

Working passenger-facing name: `Trip Ready`.

This is a date-specific journey card answering:

- Is this journey verified for my date?
- Where exactly do I board?
- What is the route variant?
- How do I buy or reserve?
- Do I need to collect or validate a ticket?
- When should I arrive?
- Which stops occur in the middle?
- What is live, estimated, predicted, or scheduled?
- What changed since I saved it?
- Which information will work offline?

It should not look like a technical report. Evidence appears as concise
passenger outcomes:

- `Verified for 30 July`
- `Official operator schedule`
- `Boarding point verified`
- `Online ticket link checked 2 days ago`
- `Position predicted, not live`
- `Contact details may have changed`

### 4.2 Private companion: My Trips

The app conversion happens when a traveler selects `Save for travel`.

The installed companion provides:

- Complete offline route, stops, boarding instructions, and operator contacts.
- Protected local ticket import or system Wallet status.
- Departure, change, delay, stop, and travel-readiness notifications.
- Online live or estimated coach state.
- Offline seasonal prediction.
- A trip-specific action list.
- A post-trip verification prompt.

No account is required for the initial product.

### 4.3 Hidden moat: national proof graph

The defensible infrastructure is:

- Operator identity and official domains.
- Exact stop places and boarding points.
- Route variants and intermediate stops.
- Service dates, calendars, and exceptions.
- Field-level source, effective date, freshness, and review state.
- Official purchase and contact paths.
- Operator-specific boarding and ticket rules.
- Semantic change history.
- Reviewed traveler evidence.

Passengers do not need to see database terminology. They experience the result
as fewer wrong terminals, fewer broken links, fewer unexplained unknowns, and
fewer missed journeys.

### 4.4 Supply flywheel: Operator Bridge

Working internal name: `Operator Bridge`.

Later, HodoMap should give smaller KTEL operators a simple way to:

- Confirm identity and official contacts.
- Publish a timetable or structured update.
- Mark seasonal exceptions.
- Provide exact boarding locations.
- Verify official store and ticket-office links.
- Publish service alerts.
- Connect a GTFS Realtime, SIRI, or documented telematics feed.
- See passenger-reported issues awaiting operator confirmation.

This is not needed for the first pilot. Manual operator onboarding comes first.
The bridge is a later supply-side moat after HodoMap proves passenger value.

## 5. Ranked unique product pillars

### 5.1 Trip Ready card

Priority: highest

Distinctive value:

- Turns fragmented data into one actionable, date-specific travel object.
- Works on Web without installation.
- Converts naturally into an offline saved trip.
- Combines information, action, evidence, and fallback.

Core content:

```text
Kavala -> Thessaloniki
Thursday 30 July, 13:05

Verified for this date
Board at Kavala Central Bus Station, Bay 4
Arrive by 12:35

[Save for travel]  [Buy from KTEL Kavala]

Ticket: electronic
Position: estimated with current traffic
Expected destination: 16:18

Changed since saved: No
```

### 5.2 Boarding Pin

Working name: `Boarding Pin`.

Priority: highest

A terminal name is not enough. HodoMap should distinguish:

- Station complex.
- Ticket office.
- Exact boarding point.
- Bay or platform.
- Correct side of the road.
- Entrance.
- Landmark.
- Walking path from a nearby transit stop.
- Accessibility entrance.

Passenger UI:

```text
Where to board
Bay 4, behind the main ticket hall

Verified entrance
240 m from your current position

[Walk there]  [Show photo or landmark]  [Show to staff]
```

Photos require source rights, moderation, freshness, and accessible
descriptions. A verified textual landmark is preferable to an unlicensed or
stale photo.

### 5.3 Confidence Ladder

Priority: highest

HodoMap already defines the correct position ladder:

1. `Live`
2. `Estimated with current traffic`
3. `Predicted from timetable and seasonal traffic`
4. `Scheduled journey, position unknown`
5. `Unknown`

This should become a recognizable HodoMap design language across:

- Map markers.
- Arrival times.
- Trip results.
- Stop predictions.
- Notifications.
- Connection guidance.

Avoid an opaque percentage such as `83% reliable`. Use clear reasons, time
ranges, and evidence labels.

### 5.4 How to Buy

Priority: highest

Every journey ends with exactly one of:

- `Buy from KTEL <operator>`
- `Open official ticket store`
- `Buy at this ticket office`
- `Call KTEL <operator>`
- `Electronic ticketing unknown`

HodoMap remains independent and link-based. The unique value is that it never
leaves the passenger with a broken or meaningless purchase action.

Operator rules can be journey-specific. For example:

- Electronic ticket available.
- Reservation only.
- Ticket must be collected before departure.
- Buy at station.
- Buy on board.
- Cash requirement.
- First come, first served.

Only publish these rules when an official source supports them.

### 5.5 Signal-Loss Guarantee

Working promise: `Ready without signal`.

Priority: highest

When the passenger saves a journey, HodoMap should automatically prepare:

- Schedule snapshot.
- Route and stops.
- Exact boarding instructions.
- Ticket-office and operator contacts.
- Official store link.
- Imported ticket.
- Local notification plan.
- Normal and seasonal prediction profile.
- Required attribution and freshness.

The saved trip should display:

```text
Ready offline

Route and stops       Ready
Boarding instructions Ready
Ticket                Saved locally
Contacts              Ready
Traffic               Historical Christmas profile
Live updates          Requires connection
```

Google's current help documentation says public transport directions are not
available offline. A complete intercity transit capsule is therefore a
credible user-facing differentiation target.

### 5.6 Connection Shield

Working name: `Connection Shield`.

Priority: next, after corridor data is complete

Do not promise a guaranteed connection. Classify:

- `Comfortable`
- `Tight`
- `Not recommended`
- `Unknown`

Inputs:

- Arrival uncertainty.
- Terminal-to-terminal walking time.
- Boarding cutoff.
- Ticket collection requirement.
- Accessibility walking time.
- Ferry or train check-in requirement.
- Last departure of the day.
- Known fallback departures.

Example:

```text
Connection at Thessaloniki
Not recommended

Scheduled margin: 18 min
Walking between terminals: about 12 min
Ticket collection: arrive 20 min early
No later verified departure today
```

Connection guidance should launch only after these inputs are reviewed. It
must not begin as a simplistic duration comparison.

### 5.7 Rescue Card

Working name: `Rescue`.

Priority: next

When a saved journey changes or becomes uncertain, show:

- What changed.
- Whether the source is operator-reported or calculated.
- Next verified departure.
- Nearby verified terminal.
- Official operator phone and ticket office.
- Ticket-change or refund link.
- Safe connection impact.
- Shareable updated trip status.

Example:

```text
Your journey changed

Departure moved from 13:05 to 13:35
Operator announcement, checked 8 min ago

[View new journey]  [Contact KTEL]  [Share update]
```

Rescue mode should not invent alternatives when coverage is incomplete.

### 5.8 Show to Staff

Working name: `Show to Staff`.

Priority: next

The app can show a large, offline Greek phrase card generated from verified
journey facts:

```text
Θέλω να ταξιδέψω στη Θεσσαλονίκη
με το λεωφορείο των 13:05.

I want to travel to Thessaloniki
on the 13:05 coach.
```

Useful cards:

- `Where do I buy this ticket?`
- `Is this the correct boarding point?`
- `Does this coach stop at <place>?`
- `I need accessibility assistance.`
- `Where should I collect my ticket?`
- `Can I take this luggage or bicycle?`

The phrase must use the saved journey's official Greek place names. Avoid open
ended machine translation for safety-critical instructions in the first
release.

### 5.9 Traveler Proof Loop

Priority: later pilot capability

After a trip, ask a few bounded questions:

- Did the journey operate?
- Was this the correct boarding point?
- Was the published purchase method correct?
- Did the coach stop at the listed intermediate stop?
- What actual departure and arrival did you observe?

Responses enter a private evidence queue. They never change public data
directly.

Benefits:

- Detect stale boarding details.
- Improve historical journey-time profiles.
- Prioritize operator rechecks.
- Produce evidence for operator conversations.

Controls:

- Consent.
- Abuse limits.
- No public user profiles.
- No social feed.
- No direct schedule edits.
- Reviewer confirmation before publication.

### 5.10 Operator Coverage Ledger

Priority: high for trust and partnerships

Publish an understandable national coverage page:

```text
KTEL Kavala
Schedules          Verified
Stops              Reviewed
Road route         Partial
Online tickets     Verified
Live coaches       Not available
Offline prediction Pilot
Last full review   28 July 2026
```

This turns incomplete coverage into honest product information and gives
operators a clear path to improve their public state.

## 6. Differentiation scorecard

Scores are directional and should be revisited after pilot research.

| Idea | Passenger value | Distinctiveness | Feasibility now | Long-term moat | Phase |
|---|---:|---:|---:|---:|---|
| Trip Ready card | 5 | 5 | 4 | 5 | Now |
| Boarding Pin | 5 | 5 | 3 | 5 | Now |
| Confidence Ladder | 5 | 4 | 5 | 4 | Now |
| How to Buy | 5 | 4 | 4 | 4 | Now |
| Signal-Loss Guarantee | 5 | 5 | 4 | 4 | Now |
| Source-change comparison | 5 | 5 | 3 | 5 | Now |
| Show to Staff | 4 | 4 | 4 | 2 | Next |
| Connection Shield | 5 | 5 | 2 | 5 | Next |
| Rescue Card | 5 | 5 | 2 | 4 | Next |
| Traveler Proof Loop | 4 | 4 | 2 | 5 | Later |
| Operator Bridge | 4 | 5 | 2 | 5 | Later |
| Live national map | 4 | 2 | 1 | 3 | Feed-dependent |
| In-app checkout | 3 | 1 | 1 | 2 | Reject |
| Social or gamified travel | 1 | 1 | 3 | 1 | Reject |

## 7. A unique map experience

The map should not be a decorative collection of pins.

### 7.1 Before saving

The map answers:

- Where is the exact origin boarding point?
- Which route variant is selected?
- Which intermediate stops are reviewed?
- Which parts of geometry are exact or uncertain?
- Is the coach live, estimated, predicted, or unknown?

### 7.2 After saving

The map becomes trip-specific:

- Walk to boarding point.
- Boarding entrance or landmark.
- Coach progress or probable route interval.
- Next stop.
- Connection walking path.
- Destination arrival point.
- Ticket office and operator contact.

### 7.3 When something goes wrong

The bottom sheet changes from information to action:

```text
Position source unavailable

Last confirmed: Stop 4 at 14:20
Current prediction: between Stop 5 and Stop 6
Expected destination: 16:20 to 16:35

[Contact KTEL]  [View next verified departure]
```

## 8. Web-first discovery and app conversion

HodoMap should provide useful public pages before asking for installation.

Indexable pages:

- Operator.
- Route.
- Date-aware journey.
- Station.
- Exact boarding point.
- `How to buy`.
- Coverage and source state.

Discovery loop:

1. A traveler searches for a KTEL route, station, or ticket.
2. HodoMap answers the date-specific journey and boarding question on the Web.
3. The traveler shares or saves a stable trip link.
4. Hotels, hostels, stations, tourism offices, and operators distribute
   corridor-specific QR links.
5. HodoMap asks for installation only after `Save for travel`.
6. The app provides offline continuity, notifications, and local ticket
   storage.
7. A post-trip verification can improve the reviewed evidence queue.

The conversion prompt should say:

```text
Keep this journey ready

Use it without signal
Receive important changes
Keep boarding instructions
Store your ticket locally

[Save for travel]
```

Do not block schedules behind installation or account creation.

## 9. Launch wedge

Do not launch three unrelated operators merely to prove adapter variety.

Choose one geographically connected pilot corridor around a meaningful hub:

- One operator with permitted structured schedules and verified online sales.
- One operator using reviewed HTML or PDF schedules.
- One island, seasonal, or contact-only operator.
- At least one realistic transfer or terminal-confusion case.
- Complete boarding-point verification.
- Greek, English, and Albanian passenger copy.

The day-one promise:

> Know when to go, exactly where to board, how to buy, and what changed.

The first release does not need national live tracking or automatic connection
assurance. It needs a small number of complete, trustworthy journeys that
demonstrate the category.

## 10. Corridor completeness

Measure coverage at corridor level, not only operator level.

A complete pilot corridor requires:

- Date-valid schedules.
- Route variants.
- Intermediate stops.
- Exact boarding points.
- Official purchase or contact action.
- Operator travel rules.
- Offline pack.
- Change monitoring.
- Accessible multilingual copy.
- Correction path.

A traveler should never cross from a polished verified segment into a silent
unknown segment. The boundary must be visible before journey selection.

## 11. Operator value proposition

HodoMap must create value for operators, not merely extract their information.

Operator benefits:

- Fewer repetitive calls asking where to board.
- Fewer passengers at the wrong terminal.
- More qualified traffic to the official ticket store.
- Clear attribution and operator ownership.
- Faster distribution of service changes.
- Multilingual passenger guidance without rebuilding the operator website.
- Evidence about recurring passenger confusion.
- A path to publish structured data without developing a new consumer app.

HodoMap should never rank operators based on referral revenue or checkout
conversion without a clear sponsored label and an independent journey-ranking
policy.

## 12. Features to reject or defer

### Reject

- HodoMap checkout, payment, refunds, or seat inventory without operator
  agreements and merchant authority.
- AI-generated missing timetables.
- Timetable-derived markers labelled live.
- Public unreviewed schedule edits.
- Social vehicle tracking.
- Driver ratings.
- Advertising profiles based on journey or location history.
- Public ticket sharing.
- Opaque reliability percentages.
- Gamification that rewards unnecessary travel or data submission.

### Defer

- National transfer guarantees.
- Broad multimodal comparison.
- Demand or crowding estimates without evidence.
- Full operator publishing portal.
- Public traveler reputation.
- Personalized hotel or restaurant recommendations.
- Carbon scoring without a defensible operator-specific method.
- Live-map hero marketing before meaningful authorized coverage.

## 13. Success metrics

### Passenger outcome

- Correct boarding-point task completion.
- Time required to find, board, and buy.
- Missed-trip reports.
- Broken purchase or contact handoffs.
- Offline trip access success.
- Notification usefulness and disable rate.
- Connection guidance error when later enabled.

### Trust

- Critical timetable or boarding errors.
- Percentage of fields with current source and effective date.
- Source-change detection and review time.
- Prediction error by method.
- Percentage of public unknowns labelled explicitly.
- Corrections confirmed, rejected, and awaiting review.

### Product

- Search page to date-specific journey selection.
- `Save for travel` conversion.
- Web to app conversion after journey selection.
- Saved trip used in airplane mode.
- Return use for a second corridor.
- Shared trip link opens.

### Supply and operations

- Review time per operator and corridor.
- Operator response time.
- Official link health.
- Boarding-point verification completeness.
- Number of accommodation or tourism distribution partners.
- Cost per verified journey corridor.

## 14. Pilot gates

Required:

- 100 percent of published journeys have source, effective date, review state,
  and official action or explicit unknown.
- Zero critical timetable, service-date, direction, or boarding-location errors
  in weekly audited samples.
- At least 95 percent of moderated pilot users reach the correct boarding point
  without staff assistance.
- 100 percent of pilot boarding pins are field-verified or officially attested.
- At least 98 percent of purchase and contact handoffs succeed on iOS, Android,
  and Web.
- 100 percent of essential Trip Ready content works in airplane mode.
- Zero ticket, passenger, barcode, or booking-reference data appears in server
  requests, analytics, logs, or notifications.
- Compared with operator websites alone, users complete `find, board, and buy`
  tasks at least 25 percent faster with fewer errors.
- Median routine review effort remains below one hour per operator per week
  outside seasonal changes.

## 15. Kill or redesign criteria

Stop expansion and redesign if:

- A pilot operator lacks sustainable reuse rights.
- A critical public error survives two release cycles.
- Source changes cannot be detected reliably.
- Exact boarding points cannot be verified for at least 90 percent of pilot
  departures.
- Users show no meaningful task-completion improvement over operator websites.
- Routine review exceeds two hours per operator per week for two consecutive
  months.
- Official handoffs repeatedly break without operator cooperation.
- Offline predictions are not measurably better than the timetable baseline.
- Notifications create more confusion than successful action.

## 16. Twelve-month priority order

### Months 1 to 2

- Select one connected tourist or high-confusion corridor.
- Select three complementary operators.
- Secure rights and record explicit coverage boundaries.
- Verify every boarding point and official sales action.
- Define Trip Ready and Confidence Ladder contracts.

### Months 2 to 4

- Publish indexable Greek, English, and Albanian operator, route, station,
  boarding, and date-aware journey pages.
- Add source and effective-date outcomes.
- Add stable shareable trip links.

### Months 4 to 5

- Create printable QR kits.
- Pilot distribution with hotels, hostels, tourism offices, stations, and
  participating operators.
- Measure boarding and purchase task completion.

### Months 5 to 7

- Build the PWA and offline Trip Ready capsule.
- Keep planning complete without account or installation.
- Add source-change comparison.

### Months 7 to 9

- Add native `Save for travel`.
- Add protected ticket import and Wallet status.
- Add local reminders and online source-change notifications.

### Months 9 to 10

- Add private post-trip verification.
- Add evidence review and abuse controls.
- Measure correction turnaround and operational cost.

### Months 10 to 12

- Expand to the next connected corridor only if accuracy, task-completion,
  distribution, review-cost, and conversion gates pass.
- Pilot Connection Shield with labelled unknowns in one reviewed transfer.

### After month 12

- Add authorized live feeds.
- Add seasonal offline prediction only after accuracy gates.
- Add Rescue where alternatives are complete.
- Evaluate Operator Bridge.

## 17. Debate record and equilibrium

### Round 1: moat versus visible value

Attack:

- Provenance is defensible but not a consumer acquisition message.
- A static offline capsule is not enough.
- Cross-operator search is generic without connection and recovery action.

Resolution:

- Evidence remains the hidden moat.
- Trip Ready becomes the visible hero.
- Connection and rescue become active journey capabilities.
- Predicted map intervals are acceptable when clearly distinct from live.

### Round 2: uniqueness versus feasibility

Attack:

- Connection assurance requires unavailable transfer and reliability evidence.
- Rescue is empty without complete alternatives.
- Operator publishing and traveler corrections create cold-start and review
  problems.

Resolution:

- Launch one connected three-operator corridor.
- Defer connection assurance beyond a narrow reviewed pilot.
- Onboard operators manually.
- Accept traveler reports privately into a review queue.
- Use explicit gates and kill criteria.

### Round 3: product value versus distribution

Attack:

- A three-operator passport may not justify an app installation.

Resolution:

- Web pages deliver value without installation.
- Shareable trip links and local QR distribution create discovery.
- Installation occurs only at `Save for travel`.
- Offline continuity, notifications, and local ticket storage provide the
  conversion reason.
- Corridor expansion is earned by accuracy and distribution.

### Conditional equilibrium

Neither position can improve the strategy by replacing its core:

- A generic planner loses local depth.
- A booking marketplace conflicts with current authority and competition.
- A live-map-first product loses trust and coverage.
- A documentation-only product lacks passenger action.
- A native-app-only launch lacks distribution.

The stable strategy is:

> Web-first verified journey discovery plus an installable private trip
> companion, expanded corridor by corridor.

The equilibrium remains conditional on rights, boarding-point verification,
measured user improvement, sustainable review effort, and official handoff
health.

## 18. Final product hierarchy

### Now

1. Trip Ready.
2. Boarding Pin.
3. Confidence Ladder.
4. How to Buy and Contact KTEL.
5. Ready without signal.
6. Source-change comparison.
7. Web-first shareable journey pages.

### Next

1. My Trips and protected local tickets.
2. Travel-readiness and source-change notifications.
3. Show to Staff.
4. One reviewed Connection Shield case.
5. Private traveler proof loop.

### Later

1. Authorized live feeds.
2. Validated seasonal predictions.
3. Rescue mode.
4. Operator Bridge.
5. Additional connected corridors.

## 19. Reference links

- [OASA Telematics App](https://www.oasa.gr/en/passenger-service/tools/telematics-app/)
- [Moovit feature overview](https://moovit.com/features/)
- [Omio app](https://www.omio.com/apps)
- [KTEL Prevezas App Store listing](https://apps.apple.com/gr/app/%CE%BA%CF%84%CE%B5%CE%BB-%CF%80%CF%81%CE%AD%CE%B2%CE%B5%CE%B6%CE%B1%CF%82/id6463635655?l=en)
- [Google Maps transit departures](https://support.google.com/maps/answer/6142130)
- [Google Maps offline limitations](https://support.google.com/maps/answer/6291838)
- [Google Maps transit payment links](https://support.google.com/maps/answer/13485034)
- [KTEL Thessaloniki reservation and ticket-counter information](https://ktelthes.gr/en/reservations/)

