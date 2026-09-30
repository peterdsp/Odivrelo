# Corridor brief: pilot selection (PILOT-01)

> **Historical document.** This records a decision made under the product's
> former name, HodoMap, which was rejected on 30 September 2026 and replaced
> by **Poravia**. The text below is preserved as written, including the old
> name, because rewriting a dated decision would falsify the record. See
> [the brand decision](../../docs/beta/BRAND-DECISION.md).


Status: proposed, provisional. The final corridor is confirmed only when it
passes the data-check gate [PILOT-07](tickets/PILOT-07-data-check-gate.md). This
brief names the primary and fallback so the data check has a target.

Date: 7 September 2026

Prepared for: [PILOT-01](tickets/PILOT-01-select-corridor.md)

## Primary corridor: Athens to Delphi

Hub: Athens. Direction: central Greece.

Why this corridor:

- Iconic, high-volume tourist destination, so likely travellers are easy to
  recruit for the five-traveller test (PILOT-09).
- It carries a genuine, high-cost terminal-confusion case, which is the heart of
  the boarding-point promise.
- The through service is a single operator with an online ticket site, which
  keeps the reviewed feed (PILOT-06) small and buildable.

### The terminal-confusion case

Athens has two main intercity terminals that serve different halves of the
country:

- Kifissos, Terminal A: Peloponnese, western Greece, and the north.
- Liosion, Terminal B: central Greece and Thessaly, including Delphi.

Delphi departs from Liosion, Terminal B. A traveller who heads to the larger,
better-known Kifissos terminal is at the wrong station. This confusion is real
enough that a major journey aggregator currently lists an "Athens Kifissos to
Delphi" routing, which points people to the wrong terminal. Getting this one
fact right, visibly, is the single clearest demonstration of the certainty
layer.

### Operators on the corridor

From `data/operators/registry.json`:

- `ktel-fokida`, federation number 59, KTEL Fokida. Operates the through
  Athens to Delphi service from Liosion. Appears to have an online ticket site.
  This is the primary structured, online-sales operator.
- `ktel-livadeia`, federation number 39, KTEL Livadeia. Boeotia services from
  Liosion toward Livadeia, on the same central-Greece axis. Candidate for the
  intermediate or transfer leg.
- `ktel-thiva`, federation number 20, KTEL Thiva. Also Boeotia, same axis.
  Alternate transfer candidate.

The pilot journey is Athens (Liosion) to Delphi, with likely intermediate stops
at Livadeia and Arachova, to be confirmed in PILOT-03.

### How this maps to the launch-wedge ideal

The differentiation wedge asks for three complementary operators, including one
island, seasonal, or contact-only operator, plus at least one transfer or
terminal-confusion case. This corridor:

- Satisfies the terminal-confusion case strongly.
- Satisfies structured-plus-online (KTEL Fokida) and a second reviewed-source
  operator (KTEL Livadeia or Thiva).
- Does not naturally include an island or contact-only operator. That diversity
  is deferred, not forced. The terminal-confusion value is judged more important
  for a first journey than operator-type variety.

## Fallback corridor: Athens to Nafplio

Hub: Athens. Direction: Peloponnese.

Why this fallback:

- Different operator and different terminal from the primary, so a failure in the
  Delphi corridor's rights or freshness does not also sink the fallback.
- Also a tourist route with its own terminal question, since Nafplio departs from
  Kifissos, Terminal A.

Operator:

- `ktel-argolida`, federation number 4, KTEL Argolida. Operates Athens to
  Nafplio from Kifissos.

## Second fallback, if operator-type diversity becomes the priority

Thessaloniki to Halkidiki, using `ktel-thessaloniki` (19) and `ktel-halkidiki`
(60). Seasonal beach demand, and Halkidiki coaches depart a separate Thessaloniki
terminal from the main intercity station, which is another terminal-confusion
case. Listed only as an option if a later decision favours seasonal or
separate-terminal diversity over the Delphi tourist volume.

## What is confirmed versus what the data check must verify

Confirmed enough to choose a target:

- Delphi departs Athens Liosion, Terminal B. The Peloponnese departs Kifissos,
  Terminal A. The terminal split is real and is a genuine traveller trap.
- KTEL Fokida operates the Athens to Delphi through service.

To verify in the data check, not assumed here:

- Reuse rights for each operator's publishable timetable. Today the registry
  marks every operator directory source `unknown` and only the NAP catalog
  `permitted`. See [PILOT-02](tickets/PILOT-02-confirm-reuse-rights.md).
- Current, date-valid departures, intermediate stops, and calendars. See
  [PILOT-03](tickets/PILOT-03-confirm-freshness-completeness.md).
- Exact boarding points and coordinates at Liosion and at the Delphi end. See
  [PILOT-04](tickets/PILOT-04-verify-boarding-points.md).
- A working official purchase or contact action per operator. See
  [PILOT-05](tickets/PILOT-05-verify-purchase-action.md).

## Recommendation

Target the Athens to Delphi corridor for the data check, with Athens to Nafplio
held as the fallback. Do not treat the choice as final until PILOT-07.

## References

- [Pilot decision](../PILOT_DECISION.md)
- [Pilot backlog](README.md)
- [Product differentiation strategy, launch wedge](../PRODUCT_DIFFERENTIATION.md)
- `data/operators/registry.json`
