#!/usr/bin/env bash
# Envio real na Sepolia pelo Blockchain Service: POST /transactions (CT10), depois
# consulta GET /tasks/{taskId} (CT14) a cada 2 s. Imprime o hash e o link do
# Etherscan assim que o worker envia, e continua até o estado final: CONFIRMED
# (sai 0) ou FAILED (imprime o motivo e sai 1). Se WAIT_SECONDS (padrão 120)
# acabar antes, imprime o último estado e sai 1.
#
# Uso: scripts/send-demo.sh <origem> <destino> <valorEth>
#   origem: carteira custodial deste serviço, abastecida na torneira
#   exemplo: scripts/send-demo.sh 0xSuaCarteira 0x5B38Da6a701c568545dCfcB03FcB875f56beddC4 0.001
#
# A SERVICE_KEY vem da variável exportada ou, se não houver, do ambiente do
# container. Ela nunca é impressa: vai para o curl pela entrada padrão (-H @-).
# A chave privada nunca sai do serviço. Não use "bash -x" aqui.
set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "Uso: $0 <origem> <destino> <valorEth>" >&2
  exit 2
fi
SOURCE="$1"
DESTINATION="$2"
AMOUNT="$3"

# Valida antes de montar o JSON, para nada estranho entrar no corpo.
ADDRESS_REGEX='^0x[0-9a-fA-F]{40}$'
if ! [[ "$SOURCE" =~ $ADDRESS_REGEX && "$DESTINATION" =~ $ADDRESS_REGEX ]]; then
  echo "Endereço inválido: use 0x seguido de 40 caracteres hexadecimais." >&2
  exit 2
fi
if ! [[ "$AMOUNT" =~ ^[0-9]+(\.[0-9]{1,18})?$ ]]; then
  echo "Valor inválido: use um número decimal em ETH, com até 18 casas (ex.: 0.001)." >&2
  exit 2
fi

BASE_URL="${BASE_URL:-http://127.0.0.1:8081}"
WAIT_SECONDS="${WAIT_SECONDS:-120}"
cd "$(dirname "$0")/.."

if [ -z "${SERVICE_KEY:-}" ]; then
  SERVICE_KEY="$(docker compose exec -T blockchain-service printenv SERVICE_KEY)"
fi
KEY_HEADER="X-Service-Key: $SERVICE_KEY"

BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT

# call <método> <caminho> [corpo JSON]: deixa o status em $STATUS e o corpo em $BODY.
call() {
  local data=()
  if [ "$#" -ge 3 ]; then
    data=(-H 'Content-Type: application/json' --data-binary "$3")
  fi
  STATUS="$(printf '%s\n' "$KEY_HEADER" | curl -s -o "$BODY_FILE" -w '%{http_code}' \
    -X "$1" -H @- "${data[@]}" "$BASE_URL$2")"
  BODY="$(cat "$BODY_FILE")"
}

call POST /transactions \
  "$(printf '{"sourceAddress":"%s","destinationAddress":"%s","amountEth":%s}' "$SOURCE" "$DESTINATION" "$AMOUNT")"
if [ "$STATUS" != "202" ]; then
  echo "Pedido recusado: $STATUS $BODY" >&2
  exit 1
fi
TASK_ID="$(printf '%s' "$BODY" | sed -E 's/.*"taskId":"([^"]+)".*/\1/')"
echo "Task $TASK_ID aceita (PENDING). Esperando o worker assinar, enviar e confirmar..."

HASH=""
waited=0
while [ "$waited" -lt "$WAIT_SECONDS" ]; do
  call GET "/tasks/$TASK_ID"
  # O hash é gravado antes do envio; se a rede recusar, ele some e a task fica FAILED.
  if [ -z "$HASH" ] && [[ "$BODY" =~ \"transactionHash\":\"(0x[0-9a-fA-F]{64})\" ]]; then
    HASH="${BASH_REMATCH[1]}"
    echo "Enviada. Hash: $HASH"
    echo "https://sepolia.etherscan.io/tx/$HASH"
    echo "Esperando o recibo..."
  fi
  if [[ "$BODY" =~ \"status\":\"CONFIRMED\" ]]; then
    echo "Confirmada."
    echo "https://sepolia.etherscan.io/tx/$HASH"
    exit 0
  fi
  if [[ "$BODY" =~ \"status\":\"FAILED\" ]]; then
    REASON="$(printf '%s' "$BODY" | sed -E 's/.*"failureReason":"([^"]+)".*/\1/')"
    echo "Task FAILED: $REASON (detalhe no log: docker compose logs blockchain-service)" >&2
    exit 1
  fi
  sleep 2
  waited=$((waited + 2))
done

echo "Sem estado final após ${WAIT_SECONDS} s. Último estado: $STATUS $BODY" >&2
exit 1
