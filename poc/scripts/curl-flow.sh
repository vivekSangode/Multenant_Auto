#!/usr/bin/env bash
# Every API of the POC, end to end, with curl only (no jq needed):
#   control plane (platform + tenant + internal) -> Keycloak -> vehicle / inventory / booking services.
# Onboards two car companies (Velocity Motors, Apex Autos) and drives the same service APIs with each company's
# token. Prints every request and its response. Safe to re-run: every run creates two new companies.
#
#   bash scripts/curl-flow.sh                      # default ports of docker compose + README
#   CP=http://localhost:18080 bash scripts/curl-flow.sh
#   FLOW_LOG=/tmp/flow.log bash scripts/curl-flow.sh   # also writes a machine-readable log of every call
set -uo pipefail

CP=${CP:-http://localhost:8080}
KC=${KC:-http://localhost:8180}
VEHICLE=${VEHICLE:-http://localhost:8091}
INVENTORY=${INVENTORY:-http://localhost:8092}
BOOKING=${BOOKING:-http://localhost:8093}
FLOW_LOG=${FLOW_LOG:-}
RUN=$(date +%s)                      # makes VINs unique per run
[ -n "$FLOW_LOG" ] && : > "$FLOW_LOG"

STATUS=""; BODY=""
HDRS=$(mktemp)
trap 'rm -f "$HDRS"' EXIT

pretty() { if command -v jq >/dev/null 2>&1 && [ -n "$1" ]; then printf '%s' "$1" | jq . 2>/dev/null || printf '%s\n' "$1"; else printf '%s\n' "$1"; fi; }

log() { # title, display command, status, body
  [ -z "$FLOW_LOG" ] && return
  { printf '@@STEP %s\n@@CMD %s\n@@STATUS %s\n@@BODY\n%s\n@@END\n' "$1" "$2" "$3" "$4"; } >> "$FLOW_LOG"
}

section() { printf '\n\033[1;34m######## %s ########\033[0m\n' "$1"; [ -n "$FLOW_LOG" ] && printf '@@SECTION %s\n' "$1" >> "$FLOW_LOG"; }

# call "Title" METHOD BASE_VAR /path TOKEN_VAR ['json body'] [extra header]
call() {
  local title=$1 method=$2 basevar=$3 path=$4 tokvar=$5 body=${6:-} extra=${7:-}
  local url="${!basevar}$path"
  local -a args=(-s -D "$HDRS" -w '\n%{http_code}' -X "$method" "$url")
  local shown="curl -X $method \"\$$basevar$path\""
  if [ -n "$tokvar" ]; then args+=(-H "Authorization: Bearer ${!tokvar}"); shown+=" -H \"Authorization: Bearer \$$tokvar\""; fi
  if [ -n "$extra" ]; then args+=(-H "$extra"); shown+=" -H \"$extra\""; fi
  if [ -n "$body" ]; then args+=(-H "Content-Type: application/json" -d "$body"); shown+=" -H \"Content-Type: application/json\" -d '$body'"; fi
  local out; out=$(curl "${args[@]}")
  STATUS=${out##*$'\n'}; BODY=${out%$'\n'*}
  [ "$BODY" = "$STATUS" ] && BODY=""
  if [ -z "$BODY" ]; then  # 401s carry the reason in a header, not a body
    local www; www=$(grep -i '^WWW-Authenticate:' "$HDRS" | tr -d '\r' | sed 's/^[^:]*: //')
    [ -n "$www" ] && BODY="(no body) WWW-Authenticate: $www"
  fi
  printf '\n\033[1m== %s\033[0m\n$ %s\n-> HTTP %s\n' "$title" "$shown" "$STATUS"; pretty "$BODY"
  log "$title" "$shown" "$STATUS" "$BODY"
}

# token "Title" REALM CLIENT USER PASSWORD_VAR -> sets TOKEN (password grant: POC stand-in for the browser login)
token() {
  local title=$1 realm=$2 client=$3 user=$4 pass=$5
  local out; out=$(curl -s -w '\n%{http_code}' "$KC/realms/$realm/protocol/openid-connect/token" \
    -d grant_type=password -d client_id="$client" -d username="$user" -d password="$pass")
  STATUS=${out##*$'\n'}; BODY=${out%$'\n'*}
  TOKEN=$(printf '%s' "$BODY" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
  local shown="curl -X POST \"\$KC/realms/$realm/protocol/openid-connect/token\" -d grant_type=password -d client_id=$client -d username=$user -d password=***"
  local shortBody; shortBody=$(printf '%s' "$BODY" | sed -E 's/"(access|refresh|id)_token":"([^"]{12})[^"]*"/"\1_token":"\2..."/g')
  printf '\n\033[1m== %s\033[0m\n$ %s\n-> HTTP %s\n' "$title" "$shown" "$STATUS"; pretty "$shortBody"
  log "$title" "$shown" "$STATUS" "$shortBody"
}

client_token() { # svc-* M2M token (client credentials)
  curl -s "$KC/realms/erp-platform/protocol/openid-connect/token" -d grant_type=client_credentials \
    -d client_id="$1" -d client_secret="$2" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4
}

claims() { # decoded JWT payload
  local p; p=$(printf '%s' "$1" | cut -d. -f2 | tr '_-' '/+'); while [ $(( ${#p} % 4 )) -ne 0 ]; do p="$p="; done
  printf '%s' "$p" | base64 -d 2>/dev/null
}

field() { printf '%s' "$BODY" | grep -o "\"$1\":\"\?[^\",}]*" | head -1 | sed -E "s/\"$1\":\"?//"; }

wait_active() { # company code
  for _ in $(seq 1 60); do
    curl -s -H "Authorization: Bearer $PLATFORM_TOKEN" "$CP/api/v1/platform/companies/$1" | grep -q '"status":"ACTIVE"' && return 0
    sleep 2
  done
  echo "!! $1 did not become ACTIVE"; return 1
}

operator_token() { OPERATOR_TOKEN=$(curl -s "$KC/realms/master/protocol/openid-connect/token" -d grant_type=password -d client_id=admin-cli -d username=admin -d password=admin | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4); }

# accept_invitation REALM EMAIL PASSWORD: what the invited Company Super Admin does via the invitation e-mail
# (no SMTP in the POC), done here with the operator's Keycloak admin API.
accept_invitation() {
  local realm=$1 email=$2 password=$3
  operator_token
  call "Keycloak admin: find the invited Company Super Admin in $realm" GET KC "/admin/realms/$realm/users?email=$email&exact=true" OPERATOR_TOKEN
  local user; user=$(printf '%s' "$BODY" | sed -E 's/^\[//; s/\]$//')
  local id; id=$(printf '%s' "$user" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
  call "Keycloak admin: set the password (invitation accepted)" PUT KC "/admin/realms/$realm/users/$id/reset-password" OPERATOR_TOKEN \
    "{\"type\":\"password\",\"value\":\"$password\",\"temporary\":false}"
  local accepted; accepted=$(printf '%s' "$user" | sed -E 's/"requiredActions":\[[^]]*\]/"requiredActions":[]/; s/"emailVerified":false/"emailVerified":true/')
  call "Keycloak admin: verify e-mail, clear required actions" PUT KC "/admin/realms/$realm/users/$id" OPERATOR_TOKEN "$accepted"
}

# ===================================================================================================================
section "0. Health"
for svc in CP VEHICLE INVENTORY BOOKING; do call "$svc health" GET $svc /actuator/health ""; done

section "1. Platform login + catalog"
token "Platform Super Admin token (realm erp-platform)" erp-platform platform-console superadmin change-me
PLATFORM_TOKEN=$TOKEN
call "Catalog with the monthly price list" GET CP "/api/v1/platform/catalog?period=MONTHLY" PLATFORM_TOKEN

section "2. Onboard Velocity Motors (AUTO_DEALER bundle)"
call "Create company" POST CP /api/v1/platform/companies PLATFORM_TOKEN \
  '{"legalName":"Velocity Motors Pvt Ltd","superAdminEmail":"owner@velocity.test"}'
A=$(field companyCode); REALM_A=$(field realm)
call "List companies" GET CP /api/v1/platform/companies PLATFORM_TOKEN
call "Get company" GET CP "/api/v1/platform/companies/$A" PLATFORM_TOKEN

call "Quote that will be cancelled" POST CP "/api/v1/platform/companies/$A/orders" PLATFORM_TOKEN \
  '{"billingPeriod":"MONTHLY","modules":["vehicle"],"users":5,"storageGb":10}'
CANCEL_ID=$(field id)
call "Cancel that quote" POST CP "/api/v1/platform/orders/$CANCEL_ID/cancel" PLATFORM_TOKEN '{"reason":"customer chose the bundle"}'

call "Quote AUTO_DEALER, 8 users, 10% launch discount" POST CP "/api/v1/platform/companies/$A/orders" PLATFORM_TOKEN \
  '{"billingPeriod":"MONTHLY","bundles":["AUTO_DEALER"],"users":8,"storageGb":10,"manualDiscount":{"type":"PERCENT","value":10,"reason":"launch offer"}}'
ORDER_A=$(field id); TOTAL_A=$(field grandTotal)
call "List the company's orders" GET CP "/api/v1/platform/companies/$A/orders" PLATFORM_TOKEN
call "Confirm the order (amounts frozen, subscription PENDING)" POST CP "/api/v1/platform/orders/$ORDER_A/confirm" PLATFORM_TOKEN
call "Provision before full payment -> 409 SUBSCRIPTION_NOT_ACTIVE" POST CP "/api/v1/platform/companies/$A/provision" PLATFORM_TOKEN
CASH=$(awk "BEGIN{printf \"%.2f\", $TOTAL_A/2}"); REST=$(awk "BEGIN{printf \"%.2f\", $TOTAL_A-$CASH}")
call "Record a cash payment (half)" POST CP "/api/v1/platform/orders/$ORDER_A/payments" PLATFORM_TOKEN "{\"mode\":\"CASH\",\"amount\":$CASH}"
call "Record a cheque for the rest (PENDING_VERIFICATION)" POST CP "/api/v1/platform/orders/$ORDER_A/payments" PLATFORM_TOKEN \
  "{\"mode\":\"CHEQUE\",\"amount\":$REST,\"instrumentRef\":\"CHQ-$RUN\",\"bankName\":\"SBI\"}"
CHEQUE_A=$(printf '%s' "$BODY" | grep -o '{"id":[0-9]*[^{}]*"paymentMode":"CHEQUE"' | tail -1 | grep -o '"id":[0-9]*' | cut -d: -f2)
call "Over-payment is refused -> 409" POST CP "/api/v1/platform/orders/$ORDER_A/payments" PLATFORM_TOKEN '{"mode":"UPI","amount":1}'
call "The cheque cleared" POST CP "/api/v1/platform/payments/$CHEQUE_A/verify" PLATFORM_TOKEN
call "Order: FULFILLED, invoice ISSUED_PAID" GET CP "/api/v1/platform/orders/$ORDER_A" PLATFORM_TOKEN

call "Provision: realm co-n + Company Super Admin, DB per module, Liquibase, buckets" POST CP "/api/v1/platform/companies/$A/provision" PLATFORM_TOKEN
wait_active "$A"
call "Provisioning job and its steps" GET CP "/api/v1/platform/companies/$A/provisioning-jobs" PLATFORM_TOKEN
JOB_A=$(field id)
call "Retry is only for FAILED jobs -> 409" POST CP "/api/v1/platform/provisioning-jobs/$JOB_A/retry" PLATFORM_TOKEN
call "Overview: subscription, entitlements, allowances, resources, Redis projection" GET CP "/api/v1/platform/companies/$A/overview" PLATFORM_TOKEN
call "Status history" GET CP "/api/v1/platform/companies/$A/status-history" PLATFORM_TOKEN

section "3. Velocity Motors' first login"
accept_invitation "$REALM_A" owner@velocity.test Velocity-Owner-1
token "Company token (realm $REALM_A)" "$REALM_A" erp-web owner@velocity.test Velocity-Owner-1
TOKEN_A=$TOKEN
printf '\nDecoded token claims:\n'; pretty "$(claims "$TOKEN_A")"
log "Decoded claims of the company token" "(base64url-decoded JWT payload)" "-" "$(claims "$TOKEN_A")"

section "4. Internal registry API (M2M, what the services call)"
SVC_TOKEN=$(client_token svc-vehicle svc-vehicle-secret)
ISS=$(claims "$TOKEN_A" | grep -o '"iss":"[^"]*"' | cut -d'"' -f4)
call "Which company is this issuer?" GET CP "/api/v1/internal/realms/company?issuer=$ISS" SVC_TOKEN
call "Which database serves (company, vehicle)?" GET CP "/api/v1/internal/companies/$A/modules/vehicle/database" SVC_TOKEN
call "A user token is refused on the internal API -> 401" GET CP "/api/v1/internal/companies/$A/modules/vehicle/database" TOKEN_A

section "5. Velocity Motors: vehicle-service"
call "Add model VX-SUV" POST VEHICLE /api/v1/vehicle/models TOKEN_A \
  '{"modelCode":"VX-SUV","name":"Velocity VX SUV","bodyType":"SUV","fuelType":"DIESEL","basePrice":1450000}'
call "Add model VX-EV" POST VEHICLE /api/v1/vehicle/models TOKEN_A \
  '{"modelCode":"VX-EV","name":"Velocity VX Electric","bodyType":"HATCHBACK","fuelType":"ELECTRIC","basePrice":1150000}'
call "Duplicate model -> 409 MODEL_EXISTS" POST VEHICLE /api/v1/vehicle/models TOKEN_A \
  '{"modelCode":"vx-suv","name":"Again","bodyType":"SUV","fuelType":"PETROL","basePrice":1}'
call "Missing / invalid fields -> 400 VALIDATION_FAILED" POST VEHICLE /api/v1/vehicle/models TOKEN_A '{"modelCode":"X","bodyType":"SUV"}'
call "Unknown enum value -> 400 MALFORMED_BODY" POST VEHICLE /api/v1/vehicle/models TOKEN_A '{"modelCode":"VX-TANK","name":"Tank","bodyType":"TANK","fuelType":"DIESEL","basePrice":1}'
call "List models" GET VEHICLE /api/v1/vehicle/models TOKEN_A
call "Get one model" GET VEHICLE /api/v1/vehicle/models/VX-SUV TOKEN_A
call "Unknown model -> 404 MODEL_NOT_FOUND" GET VEHICLE /api/v1/vehicle/models/NOPE TOKEN_A
call "Routing: which DB served Velocity" GET VEHICLE /api/v1/vehicle/_routing TOKEN_A

section "6. Velocity Motors: inventory-service"
VIN1="MA1VX$(printf '%012d' $(( RUN % 1000000000000 )))"; VIN1=${VIN1:0:16}1
VIN2=${VIN1:0:16}2
call "Receive car 1 (inventory asks vehicle-service if the model exists)" POST INVENTORY /api/v1/inventory/units TOKEN_A \
  "{\"vin\":\"$VIN1\",\"modelCode\":\"VX-SUV\",\"color\":\"Red\"}"
call "Receive car 2" POST INVENTORY /api/v1/inventory/units TOKEN_A "{\"vin\":\"$VIN2\",\"modelCode\":\"VX-SUV\",\"color\":\"Red\"}"
call "Same VIN again -> 409 VIN_EXISTS" POST INVENTORY /api/v1/inventory/units TOKEN_A "{\"vin\":\"$VIN2\",\"modelCode\":\"VX-SUV\",\"color\":\"Red\"}"
call "Model not in the catalog -> 422 UNKNOWN_MODEL" POST INVENTORY /api/v1/inventory/units TOKEN_A \
  '{"vin":"MA1XX0000XXX00009","modelCode":"NO-SUCH-CAR","color":"Red"}'
call "Reserve directly (normally booking-service does this)" POST INVENTORY /api/v1/inventory/reservations TOKEN_A \
  '{"reference":"MANUAL-1","modelCode":"VX-SUV","color":"red"}'
call "Release the reservation" DELETE INVENTORY /api/v1/inventory/reservations/MANUAL-1 TOKEN_A
call "List AVAILABLE VX-SUV" GET INVENTORY "/api/v1/inventory/units?modelCode=VX-SUV&status=AVAILABLE" TOKEN_A
call "Routing: which DB served Velocity" GET INVENTORY /api/v1/inventory/_routing TOKEN_A

section "7. Velocity Motors: booking-service"
call "Book a red VX-SUV (price from vehicle-service, car reserved in inventory-service)" POST BOOKING /api/v1/booking/bookings TOKEN_A \
  '{"customerName":"Ravi Kumar","customerPhone":"9800000001","modelCode":"VX-SUV","color":"Red"}'
BK1=$(field bookingNo)
call "Book the second red VX-SUV" POST BOOKING /api/v1/booking/bookings TOKEN_A \
  '{"customerName":"Anita Rao","customerPhone":"9800000003","modelCode":"VX-SUV","color":"Red"}'
BK2=$(field bookingNo)
call "No red VX-SUV left -> 409 OUT_OF_STOCK" POST BOOKING /api/v1/booking/bookings TOKEN_A \
  '{"customerName":"Late Customer","modelCode":"VX-SUV","color":"Red"}'
call "Get booking" GET BOOKING "/api/v1/booking/bookings/$BK1" TOKEN_A
call "Deliver booking 1 (car SOLD)" POST BOOKING "/api/v1/booking/bookings/$BK1/deliver" TOKEN_A
call "Cancel booking 2 (car AVAILABLE again)" POST BOOKING "/api/v1/booking/bookings/$BK2/cancel" TOKEN_A
call "Cancel a delivered booking -> 409 INVALID_BOOKING_STATE" POST BOOKING "/api/v1/booking/bookings/$BK1/cancel" TOKEN_A
call "List bookings" GET BOOKING /api/v1/booking/bookings TOKEN_A
call "Stock after: one SOLD, one AVAILABLE" GET INVENTORY "/api/v1/inventory/units?modelCode=VX-SUV" TOKEN_A
call "Routing: which DB served Velocity" GET BOOKING /api/v1/booking/_routing TOKEN_A
call "CP tenant API of a module Velocity did not buy -> 403 MODULE_NOT_ENTITLED" GET CP /api/v1/tenant/crm/customers TOKEN_A

section "8. Onboard Apex Autos (vehicle + inventory only; first cheque bounces)"
call "Create company" POST CP /api/v1/platform/companies PLATFORM_TOKEN '{"legalName":"Apex Autos LLP","superAdminEmail":"owner@apex.test"}'
B=$(field companyCode); REALM_B=$(field realm)
call "Quote vehicle + inventory" POST CP "/api/v1/platform/companies/$B/orders" PLATFORM_TOKEN \
  '{"billingPeriod":"MONTHLY","modules":["vehicle","inventory"],"users":5,"storageGb":10}'
ORDER_B=$(field id); TOTAL_B=$(field grandTotal)
call "Confirm" POST CP "/api/v1/platform/orders/$ORDER_B/confirm" PLATFORM_TOKEN
call "Cheque for the full amount" POST CP "/api/v1/platform/orders/$ORDER_B/payments" PLATFORM_TOKEN \
  "{\"mode\":\"CHEQUE\",\"amount\":$TOTAL_B,\"instrumentRef\":\"CHQ-B-$RUN\",\"bankName\":\"HDFC\"}"
CHEQUE_B=$(printf '%s' "$BODY" | grep -o '{"id":[0-9]*[^{}]*"paymentMode":"CHEQUE"' | tail -1 | grep -o '"id":[0-9]*' | cut -d: -f2)
call "The cheque bounced" POST CP "/api/v1/platform/payments/$CHEQUE_B/reject" PLATFORM_TOKEN '{"reason":"insufficient funds"}'
call "Pay by UPI instead (immediate)" POST CP "/api/v1/platform/orders/$ORDER_B/payments" PLATFORM_TOKEN "{\"mode\":\"UPI\",\"amount\":$TOTAL_B}"
call "Provision" POST CP "/api/v1/platform/companies/$B/provision" PLATFORM_TOKEN
wait_active "$B"
accept_invitation "$REALM_B" owner@apex.test Apex-Owner-1
token "Company token (realm $REALM_B)" "$REALM_B" erp-web owner@apex.test Apex-Owner-1
TOKEN_B=$TOKEN

section "9. Apex Autos: the SAME APIs, its own data"
call "Add model AP-SEDAN" POST VEHICLE /api/v1/vehicle/models TOKEN_B \
  '{"modelCode":"AP-SEDAN","name":"Apex Sedan","bodyType":"SEDAN","fuelType":"PETROL","basePrice":980000}'
call "Same code as Velocity is fine (separate catalog)" POST VEHICLE /api/v1/vehicle/models TOKEN_B \
  '{"modelCode":"VX-SUV","name":"Apex own VX-SUV","bodyType":"SUV","fuelType":"PETROL","basePrice":1999999}'
call "List models: only Apex's" GET VEHICLE /api/v1/vehicle/models TOKEN_B
call "Receive a car" POST INVENTORY /api/v1/inventory/units TOKEN_B "{\"vin\":\"MA3AP${VIN1:5:11}1\",\"modelCode\":\"AP-SEDAN\",\"color\":\"White\"}"
call "List stock: none of Velocity's cars" GET INVENTORY /api/v1/inventory/units TOKEN_B
call "Routing: Apex's own DB" GET VEHICLE /api/v1/vehicle/_routing TOKEN_B
call "Booking not bought -> 403 MODULE_NOT_ENTITLED" GET BOOKING /api/v1/booking/bookings TOKEN_B

section "10. Apex upgrades with booking (ADD_MODULE job, Apex stays ACTIVE)"
call "Quote booking (UPGRADE)" POST CP "/api/v1/platform/companies/$B/orders" PLATFORM_TOKEN \
  '{"billingPeriod":"MONTHLY","modules":["booking"],"users":5,"storageGb":10}'
ORDER_B2=$(field id); TOTAL_B2=$(field grandTotal)
call "Confirm" POST CP "/api/v1/platform/orders/$ORDER_B2/confirm" PLATFORM_TOKEN
call "Pay by bank transfer (needs verification)" POST CP "/api/v1/platform/orders/$ORDER_B2/payments" PLATFORM_TOKEN \
  "{\"mode\":\"BANK_TRANSFER\",\"amount\":$TOTAL_B2,\"instrumentRef\":\"UTR$RUN\"}"
PAY_B2=$(printf '%s' "$BODY" | grep -o '{"id":[0-9]*[^{}]*"paymentMode":"BANK_TRANSFER"' | tail -1 | grep -o '"id":[0-9]*' | cut -d: -f2)
call "Transfer received" POST CP "/api/v1/platform/payments/$PAY_B2/verify" PLATFORM_TOKEN
for _ in $(seq 1 60); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN_B" "$BOOKING/api/v1/booking/bookings")" = 200 ] && break; sleep 2
done
call "Booking now works for Apex" POST BOOKING /api/v1/booking/bookings TOKEN_B \
  '{"customerName":"Meera Shah","customerPhone":"9800000002","modelCode":"AP-SEDAN","color":"White"}'
call "Apex's bookings" GET BOOKING /api/v1/booking/bookings TOKEN_B

section "11. Guard rails"
GARBAGE="not-a-jwt"
call "No token -> 401" GET VEHICLE /api/v1/vehicle/models ""
call "Garbage token -> 401" GET VEHICLE /api/v1/vehicle/models GARBAGE
call "Platform token on a business API -> 401" GET VEHICLE /api/v1/vehicle/models PLATFORM_TOKEN
call "Company token on the platform API -> 401" GET CP /api/v1/platform/companies TOKEN_A
call "Spoofed X-Company-Code header is ignored" GET INVENTORY /api/v1/inventory/_routing TOKEN_A "X-Company-Code: $B"
call "Suspend Velocity Motors" POST CP "/api/v1/platform/companies/$A/status" PLATFORM_TOKEN '{"status":"SUSPENDED","reason":"payment overdue"}'
call "Velocity blocked -> 403 COMPANY_SUSPENDED" GET BOOKING /api/v1/booking/bookings TOKEN_A
call "Apex unaffected" GET BOOKING /api/v1/booking/bookings TOKEN_B
call "Reactivate Velocity Motors" POST CP "/api/v1/platform/companies/$A/status" PLATFORM_TOKEN '{"status":"ACTIVE","reason":"paid"}'
call "Velocity's data is intact" GET BOOKING /api/v1/booking/bookings TOKEN_A

printf '\nDone: Velocity Motors = %s (%s), Apex Autos = %s (%s)\n' "$A" "$REALM_A" "$B" "$REALM_B"
