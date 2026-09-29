#!/bin/sh
set -eu

repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
export PYTHONPATH="$repository_root/server/src"

python3 -m unittest discover \
  -s "$repository_root/server/tests" \
  -p 'test_*.py'

python3 -m poravia_pipeline \
  --registry "$repository_root/data/operators/registry.json" \
  validate-registry
