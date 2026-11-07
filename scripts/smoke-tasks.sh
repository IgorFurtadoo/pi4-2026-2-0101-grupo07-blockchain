#!/usr/bin/env bash
# Smoke test do POST /transactions (CT10) e do GET /tasks/{taskId} (CT14) contra o
# compose de desenvolvimento. Nada vai à rede: este passo só grava e lê a task.
#
# Uso: scripts/smoke-tasks.sh  (com "docker compose up -d" já rodando)
#
# A SERVICE_KEY vem da variável exportada ou, se não houver, do ambiente do
# container. Ela nunca é impressa: vai para o curl pela entrada padrão (-H @-),
# para não aparecer na tela nem na lista de processos. Não use "bash -x" aqui.
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8081}"
cd "$(dirname "$0")/.."

if [ -z "${SERVICE_KEY:-}" ]; then
  SERVICE_KEY="$(docker compose exec -T blockchain-service printenv SERVICE_KEY)"
fi

BODY_FILE="$(mktemp)"
REQUEST_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE" "$REQUEST_FILE"' EXIT
FAILURES=0
BODY=""

check() {
  local label="$1" ok="$2" detail="$3"
  if [ "$ok" = "1" ]; then
    echo "OK    $label -> $detail"
  else
    echo "FALHA $label -> $detail"
    FAILURES=$((FAILURES + 1))
  fi
}

# request <rótulo> <método> <caminho> <status esperado> <regex do corpo> <cabeçalho X-Service-Key ou vazio> [corpo JSON]
# Deixa o corpo da resposta em $BODY.
request() {
  local label="$1" method="$2" path="$3" expected_status="$4" body_regex="$5" header="$6"
  local status
  local data=()
  if [ "$#" -ge 7 ]; then
    printf '%s' "$7" > "$REQUEST_FILE"
    data=(-H 'Content-Type: application/json' --data-binary @"$REQUEST_FILE")
  fi
  status="$(printf '%s\n' "$header" | curl -s -o "$BODY_FILE" -w '%{http_code}' \
    -X "$method" -H @- "${data[@]}" "$BASE_URL$path")"
  BODY="$(cat "$BODY_FILE")"

  local ok=0
  if [ "$status" = "$expected_status" ] && [[ "$BODY" =~ $body_regex ]]; then
    ok=1
  fi
  check "$label" "$ok" "$status $BODY (esperado $expected_status)"
}

KEY_HEADER="X-Service-Key: $SERVICE_KEY"
ADDRESS_WITHOUT_WALLET="0x0000000000000000000000000000000000000001"
DESTINATION="0x5B38Da6a701c568545dCfcB03FcB875f56beddC4"
UUID_REGEX='[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}'

error_regex() {
  printf '^\\{"code":"%s","message":"[^"]+"\\}$' "$1"
}

transaction_body() {
  printf '{"sourceAddress":"%s","destinationAddress":"%s","amountEth":%s}' "$1" "$2" "$3"
}

# ---------- erros do POST /transactions ----------

request "sem chave" POST "/transactions" \
  401 "$(error_regex SERVICE_KEY_INVALID)" "" \
  "$(transaction_body "$ADDRESS_WITHOUT_WALLET" "$DESTINATION" 0.01)"
request "endereço inválido" POST "/transactions" \
  400 "$(error_regex ADDRESS_INVALID)" "$KEY_HEADER" \
  "$(transaction_body 0x123 "$DESTINATION" 0.01)"
request "valor zero" POST "/transactions" \
  400 "$(error_regex AMOUNT_INVALID)" "$KEY_HEADER" \
  "$(transaction_body "$ADDRESS_WITHOUT_WALLET" "$DESTINATION" 0)"
request "JSON malformado" POST "/transactions" \
  400 "$(error_regex REQUEST_INVALID)" "$KEY_HEADER" \
  '{"sourceAddress":'
request "origem sem carteira" POST "/transactions" \
  404 "$(error_regex WALLET_NOT_FOUND)" "$KEY_HEADER" \
  "$(transaction_body "$ADDRESS_WITHOUT_WALLET" "$DESTINATION" 0.01)"

# ---------- caminho feliz: carteira nova → task PENDING ----------

request "criar carteira" POST "/wallets" \
  201 '^\{"ethereumAddress":"0x[0-9a-fA-F]{40}"\}$' "$KEY_HEADER"
SOURCE="$(printf '%s' "$BODY" | sed -E 's/.*"ethereumAddress":"(0x[0-9a-fA-F]{40})".*/\1/')"

request "pedido de envio" POST "/transactions" \
  202 "^\\{\"taskId\":\"$UUID_REGEX\",\"status\":\"PENDING\"\\}$" "$KEY_HEADER" \
  "$(transaction_body "$SOURCE" "$DESTINATION" 0.01)"
TASK_ID="$(printf '%s' "$BODY" | sed -E 's/.*"taskId":"([^"]+)".*/\1/')"

if [[ "$TASK_ID" =~ ^$UUID_REGEX$ ]]; then
  request "consultar task" GET "/tasks/$TASK_ID" \
    200 "^\\{\"taskId\":\"$TASK_ID\",\"status\":\"PENDING\",\"updatedAt\":\"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z\"\\}$" \
    "$KEY_HEADER"

  # O taskId só entra no SQL depois de casar com a regex de UUID acima.
  SQL="SELECT status, transaction_hash IS NULL FROM task WHERE task_id = '$TASK_ID'"
  ROW="$(docker compose exec -T -e SQL="$SQL" blockchain-db \
    sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -B -u "$MYSQL_USER" blockchain -e "$SQL"' | tr '\t' ' ')"
  ok=0
  [ "$ROW" = "PENDING 1" ] && ok=1
  check "linha no MySQL (status, hash nulo)" "$ok" "'$ROW' (esperado 'PENDING 1')"
else
  check "taskId da resposta" 0 "'$TASK_ID' não é um UUID v4"
fi

# ---------- erros do GET /tasks e rotas ----------

request "task inexistente" GET "/tasks/00000000-0000-4000-8000-000000000000" \
  404 "$(error_regex TASK_NOT_FOUND)" "$KEY_HEADER"
request "rota inexistente" GET "/nao-existe" \
  404 '.*' "$KEY_HEADER"
request "método errado" DELETE "/wallets" \
  405 '.*' "$KEY_HEADER"

if [ "$FAILURES" -gt 0 ]; then
  echo "$FAILURES verificação(ões) falharam."
  exit 1
fi
echo "Todas as verificações passaram."
