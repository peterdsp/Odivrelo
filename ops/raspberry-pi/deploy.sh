#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
target=${ODIVRELO_PI_TARGET:-peterdsp@192.168.10.10}
remote_root=/home/peterdsp/odivrelo

case "$target" in
  peterdsp@*)
    ;;
  *)
    echo "Refusing unexpected deployment target: $target" >&2
    exit 1
    ;;
esac

rsync -az \
  --exclude .git/ \
  --exclude .env \
  --exclude '*.db' \
  --exclude '*.db-shm' \
  --exclude '*.db-wal' \
  --exclude data/raw/ \
  --exclude data/staging/ \
  --exclude data/private/ \
  --exclude data/releases/ \
  "$repository_root/" "$target:$remote_root/"

ssh "$target" sh -s <<'REMOTE'
set -eu

repository_root=/home/peterdsp/odivrelo
config_root=/home/peterdsp/.config/odivrelo
unit_root=/home/peterdsp/.config/systemd/user

mkdir -p \
  "$config_root" \
  "$unit_root" \
  /home/peterdsp/.local/share/odivrelo \
  /home/peterdsp/.local/state/odivrelo/reports

chmod 0755 "$repository_root/ops/raspberry-pi/run-acquisition.sh"

if [ ! -f "$config_root/acquisition.env" ]; then
  cp \
    "$repository_root/ops/raspberry-pi/acquisition.env.example" \
    "$config_root/acquisition.env"
  chmod 0600 "$config_root/acquisition.env"
fi

cp \
  "$repository_root/ops/raspberry-pi/systemd/odivrelo-acquire.service" \
  "$unit_root/odivrelo-acquire.service"
cp \
  "$repository_root/ops/raspberry-pi/systemd/odivrelo-acquire.timer" \
  "$unit_root/odivrelo-acquire.timer"

PYTHONPATH="$repository_root/server/src" /usr/bin/python3 \
  -m odivrelo_pipeline \
  --registry "$repository_root/data/operators/registry.json" \
  validate-registry

systemctl --user daemon-reload
systemctl --user enable --now odivrelo-acquire.timer
systemctl --user list-timers odivrelo-acquire.timer --no-pager
REMOTE
