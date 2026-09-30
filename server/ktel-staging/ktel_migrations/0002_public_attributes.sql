-- Public presentation attributes for the v1 public data contract.
--
-- The v1 contract (data/schemas/CONTRACT-v1.md) exposes per-entity facts that
-- the staging schema has no column for: a third display language (sq), the
-- boarding bay label, reviewed step-free boarding, boarding instructions, the
-- purchase action and per-journey restrictions.
--
-- Rather than add a dozen sparsely populated columns to tables that are still
-- being shaped by ingestion work, each affected table gains one nullable JSON
-- object column. It is written only by reviewed imports, copied verbatim by the
-- publication compiler (which selects *), and parsed against a strict typed
-- reader in the public API. Nothing existing changes meaning: a row without the
-- column behaves exactly as before.

ALTER TABLE ktel_operators   ADD COLUMN public_attributes TEXT;
ALTER TABLE ktel_stop_places ADD COLUMN public_attributes TEXT;
ALTER TABLE ktel_stops       ADD COLUMN public_attributes TEXT;
ALTER TABLE ktel_trips       ADD COLUMN public_attributes TEXT;

INSERT OR IGNORE INTO schema_version(version) VALUES (2);
