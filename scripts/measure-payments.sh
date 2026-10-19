#!/usr/bin/env bash
# Measures payment latency through the API gateway (target: a payment answers within 200 ms).
# Needs a running stack (docker compose up -d --build), curl and python. Ana pays Besi 0.01, one payment at a time.
#   bash scripts/measure-payments.sh [count]      (default 200, gateway on localhost:8080, Mailpit on localhost:8025)
set -eu
COUNT="${1:-200}"
GW=localhost:8080

signin() {
  local id="$1"
  curl -s -X POST $GW/api/v1/auth/magic-link -H 'Content-Type: application/json' -d "{\"identifier\":\"$id\"}" > /dev/null
  sleep 1
  local token=$(curl -s "localhost:8025/api/v1/search?query=to:%22$id%22&limit=1" | python -c "import sys,json,re; d=json.load(sys.stdin); print(re.search(r'token=([^&\s]+)', d['messages'][0]['Snippet']).group(1))")
  curl -s -X POST $GW/api/v1/auth/login -H 'Content-Type: application/json' -d "{\"identifier\":\"$id\",\"token\":\"$token\"}" | python -c "import sys,json; print(json.load(sys.stdin)['accessToken'])"
}

ANA=$(signin ana@wallet.local)
BESI=$(curl -s "$GW/api/v1/users/lookup?identifier=besi@wallet.local" -H "Authorization: Bearer $ANA" | python -c "import sys,json; print(json.load(sys.stdin)['userId'])")

pay() {
  curl -s -o /dev/null -w '%{http_code} %{time_total}\n' -X POST $GW/api/v1/transactions/pay \
    -H "Authorization: Bearer $ANA" -H "Idempotency-Key: $(python -c 'import uuid; print(uuid.uuid4())')" \
    -H 'Content-Type: application/json' \
    -d "{\"recipientId\":\"$BESI\",\"amount\":0.01,\"currency\":\"EUR\",\"transactionType\":\"P2P\"}"
}

# Warm-up: the first calls load classes and fill connection pools; they are not counted.
for i in $(seq 1 20); do pay > /dev/null; done

RESULTS=$(for i in $(seq 1 "$COUNT"); do pay; done)
echo "$RESULTS" | python -c "
import sys
rows = [line.split() for line in sys.stdin if line.strip()]
failed = [r for r in rows if r[0] != '201']
ms = sorted(float(r[1]) * 1000 for r in rows if r[0] == '201')
def pct(p):
    return ms[min(len(ms) - 1, int(round(p / 100 * len(ms))) - 1)]
print(f'{len(ms)} payments OK, {len(failed)} failed')
print(f'p50 {pct(50):.1f} ms   p95 {pct(95):.1f} ms   p99 {pct(99):.1f} ms   max {ms[-1]:.1f} ms')
"
