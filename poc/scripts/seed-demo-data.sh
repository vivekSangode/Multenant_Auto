#!/usr/bin/env bash
# Demo data for testing multi-tenancy: three car companies, each with its OWN catalog, stock, bookings and users,
# loaded through the real APIs (so every row lands in that company's own cmp_<n>_<module>_db).
#
#   Velocity Motors  AUTO_DEALER bundle   3 models, 6 cars, 3 bookings (delivered / booked / cancelled)
#   Apex Autos       AUTO_DEALER bundle   3 models (one with the SAME code as Velocity, other price), 5 cars, 2 bookings
#   Zenith Cars      vehicle + inventory  2 models, 4 cars, NO booking module (booking API -> MODULE_NOT_ENTITLED)
#
# Users per company (realm co-<n>):  owner@…  COMPANY_SUPER_ADMIN
#                                    sales@…  VEHICLE_USER + INVENTORY_USER (+ BOOKING_USER where bought)
#                                    viewer@… VEHICLE_USER only (inventory / booking -> MODULE_ACCESS_DENIED)
#
#   bash scripts/seed-demo-data.sh                 # against docker compose + README ports
#   CP=http://localhost:18080 KC=… bash scripts/seed-demo-data.sh
# Every run onboards three NEW companies (new CMP_n codes), so it can be run any number of times.
set -uo pipefail

CP=${CP:-http://localhost:8080}
KC=${KC:-http://localhost:8180}
VEHICLE=${VEHICLE:-http://localhost:8091}
INVENTORY=${INVENTORY:-http://localhost:8092}
BOOKING=${BOOKING:-http://localhost:8093}
MYSQL_CONTAINER=${MYSQL_CONTAINER:-erp-poc-mysql}
RUN=$(date +%s)
PASSWORD=${DEMO_PASSWORD:-Demo-Pass-1}

STATUS=""; BODY=""
api() { # METHOD URL TOKEN [json]
  local -a a=(-s -w '\n%{http_code}' -X "$1" "$2")
  [ -n "${3:-}" ] && a+=(-H "Authorization: Bearer $3")
  [ -n "${4:-}" ] && a+=(-H "Content-Type: application/json" -d "$4")
  local out; out=$(curl "${a[@]}"); STATUS=${out##*$'\n'}; BODY=${out%$'\n'*}; [ "$BODY" = "$STATUS" ] && BODY=""
}
must() { # expected-status description
  if [ "$STATUS" != "$1" ]; then printf '  !! %s: expected HTTP %s, got %s %s\n' "$2" "$1" "$STATUS" "$BODY"; exit 1; fi
}
field() { printf '%s' "$BODY" | grep -o "\"$1\":\"\?[^\",}]*" | head -1 | sed -E "s/\"$1\":\"?//"; }
kc_token() { curl -s "$KC/realms/$1/protocol/openid-connect/token" -d grant_type=password -d client_id="$2" \
  -d username="$3" -d password="$4" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4; }
platform() { PT=$(kc_token erp-platform platform-console superadmin change-me); }
operator() { OT=$(kc_token master admin-cli admin admin); }

# onboard NAME EMAIL_DOMAIN ORDER_JSON -> sets CODE REALM OWNER_TOKEN
onboard() {
  local name=$1 domain=$2 order=$3
  platform
  api POST "$CP/api/v1/platform/companies" "$PT" "{\"legalName\":\"$name\",\"superAdminEmail\":\"owner@$domain\"}"; must 201 "create $name"
  CODE=$(field companyCode); REALM=$(field realm); LOGIN=$(field loginCode)
  api POST "$CP/api/v1/platform/companies/$CODE/orders" "$PT" "$order"; must 201 "quote $name"
  local id total; id=$(field id); total=$(field grandTotal)
  api POST "$CP/api/v1/platform/orders/$id/confirm" "$PT"; must 200 "confirm $name"
  api POST "$CP/api/v1/platform/orders/$id/payments" "$PT" "{\"mode\":\"UPI\",\"amount\":$total}"; must 201 "pay $name"
  api POST "$CP/api/v1/platform/companies/$CODE/provision" "$PT"; must 202 "provision $name"
  for _ in $(seq 1 60); do
    api GET "$CP/api/v1/platform/companies/$CODE" "$PT"; [ "$(field status)" = ACTIVE ] && break; sleep 2
  done
  [ "$(field status)" = ACTIVE ] || { echo "  !! $name did not become ACTIVE"; exit 1; }
  # the invited Company Super Admin accepts the invitation (no SMTP in the POC)
  operator
  api GET "$KC/admin/realms/$REALM/users?email=owner@$domain&exact=true" "$OT"; must 200 "find owner"
  local user uid; user=$(printf '%s' "$BODY" | sed -E 's/^\[//; s/\]$//'); uid=$(printf '%s' "$user" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
  api PUT "$KC/admin/realms/$REALM/users/$uid/reset-password" "$OT" "{\"type\":\"password\",\"value\":\"$PASSWORD\",\"temporary\":false}"; must 204 "owner password"
  api PUT "$KC/admin/realms/$REALM/users/$uid" "$OT" \
    "$(printf '%s' "$user" | sed -E 's/"requiredActions":\[[^]]*\]/"requiredActions":[]/; s/"emailVerified":false/"emailVerified":true/')"; must 204 "owner accept"
  OWNER_TOKEN=$(kc_token "$REALM" erp-web "owner@$domain" "$PASSWORD")
  printf '  %s = %s (realm %s, login code %s)\n' "$name" "$CODE" "$REALM" "$LOGIN"
}

# brand CODE PRIMARY ACCENT MODE FONT LOGO_TEXT TAGLINE: the company portal's white-label look
brand() {
  platform
  api PUT "$CP/api/v1/platform/companies/$1/branding" "$PT" \
    "{\"primaryColor\":\"$2\",\"accentColor\":\"$3\",\"themeMode\":\"$4\",\"fontStyle\":\"$5\",\"logoText\":\"$6\",\"tagline\":\"$7\"}"
  must 200 "branding $1"
  printf '    branding %s / %s, %s, %s\n' "$2" "$3" "$4" "$5"
}

# add_user REALM EMAIL ROLE... (roles that do not exist in the realm, i.e. modules not bought, are skipped)
add_user() {
  local realm=$1 email=$2; shift 2
  operator
  api POST "$KC/admin/realms/$realm/users" "$OT" "{\"username\":\"$email\",\"email\":\"$email\",\"firstName\":\"Demo\",\"lastName\":\"User\",\"enabled\":true,\"emailVerified\":true,\"credentials\":[{\"type\":\"password\",\"value\":\"$PASSWORD\",\"temporary\":false}]}"
  must 201 "create user $email"
  api GET "$KC/admin/realms/$realm/users?email=$email&exact=true" "$OT"
  local uid; uid=$(printf '%s' "$BODY" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
  local granted=()
  for role in "$@"; do
    api GET "$KC/admin/realms/$realm/roles/$role" "$OT"
    [ "$STATUS" = 200 ] || continue
    local rep=$BODY
    api POST "$KC/admin/realms/$realm/users/$uid/role-mappings/realm" "$OT" "[$rep]"; must 204 "grant $role"
    granted+=("$role")
  done
  printf '    user %-24s %s\n' "$email" "${granted[*]}"
}

model() { # token code name body fuel price
  api POST "$VEHICLE/api/v1/vehicle/models" "$1" \
    "{\"modelCode\":\"$2\",\"name\":\"$3\",\"bodyType\":\"$4\",\"fuelType\":\"$5\",\"basePrice\":$6}"; must 201 "model $2"
}
SEQ=0
cars() { # token letter model color count
  for _ in $(seq 1 "$5"); do
    SEQ=$((SEQ + 1))
    local vin; vin=$(printf 'MA%s%s%04d' "$2" "${RUN: -10}" "$SEQ")
    api POST "$INVENTORY/api/v1/inventory/units" "$1" "{\"vin\":\"$vin\",\"modelCode\":\"$3\",\"color\":\"$4\"}"; must 201 "car $vin"
  done
}
book() { # token customer phone model color [deliver|cancel]
  api POST "$BOOKING/api/v1/booking/bookings" "$1" \
    "{\"customerName\":\"$2\",\"customerPhone\":\"$3\",\"modelCode\":\"$4\",\"color\":\"$5\"}"; must 201 "booking $2"
  local no; no=$(field bookingNo)
  case "${6:-}" in
    deliver) api POST "$BOOKING/api/v1/booking/bookings/$no/deliver" "$1"; must 200 "deliver $no" ;;
    cancel)  api POST "$BOOKING/api/v1/booking/bookings/$no/cancel" "$1"; must 200 "cancel $no" ;;
  esac
}

echo "== Onboarding three car companies"
onboard "Velocity Motors Pvt Ltd" velocity.test '{"billingPeriod":"MONTHLY","bundles":["AUTO_DEALER"],"users":10,"storageGb":20}'
V_CODE=$CODE; V_REALM=$REALM; V_TOKEN=$OWNER_TOKEN; V_LOGIN=$LOGIN
brand "$V_CODE" "#B91C1C" "#F59E0B" LIGHT MODERN VM "Feel the drive."
add_user "$V_REALM" sales@velocity.test VEHICLE_USER INVENTORY_USER BOOKING_USER
add_user "$V_REALM" viewer@velocity.test VEHICLE_USER

onboard "Apex Autos LLP" apex.test '{"billingPeriod":"YEARLY","bundles":["AUTO_DEALER"],"users":5,"storageGb":10}'
A_CODE=$CODE; A_REALM=$REALM; A_TOKEN=$OWNER_TOKEN; A_LOGIN=$LOGIN
brand "$A_CODE" "#1E3A8A" "#22D3EE" DARK TECHNICAL AA "Built tough. Priced right."
add_user "$A_REALM" sales@apex.test VEHICLE_USER INVENTORY_USER BOOKING_USER
add_user "$A_REALM" viewer@apex.test VEHICLE_USER

onboard "Zenith Cars Ltd" zenith.test '{"billingPeriod":"MONTHLY","modules":["vehicle","inventory"],"users":5,"storageGb":10}'
Z_CODE=$CODE; Z_REALM=$REALM; Z_TOKEN=$OWNER_TOKEN; Z_LOGIN=$LOGIN
brand "$Z_CODE" "#15803D" "#EAB308" LIGHT ROUNDED ZC "Family cars, fairly priced."
add_user "$Z_REALM" sales@zenith.test VEHICLE_USER INVENTORY_USER BOOKING_USER
add_user "$Z_REALM" viewer@zenith.test VEHICLE_USER

echo "== Loading business data (each company only through its own token)"
model "$V_TOKEN" VX-SUV   "Velocity VX SUV"      SUV       DIESEL   1450000
model "$V_TOKEN" VX-EV    "Velocity VX Electric" HATCHBACK ELECTRIC 1150000
model "$V_TOKEN" VX-SEDAN "Velocity VX Sedan"    SEDAN     PETROL   990000
cars  "$V_TOKEN" V VX-SUV Red 2; cars "$V_TOKEN" V VX-SUV Black 1; cars "$V_TOKEN" V VX-EV White 2; cars "$V_TOKEN" V VX-SEDAN Silver 1
book  "$V_TOKEN" "Ravi Kumar"  9800000001 VX-SUV   Red    deliver
book  "$V_TOKEN" "Anita Rao"   9800000002 VX-EV    White
book  "$V_TOKEN" "Sameer Khan" 9800000003 VX-SEDAN Silver cancel
echo "  Velocity Motors: 3 models, 6 cars, 3 bookings"

model "$A_TOKEN" AP-SEDAN  "Apex Sedan"           SEDAN  PETROL 980000
model "$A_TOKEN" AP-PICKUP "Apex Pickup"          PICKUP DIESEL 1250000
model "$A_TOKEN" VX-SUV    "Apex's own VX-SUV"    SUV    PETROL 1999999
cars  "$A_TOKEN" A AP-SEDAN White 2; cars "$A_TOKEN" A AP-PICKUP Blue 2; cars "$A_TOKEN" A VX-SUV Grey 1
book  "$A_TOKEN" "Meera Shah"   9800000004 AP-SEDAN  White
book  "$A_TOKEN" "John D'souza" 9800000005 AP-PICKUP Blue deliver
echo "  Apex Autos: 3 models (VX-SUV at a different price than Velocity's), 5 cars, 2 bookings"

model "$Z_TOKEN" ZN-MUV "Zenith Family MUV" MUV CNG    870000
model "$Z_TOKEN" ZN-HYB "Zenith Hybrid SUV" SUV HYBRID 1650000
cars  "$Z_TOKEN" Z ZN-MUV Green 3; cars "$Z_TOKEN" Z ZN-HYB Pearl 1
echo "  Zenith Cars: 2 models, 4 cars, booking module not bought"

count() { # url token -> number of items or error code
  api GET "$1" "$2"
  if [ "$STATUS" = 200 ]; then printf '%s' "$BODY" | grep -o '"id":' | wc -l | tr -d ' '; else printf '%s %s' "$STATUS" "$(field code)"; fi
}

echo
echo "== Same APIs, different tenants (counts as each company's owner sees them)"
printf '  %-16s %-9s %-8s %-8s %s\n' Company Code Models Cars Bookings
for c in V A Z; do
  eval "code=\$${c}_CODE tok=\$${c}_TOKEN"
  case $c in V) n="Velocity Motors";; A) n="Apex Autos";; Z) n="Zenith Cars";; esac
  printf '  %-16s %-9s %-8s %-8s %s\n' "$n" "$code" "$(count "$VEHICLE/api/v1/vehicle/models" "$tok")" \
    "$(count "$INVENTORY/api/v1/inventory/units" "$tok")" "$(count "$BOOKING/api/v1/booking/bookings" "$tok")"
done

echo
echo "== Roles inside one company (Velocity Motors)"
SALES=$(kc_token "$V_REALM" erp-web sales@velocity.test "$PASSWORD"); VIEWER=$(kc_token "$V_REALM" erp-web viewer@velocity.test "$PASSWORD")
printf '  sales@velocity.test   bookings: %s\n' "$(count "$BOOKING/api/v1/booking/bookings" "$SALES")"
printf '  viewer@velocity.test  models: %s, cars: %s, bookings: %s\n' "$(count "$VEHICLE/api/v1/vehicle/models" "$VIEWER")" \
  "$(count "$INVENTORY/api/v1/inventory/units" "$VIEWER")" "$(count "$BOOKING/api/v1/booking/bookings" "$VIEWER")"

if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -qx "$MYSQL_CONTAINER"; then
  echo
  echo "== Physical isolation: rows per company database (MySQL container $MYSQL_CONTAINER)"
  for c in V A Z; do
    eval "code=\$${c}_CODE"; n=$(printf '%s' "$code" | tr 'A-Z' 'a-z')
    for t in vehicle.vehicle_model inventory.stock_unit booking.vehicle_booking; do
      db="${n}_${t%%.*}_db"; table=${t#*.}
      rows=$(docker exec "$MYSQL_CONTAINER" mysql -uerp_app -perp_app_pass -N -e "SELECT COUNT(*) FROM \`$db\`.$table" 2>/dev/null || echo "no such database")
      printf '  %-28s %s\n' "$db.$table" "$rows"
    done
  done
fi

cat <<EOF

== Logins (password for every user: $PASSWORD)
  Velocity Motors  $V_CODE  login code $V_LOGIN  realm $V_REALM  owner@velocity.test  sales@velocity.test  viewer@velocity.test
  Apex Autos       $A_CODE  login code $A_LOGIN  realm $A_REALM  owner@apex.test      sales@apex.test      viewer@apex.test
  Zenith Cars      $Z_CODE  login code $Z_LOGIN  realm $Z_REALM  owner@zenith.test    sales@zenith.test    viewer@zenith.test

Try it: get a token for any user, then call the same URL with each company's token.
  TOKEN=\$(curl -s $KC/realms/$A_REALM/protocol/openid-connect/token -d grant_type=password -d client_id=erp-web -d username=owner@apex.test -d password=$PASSWORD | sed 's/.*"access_token":"\\([^"]*\\)".*/\\1/')
  curl -s -H "Authorization: Bearer \$TOKEN" $VEHICLE/api/v1/vehicle/models
EOF
