# Public schemas

This directory holds the versioned public interfaces. Every client (Web, iOS,
Android) and the API service implement exactly these shapes.

## What is authored and what is generated

| File | Status |
| --- | --- |
| `CONTRACT-v1.md` | **Authoritative prose.** Hand written. Everything else must agree with it. |
| `public-api-v1.yaml` | Authored. The machine-readable form of the contract, OpenAPI 3.1. |
| `*.schema.json` | **Generated** from the OpenAPI components. Do not edit. |
| `generated/types.ts` | **Generated** by `openapi-typescript`. Do not edit. |

Regenerate everything derived, and validate everything authored, with:

```bash
scripts/gen-contracts.sh
```

CI runs the same script with `--check`, which validates the documents and then
exits non-zero if any committed generated file differs from a fresh generation.
A drifted checkout fails the build rather than shipping types that describe a
promise the service no longer keeps.

The JSON Schema documents are projected from the OpenAPI components rather than
maintained beside them, because two hand-maintained copies of the same contract
eventually disagree. An OpenAPI 3.1 schema object *is* a JSON Schema 2020-12
schema, so the projection is mechanical: resolve the component closure, rewrite
`#/components/schemas/X` to `#/$defs/X`, and add `$schema` and `$id`.

## The documents

| Document | Root shape |
| --- | --- |
| `offline-manifest-v1.schema.json` | The generated release manifest, as served by `GET /v1/offline/manifest` and mirrored into the web client's `data/manifest.json`. |
| `journey-v1.schema.json` | One journey in its detail form. |
| `place-v1.schema.json` | A stop place (terminal) or one of its boarding points. |
| `operator-v1.schema.json` | One operator, its coverage and its sources. |
| `coverage-v1.schema.json` | The coverage and freshness summary. |
| `error-v1.schema.json` | The single error shape every non-2xx response returns. |
| `pack-meta-v1.schema.json` | The `meta` offline pack. |
| `pack-coverage-v1.schema.json` | The `coverage` offline pack. |
| `pack-sources-v1.schema.json` | The `sources` offline pack. |
| `pack-places-v1.schema.json` | The `places` offline pack. |
| `pack-operators-v1.schema.json` | The `operators` offline pack. |
| `pack-stops-v1.schema.json` | The `stops` offline pack. |
| `pack-journeys-v1.schema.json` | One `journeys-<serviceDate>` offline pack. |

Each `pack-*` document is the response schema of the endpoint whose body that
pack carries, not a separate shape invented for offline use. There is one
generator of packs, `server/api/publicapi/packs.py`, and `PackName` in the
OpenAPI document constrains the manifest to the permitted names, so the contract
and the generator cannot drift apart again.

The private administrative surface is deliberately absent from
`public-api-v1.yaml`. It is mounted on a separate origin, requires a bearer
token, and is not a public interface. The drift gate therefore checks the public
surface in both directions and ignores `/admin`.

## Versioning rules

1. **The version is in the path and in every payload.** Routes live under `/v1`
   and every 200 response carries `contractVersion`. The current value is
   `1.0.0` and comes from `brand.json.contractVersion`; nothing restates it.

2. **A breaking change requires `/v2`.** These are breaking:
   - removing an endpoint, a field or an enum member;
   - renaming anything;
   - making an optional request parameter required;
   - making a nullable response field non-nullable, or the reverse;
   - narrowing a type, a pattern or a numeric bound;
   - changing the meaning of an existing value, which is the dangerous one
     because it passes every schema check.

   `/v1` keeps working while `/v2` exists. Clients in the field are not
   upgradable on demand.

3. **Additive changes are a minor bump.** Adding an endpoint, adding an optional
   request parameter, or adding a field that older clients can ignore. Bump the
   minor version in `brand.json` and regenerate.

4. **Adding an enum member is breaking for a closed reader.** Every enum in this
   contract is closed, and the schemas say so. A client that switches
   exhaustively over `purchaseKind` breaks when a sixth member appears. Treat a
   new enum member as breaking unless every client is known to handle unknown
   values, and say which behaviour applies in `CONTRACT-v1.md`.

5. **Response objects are closed.** Response schemas set
   `additionalProperties: false`, or `unevaluatedProperties: false` where they
   compose with `allOf`. That makes the contract exact and makes an accidental
   field a test failure. It also means adding a field is a deliberate, versioned
   act.

6. **Identifiers are opaque.** They are strings and clients must not parse them.
   Their internal shape is free to change without a version bump; anything a
   client needs is a field of its own.

7. **The release id is not a version.** It changes on every publish and says
   which snapshot a response came from. Mixing packs from one release with API
   responses from another is refused with `409 release_mismatch`.

8. **`dataMode` is part of the contract, not a deployment detail.** When it is
   `demo`, a client must show a persistent, non-dismissible notice that the
   services shown are invented and must send `noindex` on web.

## Changing the contract

1. Edit `CONTRACT-v1.md` first. It is the authority and the reviewable artifact.
2. Mirror the change in `public-api-v1.yaml`.
3. Run `scripts/gen-contracts.sh` and commit the regenerated files.
4. Run the API suite. `server/api/tests/test_contract.py` fails if the served
   document and the committed one disagree on any path, method, parameter or
   status code, and validates live responses against the JSON Schema documents.
