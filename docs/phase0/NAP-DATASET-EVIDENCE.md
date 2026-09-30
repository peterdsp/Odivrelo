# PHASE0-02 evidence: the NAP long-distance bus dataset

> **Historical document.** Written on 30 September 2026, when the product was
> called Poravia. It was renamed **Odivrelo** on 1 October 2026; the evidence
> below is preserved as written. See
> [the brand decision](../beta/BRAND-DECISION.md).

- Ticket: [PHASE0-02](tickets/PHASE0-02-nap-dataset-licence.md)
- Retrieved and inventoried: **30 September 2026**
- Outcome: **no-go for a real-data beta.** The licence is fine. The data is
  not.

## Source identity

| Field | Value |
|---|---|
| Title | Information about transport by long-distance buses in Greece |
| Publisher | Hellenic Institute of Transport (H.I.T.) |
| Catalogue page | `https://data.nap.imet.gr/dataset/information-about-transport-by-long-distance-buses-in-greece` |
| Resource URL | `https://data.nap.imet.gr/dataset/64f46ecc-3971-43f2-98b1-173718eab629/resource/f159d9ab-3e68-435f-9b4a-c160f6fad99e/download/information-about-transport-by-long-distance-buses-in-greece.xlsx` |
| Format | XLSX, 231,397 bytes |
| SHA-256 | `b01d4711774b7a87c70241c420b021da62a38d709518ae0348e7bd066987d856` |
| Retrieved at | 2026-09-30 |
| Dataset created | 11 September 2020, 16:06 EEST |
| **Dataset last updated** | **1 December 2020, 11:27 EET** |
| Licence | **Open Data Commons Open Database License (ODbL) 1.0**, `https://opendefinition.org/licenses/odc-odbl` |

The workbook itself is not committed. It is ODbL, so redistribution would be
permitted with attribution and share-alike, but a 231 KB binary is not
repository content under `DATA_GOVERNANCE.md`. The digest and lineage above are
the committed evidence. The file is retained locally under the gitignored
`artifacts/nap/`.

## Licence finding

The concern recorded in PHASE0-02 was that the Greek NAP carries a mix of
licences including a Non-Commercial Government Licence that would bar
commercial reuse. **That concern does not apply to this dataset.** It is ODbL
1.0, which permits reuse, including commercial reuse, subject to:

- attribution of the Hellenic Institute of Transport, and
- share-alike on any derived database.

The share-alike obligation is a real design constraint on any published
Poravia dataset derived from it and needs a human legal read before a real-data
release. It is not, by itself, a blocker. `data/operators/registry.json`
already marks the NAP catalog `permitted`; this confirms that marking for this
specific dataset with dated evidence.

## Access note: the primary host has an expired certificate

`https://data.nap.gov.gr` presents a Let's Encrypt certificate that expired on
**15 April 2026**:

```
subject= /CN=data.nap.gov.gr
issuer=  /C=US/O=Let's Encrypt/CN=R12
notBefore=Jan 15 15:14:39 2026 GMT
notAfter= Apr 15 15:14:38 2026 GMT
```

TLS verification was **not** bypassed, per the ticket's explicit instruction.
The dataset was instead retrieved from the working mirror
`https://data.nap.imet.gr`, whose certificate is valid. The portal front page
`https://nap.gov.gr` also has a valid certificate and itself points at the IMET
host.

This is an operator-side defect worth reporting to the publisher. Drafting
that note is out of scope here and nobody was contacted.

## Structure inventory

42 prefecture sheets plus one `ΠΛΗΡΟΦΟΡΙΕΣ` contact sheet. 1,184 non-empty
rows in total.

Two mutually incompatible layouts are mixed in one workbook:

**Layout A** (Thessaloniki, Evia, Evrytania, Fokida, Fthiotida, Boeotia,
Halkidiki, Imathia, Kilkis, Pella, Pieria, Serres, Crete, Drama, Evros,
Kavala, Rodopi, Xanthi, Arta, Ioannina, Preveza, Thesprotia):

```
ΔΡΟΜΟΛΟΓΙΑ | ΑΠΟ | ΠΡΟΣ | ΩΡΑ ΑΝΑΧ/ΣΗΣ | ΩΡΑ ΑΦΙΞΗΣ | ΔΙΑΡΚΕΙΑ | ΗΜΕΡΕΣ |
ΤΙΜΗ ΕΙΣΙΤΗΡΙΟΥ (ΟΛΟΚΛΗΡΟ / 0.5 / 0.25) | ΠΛΗΡΟΦΟΡΙΕΣ
```

**Layout B** (Kozani, Kastoria, Grevena, Ilia, Aetolia-Acarnania, Achaia,
Trikala, Magnesia, Larisa, Karditsa, Messinia, Laconia, Corinthia, Argolida,
Arcadia, Zakynthos, Lefkada, Kefalonia, Corfu):

```
Όνομα ΚΤΕΛ | Από | Προς | Απόσταση | Ώρα αναχώρησης | Ώρα άφιξης | Διάρκεια |
Ημέρες | Άλλες πληροφορίες | Απλό | ΑΠΛΟ ΜΕ ΕΠΙΣΤΡΟΦΗ | ΑΠΛΟ 25% | ΑΠΛΟ 50%
```

The `ΠΛΗΡΟΦΟΡΙΕΣ` sheet is a transposed contact table: address, phone, e-mail
and website per operator, laid out one operator per column.

## Why this fails the data gate

The licence passes. Four independent data properties fail, and each one alone
would be disqualifying for this product.

### 1. There are no boarding points, no coordinates and no stop identifiers

Origin and destination are **city names in free-text Greek capitals**:
`ΑΜΦΙΣΣΑ`, `ΑΘΗΝΑ`, `ΘΕΣΣΑΛΟΝΙΚΗ`. There is no stop identifier, no coordinate,
no terminal name, no bay.

Poravia's entire differentiating promise is the exact boarding point and the
Athens terminal-confusion case, Kifissos Terminal A versus Liosion Terminal B.
**This dataset cannot distinguish the two terminals at all.** The contact sheet
gives KTEL Attikis at `Πατησίων 68 - Κότσικα 2, Αθήνα`, which is an office
address, not either intercity terminal. PILOT-04, verify exact boarding
locations, is therefore **unsupported by the only permitted source**.

### 2. The pilot corridor is absent

`ΔΕΛΦΟΙ` (Delphi) appears **zero times** in the entire national workbook. The
KTEL Fokida sheet contains seventeen rows, all built around Amfissa:

```
ΑΜΦΙΣΣΑ → ΑΘΗΝΑ        05:00 (ΔΕΥ ΚΑΙ ΠΑΡ), 10:30, 15:30, 17:30
ΑΘΗΝΑ   → ΑΜΦΙΣΣΑ      07:00, 10:30, 15:00, 17:30
ΑΜΦΙΣΣΑ → ΘΕΣΣΑΛΟΝΙΚΗ  11:00 (ΠΕΜ), 16:00 (ΠΑΡ ΚΑΙ ΣΑΒ)
ΑΜΦΙΣΣΑ → ΛΑΜΙΑ, ΠΑΤΡΑ …
```

The primary corridor in `docs/pilot/CORRIDOR_BRIEF.md`, Athens to Delphi,
cannot be built from this source. The fallback, Athens to Nafplio, does appear
in the Argolida sheet, but with the same defects: no boarding point, no arrival
time, departure times crammed into one cell as
`5:00,7:00,8:00,9:30,11:00,12:30,14:00,15:30,17:30,18:30,20:30`.

### 3. It is five years and ten months stale, and says so about itself

Last updated **1 December 2020**. The `ΠΛΗΡΟΦΟΡΙΕΣ` sheet carries its own
provenance note that the information was retrieved from operator websites
"in August". The Achaia sheet does not give departure times at all; in the time
column it says **`ΤΡΟΠΟΠΟΙΟΥΝΤΑΙ ΚΆΘΕ ΕΒΔΟΜΑΔΑ`**, "modified every week". The
publisher is telling the reader, in the data, that these values move faster
than the file does.

Publishing 2020 departure times as current travel instructions is precisely
what the product principle "evidence before coverage" forbids.

### 4. The calendar model is free text and there is no validity period

Service days are unnormalised Greek prose with inconsistent spelling:
`ΚΑΘΗΜΕΡΙΝΑ`, `ΔΕΥ ΜΕΧΡΙ ΠΑΡ`, `ΔΕΥ-ΠΑΡ`, `ΕΚΤΟΣ ΣΚ`, `ΕΚΤΟΣ Κ`,
`ΚΑΘΗΜΕΡΙΝΑ ΕΚΤΟΣ ΣΑΒΒΑΤΟΥ`, `ΤΡ ΠΑΡ ΚΑΙ ΚΥΡ`, `ΔΕΥ,ΤΕΤ,ΠΑΡ`. There is no
`valid_from`, no `valid_until`, no exception list, and no seasonal variation.
Most rows have no arrival time and no duration, so chronology cannot be
validated. Several rows carry annotations such as `09:30(Ζ)` whose meaning is
undocumented.

## Decision

**No-go.** This dataset cannot seed PILOT-06, and no operator permission is in
hand for any corridor, so PILOT-02 cannot supply one either.

Consequences, recorded as
[AD-002](../beta/ARCHITECTURE-DECISIONS.md#ad-002-30-september-2026-the-beta-ships-an-invented-demonstration-dataset-not-greek-coach-data):

- Gate D1 in `docs/PILOT_DECISION.md` **fails on evidence**, not on
  assumption. Assumptions 2, 3 and 4 in that document are now refuted for this
  source.
- The beta ships a clearly labelled invented demonstration dataset, and every
  client says so on screen.
- What it would take to pass is recorded in
  [`docs/beta/EXTERNAL-BLOCKERS.md`](../beta/EXTERNAL-BLOCKERS.md).

## What the dataset is still good for

- **Operator contact and website lineage.** The `ΠΛΗΡΟΦΟΡΙΕΣ` sheet is a
  lawful, attributable source for operator addresses, phones, e-mails and
  official sites, which is exactly the ticket-office fallback Poravia promises
  for operators without online sales. It is stale but it is checkable.
- **A structural reference** for what a Greek intercity feed must normalise:
  two layouts, prose calendars, city-level stops and missing arrivals. The
  adapter design should assume all four.
- **A baseline** for measuring any future operator-supplied feed against.

It is not a timetable source for a public release.

## Method and limits

- One HTTPS GET for the catalogue page and one for the resource. No crawling,
  no bypassed TLS, no authentication, no contact with the publisher.
- `openpyxl` read-only inventory. No row was published, imported into the
  ingestion database, or exposed to any client.
- This is an engineering assessment of fitness for purpose. The ODbL
  share-alike analysis is not legal advice and needs a human legal read before
  any real-data release.
