#!/usr/bin/env bash
# Verify a live Odivrelo Web deployment. Every check must pass before the
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
grep -qi 'odivrelo' <<<"$body" && ok "homepage carries the Odivrelo identity" || bad "homepage does not mention Odivrelo"
grep -qiE 'hodomap|poravia|dromiqo|<newname>' <<<"$body" && bad "homepage still carries an old-brand or placeholder string" || ok "no old-brand or placeholder string"

echo "Release identity"
manifest=$(curl -sS --max-time 20 "$BASE/data/manifest.json" || true)
if grep -q '"releaseId"' <<<"$manifest"; then
  ok "release manifest served: $(sed -n 's/.*"releaseId": *"\([^"]*\)".*/\1/p' <<<"$manifest" | head -1)"
else bad "release manifest missing at $BASE/data/manifest.json"; fi

echo "Deep links and reload"
# A prerendered route must return a real 200. Static hosting that falls back to
# 404.html would render the app but report 404, which breaks link previews and
# crawlers, so the status code is checked, not just the body.
for path in /search /operators /settings /offline /saved /coverage; do
  code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 20 "$BASE$path" || echo 000)
  [ "$code" = "200" ] && ok "$path loads directly ($code)" || bad "$path returned $code, expected 200"
done

# Per-page metadata, not the generic shell repeated.
for path in /search /operators; do
  title=$(curl -sS --max-time 20 "$BASE$path" | sed -n 's/.*<title>\([^<]*\)<\/title>.*/\1/p' | head -1)
  [ -n "$title" ] && ok "$path has a title: $title" || bad "$path has no title"
done

echo "Unknown paths"
# An unknown path SHOULD be a 404, and should still render the app shell so the
# visitor gets a useful screen rather than a blank page.
unknown="$BASE/this-route-does-not-exist-$$"
code=$(curl -sS -o /tmp/unknown.$$ -w '%{http_code}' --max-time 20 "$unknown" || echo 000)
case "$code" in
  404) ok "unknown path returns 404" ;;
  *)   bad "unknown path returned $code, expected 404" ;;
esac
grep -qi 'id="root"\|odivrelo' /tmp/unknown.$$ 2>/dev/null \
  && ok "the 404 page still renders the app shell" \
  || bad "the 404 page is blank"
rm -f /tmp/unknown.$$

echo "Static assets and PWA"
for path in /manifest.webmanifest /robots.txt; do
  code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 20 "$BASE$path" || echo 000)
  [ "$code" = "200" ] && ok "$path served" || bad "$path returned $code"
done

echo
if [ "$fail" -eq 0 ]; then echo "Deployment verified."; else echo "Deployment verification FAILED."; fi
exit "$fail"
