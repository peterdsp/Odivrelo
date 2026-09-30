#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)

export PYTHONPATH="$repository_root/server/src"

exec /usr/bin/python3 -m odivrelo_pipeline \
  --registry "$repository_root/data/operators/registry.json" \
  refresh
