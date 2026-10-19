#!/usr/bin/env bash
# Runs the whole demo against a running stack (docker compose up -d --build) through the frontend's nginx,
# exactly the way the browser does, and prints OK / FAIL per step. Needs curl and python.
#   bash scripts/smoke-test.sh            (frontend on localhost:4200, Mailpit on localhost:8025)
set -u
signin() {
  local id="$1"; local gw="${2:-localhost:4200}"
  curl -s -X POST $gw/api/v1/auth/magic-link -H 'Content-Type: application/json' -d "{\"identifier\":\"$id\"}" > /dev/null
  sleep 1
  local enc=$(python -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1], safe=''))" "$id")
  local token=$(curl -s "localhost:8025/api/v1/search?query=to:%22$enc%22&limit=1" | python -c "import sys,json,re; d=json.load(sys.stdin); print(re.search(r'token=([^&\s]+)', d['messages'][0]['Snippet']).group(1))")
  curl -s -X POST $gw/api/v1/auth/login -H 'Content-Type: application/json' -d "{\"identifier\":\"$id\",\"token\":\"$token\"}" | python -c "import sys,json; print(json.load(sys.stdin)['accessToken'])"
}

GW=localhost:4200
j() { python -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
ok() { echo "OK   $*"; }; bad() { echo "FAIL $*"; FAILS=$((FAILS+1)); }
FAILS=0

# 1. sign up + sign in with the welcome link
ID="+35569$((1000000 + RANDOM))"
curl -s -X POST $GW/api/v1/auth/register -H 'Content-Type: application/json' -d "{\"displayName\":\"Fresh User\",\"identifier\":\"$ID\",\"accountType\":\"PERSONAL\"}" >/dev/null
sleep 1
ENC=$(python -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1], safe=''))" "$ID")
LINK=$(curl -s "localhost:8025/api/v1/search?query=to:%22$ENC@sms.wallet.local%22&limit=1" | j "d['messages'][0]['Snippet']")
TOKEN=$(echo "$LINK" | python -c "import sys,re; print(re.search(r'token=([^&\s]+)', sys.stdin.read()).group(1))")
NEW=$(curl -s -X POST $GW/api/v1/auth/login -H 'Content-Type: application/json' -d "{\"identifier\":\"$ID\",\"token\":\"$TOKEN\"}" | j "d['accessToken']")
B=$(curl -s $GW/api/v1/wallet/balance -H "Authorization: Bearer $NEW" | j "d['availableBalance']")
[ "$B" = "0.0" ] && ok "sign up by phone + welcome link -> wallet 0.00" || bad "sign up ($B)"

ANA=$(signin ana@wallet.local $GW); ELIRA=$(signin elira@wallet.local $GW)
A="Authorization: Bearer $ANA"; E="Authorization: Bearer $ELIRA"

# 2. reconcile (RECONCILED on a fresh stack and after every complete run of this script)
R=$(curl -s -X POST $GW/api/v1/admin/ledger/reconcile -H "$E" | j "d['status']")
[ "$R" = "RECONCILED" ] && ok "reconciliation RECONCILED" || bad "reconcile ($R)"

# 3. pay (all balance checks compare with Ana's balance at the start, so the script can run again and again)
bal() { curl -s $GW/api/v1/wallet/balance -H "$A" | j "d['availableBalance']"; }
plus() { python -c "import sys; print(float(sys.argv[1]) + float(sys.argv[2]))" "$1" "$2"; }
START=$(bal)
P=$(curl -s -X POST $GW/api/v1/transactions/pay -H "$A" -H "Idempotency-Key: s-$RANDOM" -H 'Content-Type: application/json' -d '{"recipientId":"00000000-0000-7000-8000-000000000003","amount":25.00,"currency":"EUR","transactionType":"P2P"}')
PAYID=$(echo "$P" | j "d['transactionId']"); PB=$(echo "$P" | j "d['availableBalance']")
[ "$PB" = "$(plus $START -25)" ] && ok "Ana pays Besi 25 -> $PB" || bad "pay ($P)"

W=$(curl -s $GW/api/v1/wallet/balance -H "$A" | j "d['walletId']")
transfer() { curl -s -X POST $GW/api/v1/bank/transfer -H "$A" -H "Idempotency-Key: t-$RANDOM$RANDOM" -H 'Content-Type: application/json' -d "{\"sourceWalletId\":\"$W\",\"bankRoutingNumber\":\"021000021\",\"externalAccountNumber\":\"123456789\",\"transferDirection\":\"OUTFLOW\",\"amount\":$1,\"currency\":\"EUR\"}" | j "d['trackingId']"; }
status() { curl -s $GW/api/v1/bank/transfers -H "$A" | j "[t['status'] for t in d if t['trackingId']=='$1'][0]"; }
mode() { curl -s -X PUT $GW/api/v1/admin/mock-bank/settings -H "$E" -H 'Content-Type: application/json' -d "{\"mode\":\"$1\",\"settlementDelaySeconds\":5}" >/dev/null; }
batch() { curl -s -X POST $GW/api/v1/admin/banking/batches/run -H "$E" >/dev/null; }

# 4. withdraw with the bank offline, then retry
mode OFFLINE; T1=$(transfer 100.00); batch
S1=$(status $T1); [ "$S1" = "PENDING_RETRY" ] && ok "bank offline -> PENDING_RETRY" || bad "offline ($S1)"
mode NORMAL
for i in $(seq 1 30); do S1=$(status $T1); [ "$S1" = "SETTLED" ] && break; sleep 3; done
[ "$S1" = "SETTLED" ] && ok "retry job resent it -> SETTLED" || bad "retry ($S1)"

# 5. rejected withdrawal -> money back
mode REJECT_NEXT; T2=$(transfer 50.00); batch
S2=$(status $T2); AB=$(bal)
[ "$S2" = "REJECTED" ] && [ "$AB" = "$(plus $START -125)" ] && ok "REJECT_NEXT -> REJECTED, the 50 came back ($AB)" || bad "reject ($S2 $AB)"

# 6. sweep, reconcile
SW=$(curl -s -X POST $GW/api/v1/admin/banking/sweep -H "$E" -H 'Content-Type: application/json' -d '{"reserveLimit":0}' | j "d.get('code', d.get('status'))")
[ "$SW" = "COMPLETED" ] || [ "$SW" = "NOTHING_TO_SWEEP" ] && ok "sweep $SW" || bad "sweep ($SW)"
R=$(curl -s -X POST $GW/api/v1/admin/ledger/reconcile -H "$E" | j "d['status']")
[ "$R" = "RECONCILED" ] && ok "reconcile after transfers + sweep RECONCILED" || bad "reconcile 2 ($R)"

# 7. inject 0.01 -> frozen + email, withdrawal refused, unfreeze
curl -s -X POST $GW/api/v1/admin/mock-bank/inject-discrepancy -H "$E" >/dev/null
R=$(curl -s -X POST $GW/api/v1/admin/ledger/reconcile -H "$E" | j "d['status'] + ' ' + str(d['outgoingFrozen'])")
[ "$R" = "MISMATCHED True" ] && ok "inject 0.01 -> MISMATCHED, frozen" || bad "mismatch ($R)"
sleep 1; M=$(curl -s 'localhost:8025/api/v1/search?query=to:ops@wallet.local&limit=1' | j "d['messages'][0]['Subject']")
[[ "$M" == ALERT* ]] && ok "alert email in Mailpit" || bad "alert ($M)"
C=$(curl -s -X POST $GW/api/v1/bank/transfer -H "$A" -H "Idempotency-Key: f-$RANDOM" -H 'Content-Type: application/json' -d "{\"sourceWalletId\":\"$W\",\"bankRoutingNumber\":\"021000021\",\"externalAccountNumber\":\"123456789\",\"transferDirection\":\"OUTFLOW\",\"amount\":1.00,\"currency\":\"EUR\"}" | j "d['code']")
[ "$C" = "SYSTEM_FROZEN" ] && ok "withdrawal while frozen -> SYSTEM_FROZEN" || bad "frozen withdrawal ($C)"
U=$(curl -s -X POST $GW/api/v1/admin/system/unfreeze -H "$E" | j "d['outgoingFrozen']")
[ "$U" = "False" ] && ok "unfreeze" || bad "unfreeze ($U)"
curl -s -X POST $GW/api/v1/admin/mock-bank/inject-discrepancy -H "$E" -H 'Content-Type: application/json' -d '{"amount":-0.01}' >/dev/null

# 8. reversal
RV=$(curl -s -X POST $GW/api/v1/admin/transactions/$PAYID/reverse -H "$E" -H 'Content-Type: application/json' -d '{"reason":"smoke test"}' | j "d['amount']")
AB=$(bal)
[ "$RV" = "25.0" ] && [ "$AB" = "$(plus $START -100)" ] && ok "reversal gave Ana her 25 back ($AB)" || bad "reversal ($RV $AB)"

# 9. customer cannot reach admin
CODE=$(curl -s -o /dev/null -w "%{http_code}" $GW/api/v1/admin/metrics -H "$A")
[ "$CODE" = "403" ] && ok "customer on admin -> 403" || bad "admin guard ($CODE)"

echo "FAILURES: $FAILS"
