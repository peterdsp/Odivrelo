#!/usr/bin/env python3
"""Split the national stops pack for the Cloudflare Pages per-file limit."""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

root = Path(sys.argv[1])
manifest_path = root / "manifest.json"
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
entry = manifest["files"]["stops"]
source = root / entry["path"]
body = json.loads(source.read_text(encoding="utf-8"))
items = sorted(body["stops"].items())
chunk_size = 3000

def write_pack(name: str, payload: dict) -> dict:
    encoded = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    digest = hashlib.sha256(encoded).hexdigest()
    filename = f"{name}-{digest[:16]}.json"
    (root / "packs" / filename).write_bytes(encoded)
    return {"path": f"packs/{filename}", "sha256": digest, "bytes": len(encoded), "mediaType": "application/json"}

chunks = [items[i:i + chunk_size] for i in range(0, len(items), chunk_size)]
manifest["files"]["stops"] = write_pack("stops", {**body, "stops": dict(chunks[0])})
for index, chunk in enumerate(chunks[1:], start=1):
    manifest["files"][f"stops-{index}"] = write_pack(f"stops-{index}", {**body, "stops": dict(chunk)})
manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
source.unlink()
print(f"split {len(items)} stops into {len(chunks)} web packs")
