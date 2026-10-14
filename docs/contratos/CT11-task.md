# CT11: task

| Linha | Valor |
|---|---|
| Fronteira | Banco |
| Quem chama | Blockchain Service |
| Quem responde | Banco do Blockchain Service |
| Atende | E4, E5, E8 e E9 |
| Como chamar | Tabela `task`, chave `task_id`. Operações usadas: gravar, atualizar, ler por `task_id` e listar as de `status` PENDING. |

Persiste o estado de cada task, de modo que um reinício do Blockchain Service não perca tasks em andamento (E9). Os estados seguem a premissa P3.

## Entrada

Campos para gravar ou atualizar uma task (nomes de coluna em `snake_case`).

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| task_id | texto | sim | a3f1c2d4-5b6e-4f70-8a91-0c2d3e4f5a6b |
| source_address | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |
| destination_address | texto | sim | 0x5B38Da6a701c568545dCfcB03FcB875f56beddC4 |
| amount_eth | decimal | sim | 0.01 |
| status | um de: PENDING, CONFIRMED, FAILED | sim | PENDING |
| transaction_hash | texto | não (preenchido após o envio à rede) | 0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060 |
| failure_reason | um de: REJECTED_BY_NETWORK, TRANSACTION_REVERTED, INTERNAL_ERROR | não (só quando `status` é FAILED) | REJECTED_BY_NETWORK |
| created_at | data e hora | sim | 2026-10-20T14:35:00Z |
| updated_at | data e hora | sim | 2026-10-20T14:35:00Z |

## Saída

Campos devolvidos na leitura de uma task: os mesmos da entrada.

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| task_id | texto | sim | a3f1c2d4-5b6e-4f70-8a91-0c2d3e4f5a6b |
| source_address | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |
| destination_address | texto | sim | 0x5B38Da6a701c568545dCfcB03FcB875f56beddC4 |
| amount_eth | decimal | sim | 0.01 |
| status | um de: PENDING, CONFIRMED, FAILED | sim | CONFIRMED |
| transaction_hash | texto | não (ausente antes do envio à rede) | 0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060 |
| failure_reason | um de: REJECTED_BY_NETWORK, TRANSACTION_REVERTED, INTERNAL_ERROR | não (só quando `status` é FAILED) | REJECTED_BY_NETWORK |
| created_at | data e hora | sim | 2026-10-20T14:35:00Z |
| updated_at | data e hora | sim | 2026-10-20T14:36:10Z |

## Erros

| Quando | O que volta |
|---|---|
| Gravar com `task_id` já existente | Violação de unicidade da chave. O Blockchain Service responde HTTP 500, `code` INTERNAL_ERROR |
| Ler ou atualizar um `task_id` inexistente | Nenhum registro. Em CT14 vira HTTP 404, `code` TASK_NOT_FOUND |
| Banco do Blockchain Service indisponível | Falha de conexão. O Blockchain Service responde HTTP 500, `code` INTERNAL_ERROR |
