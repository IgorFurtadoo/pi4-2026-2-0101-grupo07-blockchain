# CT5: wallet

| Linha | Valor |
|---|---|
| Fronteira | Banco |
| Quem chama | Blockchain Service |
| Quem responde | Banco do Blockchain Service |
| Atende | E2 e E8 |
| Como chamar | Tabela `wallet`, chave `ethereum_address`. Operações usadas: gravar e ler por `ethereum_address`. |

Guarda a chave privada cifrada (premissa P7). A chave de cifragem fica em variável de ambiente do Blockchain Service, nunca no banco.

## Entrada

Campos para gravar uma carteira (nomes de coluna em `snake_case`).

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| ethereum_address | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |
| encrypted_private_key | texto | sim | 9fA3c1...b7E2 (valor cifrado) |
| created_at | data e hora | sim | 2026-10-20T14:30:00Z |

Para ler, a entrada é `ethereum_address` (texto, sim, exemplo 0x71C7656EC7ab88b098defB751B7401B5f6d8976F).

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| ethereum_address | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |
| encrypted_private_key | texto | sim | 9fA3c1...b7E2 (valor cifrado) |
| created_at | data e hora | sim | 2026-10-20T14:30:00Z |

## Erros

| Quando | O que volta |
|---|---|
| Gravar com `ethereum_address` já existente | Violação de unicidade. O Blockchain Service responde HTTP 500, `code` INTERNAL_ERROR |
| Ler um `ethereum_address` inexistente | Nenhum registro. Em CT7 e CT10 vira HTTP 404, `code` WALLET_NOT_FOUND |
| Banco do Blockchain Service indisponível | Falha de conexão. O Blockchain Service responde HTTP 500, `code` INTERNAL_ERROR |
