#!/usr/bin/env bash
# Prueba de humo del flujo completo. Uso: scripts/smoke.sh [http://localhost:8080]
set -euo pipefail
HOST="${1:-http://localhost:8080}"
J='Content-Type: application/json'

say() { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
expect() { [ "$1" = "$2" ] && echo "   ok ($1)" || { echo "   FALLO: esperaba $2 y obtuve $1"; exit 1; }; }

say "1. Salud"
expect "$(code "$HOST/actuator/health")" 200

say "2. Ruta A* (0,0) -> (11,7)"
curl -s "$HOST/api/routes?toX=11&toY=7" | head -c 300; echo

say "3. Ruta a celda bloqueada -> 422"
expect "$(code "$HOST/api/routes?toX=1&toY=1")" 422

say "4. Crear pedido -> 201"
expect "$(code -X POST -H "$J" -d '{"customer":"Smoke Test","destX":10,"destY":5}' "$HOST/api/orders")" 201

say "5. Validación -> 400"
expect "$(code -X POST -H "$J" -d '{"customer":"","destX":1,"destY":0}' "$HOST/api/orders")" 400

say "6. Despachar pedido 1 -> 200; repetir -> 409"
expect "$(code -X POST "$HOST/api/orders/1/dispatch")" 200
expect "$(code -X POST "$HOST/api/orders/1/dispatch")" 409

say "7. Saltar estados (pedido 2: CREATED -> DELIVERED) -> 409"
expect "$(code -X POST -H "$J" -d '{"to":"DELIVERED"}' "$HOST/api/orders/2/transitions")" 409

say "8. Estado del pedido 1 (en modo docker pasa a IN_TRANSIT por RabbitMQ)"
sleep 1; curl -s "$HOST/api/orders/1" | head -c 400; echo

say "9. Entregar pedido 1 (si ya está IN_TRANSIT)"
code -X POST -H "$J" -d '{"to":"DELIVERED"}' "$HOST/api/orders/1/transitions"; echo

say "10. Métricas del laboratorio"
curl -s "$HOST/actuator/prometheus" | grep -E '^logistpulse_' | head -20

echo; echo "Smoke test terminado."
