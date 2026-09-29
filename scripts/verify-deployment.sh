#!/usr/bin/env bash
# Verify a live Poravia Web deployment. Every check must pass before the
# release can be called delivered.
set -uo pipefail

BASE="${1:?usage: verify-deployment.sh https://host}"
HOST="${BASE#https://}"; HOST="${HOST#http://}"; HOST="${HOST%%/*}"
fail=0
ok()   { printf '  PASS  %s\n' "$1"; }
bad()  { printf '  FAIL  %s\n' "$1"; fail=1; }

echo "Verifying $BASE"

echo "DNS"
if dig +short "$HOST" | grep -q .; then ok "$HOST resolves: $(dig +short "$HOST" | tr '\n' ' ')"
else bad "$HOST does not resolve"; echo; echo "Blocked: the DNS record does not exist."; exit 1; fi

echo "HTTPS"
code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 20 "$BASE/" || echo 000)
[ "$code" = "200" ] && ok "homepage returns 200" || bad "homepage returned $code"
curl -sSI --max-time 20 "$BASE/" >/dev/null 2>&1 && ok "valid certificate" || bad "TLS failed"

echo "HTTP to HTTPS"
redir=$(curl -sS -o /dev/null -w '%{http_code} %{redirect_url}' --max-time 20 "http://$HOST/" || echo "000")
case "$redir" in 30*https://*) ok "http redirects to https ($redir)";; 200*) ok "http served (proxy terminates TLS)";; *) bad "http behaviour: $redir";; esac

echo "Content"
body=$(curl -sS --max-time 20 "$BASE/" || true)
grep -qi 'poravia' <<<"$body" && ok "homepage carries the Poravia identity" || bad "homepage does not mention Poravia"
grep -qiE 'hodomap|<newname>' <<<"$body" && bad "homepage still carries an old-brand or placeholder string" || ok "no old-brand or placeholder string"

echo "Release identity"
manifest=$(curl -sS --max-time 20 "$BASE/data/manifest.json" || true)
if grep -q '"releaseId"' <<<"$manifest"; then
  ok "release manifest served: $(sed -n 's/.*"releaseId": *"\([^"]*\)".*/\1/p' <<<"$manifest" | head -1)"
else bad "release manifest missing at $BASE/data/manifest.json"; fi

echo "Deep links and reload"
for path in /search /operators /settings; do
  code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 20 "$BASE$path" || echo 000)
  [ "$code" = "200" ] && ok "$path loads directly ($code)" || bad "$path returned $code"
done

echo "Static assets and PWA"
for path in /manifest.webmanifest /robots.txt; do
  code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 20 "$BASE$path" || echo 000)
  [ "$code" = "200" ] && ok "$path served" || bad "$path returned $code"
done

echo
if [ "$fail" -eq 0 ]; then echo "Deployment verified."; else echo "Deployment verification FAILED."; fi
exit "$fail"
