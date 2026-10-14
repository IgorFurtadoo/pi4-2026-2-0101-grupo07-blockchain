# CT10: requestTransactionSubmission

| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Core API |
| Quem responde | Blockchain Service |
| Atende | E4 e E8 |
| Como chamar | `POST /transactions` |

Cria uma task em CT11 `task` e responde na hora. A assinatura e o envio à rede acontecem em segundo plano (CT21, CT22 e CT23).

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| X-Service-Key (cabeçalho) | texto | sim | dev-service-key |
| fromAddress (corpo) | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |
| destinationAddress (corpo) | texto | sim | 0x5B38Da6a701c568545dCfcB03FcB875f56beddC4 |
| amountEth (corpo) | decimal | sim | 0.01 |

## Saída

Resposta `202`.

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| taskId | texto | sim | a3f1c2d4-5b6e-4f70-8a91-0c2d3e4f5a6b |
| status | um de: PENDING | sim | PENDING |

## Erros

| Quando | O que volta |
|---|---|
| `X-Service-Key` ausente ou incorreta | HTTP 401, `code` SERVICE_KEY_INVALID |
| Endereço com formato inválido | HTTP 400, `code` ADDRESS_INVALID |
| `amountEth` ausente, zero ou negativo | HTTP 400, `code` AMOUNT_INVALID |
| `fromAddress` sem carteira em CT5 `wallet` | HTTP 404, `code` WALLET_NOT_FOUND |
| Falha inesperada, incluindo Banco do Blockchain Service indisponível | HTTP 500, `code` INTERNAL_ERROR |
