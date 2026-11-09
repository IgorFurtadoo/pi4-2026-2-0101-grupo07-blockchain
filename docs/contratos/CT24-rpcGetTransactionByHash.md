# CT24: rpcGetTransactionByHash

| Linha | Valor |
|---|---|
| Fronteira | JSON-RPC |
| Quem chama | Blockchain Service |
| Quem responde | Provedor RPC |
| Atende | E5 e E9 |
| Como chamar | JSON-RPC, método `eth_getTransactionByHash` |

Usado pelo acompanhamento (seção 7.3 do desenho) quando CT16 ainda não devolve recibo, para distinguir "a transação está na rede, esperando bloco" de "a rede não conhece a transação". No segundo caso, o Blockchain Service reenvia a mesma transação assinada (CT23).

Contrato novo, decidido pelo responsável do módulo (Igor), no H08.1. Liga só o Blockchain Service ao Provedor RPC; nenhum outro módulo consome.

## Entrada

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| params[0] (hash da transação) | texto | sim | 0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060 |

## Saída

| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| result | objeto (Transaction) | não (nulo quando a rede não conhece a transação) | dados da transação |

O sistema não lê nenhum campo de `objeto (Transaction)`: só importa se `result` é nulo ou não.

## Erros

| Quando | O que volta |
|---|---|
| A rede não conhece a transação (nunca chegou ou foi descartada do mempool) | `result` nulo (não é erro). Se a task tem a transação assinada, ela é reenviada (CT23); a task segue `PENDING` |
| Erro JSON-RPC (objeto `error`) | A task segue `PENDING` e a verificação é repetida no próximo ciclo |
| Provedor fora do ar, tempo esgotado ou limite excedido | Falha de conexão ou HTTP 429/5xx. A task segue `PENDING` e a verificação é repetida no próximo ciclo |
