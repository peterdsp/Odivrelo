# PILOT-01: Select the pilot corridor and operators, with a fallback

- Type: research
- Status: done, 7 September 2026
- Step: 2, data check
- Depends on: none
- Blocks: PILOT-02, PILOT-03, PILOT-04, PILOT-05
- Owner: unassigned
- Estimate: 1 to 2 days

## Goal

Choose one geographically connected corridor around a meaningful hub, name the
operators involved, and name a fallback corridor to switch to if the data check
fails.

## Why

The corridor is chosen by which one's data actually checks out, not by ambition.
Naming a fallback up front keeps the pilot from stalling if the first choice
fails PILOT-02 to PILOT-05.

## Outcome

Selection recorded in [the corridor brief](../CORRIDOR_BRIEF.md).

- Primary: Athens to Delphi, hub Athens Liosion Terminal B. Operators
  `ktel-fokida` (through service), with `ktel-livadeia` or `ktel-thiva` as the
  intermediate or transfer leg. Terminal-confusion case: Delphi departs Liosion,
  not the better-known Kifissos.
- Fallback: Athens to Nafplio, `ktel-argolida`, from Kifissos Terminal A.
- Second fallback if operator-type diversity is later preferred: Thessaloniki to
  Halkidiki, `ktel-thessaloniki` and `ktel-halkidiki`.

The choice stays provisional until the data-check gate
[PILOT-07](PILOT-07-data-check-gate.md).

## Tasks

- [ ] Pick a primary corridor: one connected route around a hub with real
      traveller confusion (tourist route, terminal confusion, or a transfer).
- [ ] Identify the operators on that corridor from
      `data/operators/registry.json`.
- [ ] Confirm at least one realistic transfer or terminal-confusion case exists
      on the corridor.
- [ ] Pick a fallback corridor with different operators.
- [ ] Record both in a short corridor brief committed to `docs/pilot/`.

## Candidate corridors (examples only, not a decision)

- Athens hub to Delphi. Tourist and high-confusion, with the Athens Liosion
  versus Kifissos terminal question as a real boarding-point test. Operators
  include KTEL Attiki and KTEL Fokida, with a Livadeia or Boeotia transfer.
- Thessaloniki hub to Halkidiki. Seasonal tourist demand. Operators include
  KTEL Thessaloniki and KTEL Halkidiki.
- Fallback example: Athens to Nafplio via KTEL Argolida.

Do not treat these as chosen. The chosen corridor is whichever one passes the
data check with the least effort.

## Acceptance criteria

- [ ] One primary corridor named, with its operators and at least one transfer or
      terminal-confusion case.
- [ ] One fallback corridor named, with different operators.
- [ ] A one-page corridor brief exists under `docs/pilot/`.

## Kill or switch criteria

- If no connected corridor has a plausible path to `permitted` rights, stop and
  escalate to the decision owner before spending effort on data work.

## Out of scope (provisional, do not build yet)

- Any national corridor inventory.
- Any routing engine or automated corridor scoring.

## References

- [Pilot decision](../../PILOT_DECISION.md)
- [Product differentiation strategy, launch wedge](../../PRODUCT_DIFFERENTIATION.md)
- `data/operators/registry.json`
