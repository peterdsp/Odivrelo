# Odivrelo design

The design source of truth is:

- `tokens/odivrelo.tokens.json` for platform generation.
- `tokens/odivrelo.css` for the Web application.
- `Odivrelo-Brand-Board.svg` for the visual direction, and `logo/` for the marks
  and the generator that draws them.
- `docs/DESIGN_SYSTEM.md` for product and accessibility rules.

Do not copy color values directly into application features. Platform themes
consume generated semantic tokens: `scripts/generate-design-tokens.sh` writes
the Web, Android and iOS colours from the token file and audits WCAG AA
contrast in both themes.
