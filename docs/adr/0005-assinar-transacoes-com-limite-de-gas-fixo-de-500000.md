# ADR 0005: assinar as transações com limite de gás fixo de 500.000

**Status:** aceito

**Contexto:** A premissa P8 da Especificação fixava o limite de gás em 21.000, o custo de uma transferência simples de ETH. Em 06/10/2026 a Sepolia ativou o Glamsterdam, e o EIP-8037 encareceu a criação de conta: enviar valor a um endereço que ainda não existe na rede (saldo 0, nonce 0, sem código) passou a custar cerca de 207 mil de gás. O primeiro envio real do Blockchain Service, em 08/11/2026, usou 21.000 e reverteu por falta de gás (tx `0x14d26d929efa2f658a0b7968bfa75cb752a039414a587c6bada642bf67d1ea2a`): a origem pagou a taxa e o valor não saiu. Medições com `eth_estimateGas` deram 207.391 para conta nova e 21.000 para conta existente. Toda carteira criada pelo Lemma começa vazia, então o primeiro envio a ela sempre cai no caso caro.

**Decisão:** O worker do Blockchain Service assina toda transação com limite de gás fixo de 500.000 (constante `GAS_LIMIT` no `TaskWorker`), mantendo o preço do gás por `eth_gasPrice` (CT22) e a transação do tipo legado. O gás não usado é devolvido pela rede.

**Alternativas consideradas:**

- Manter 21.000: descartada porque reverte todo envio a conta nova, inclusive de uma carteira Lemma para outra recém-criada.
- Estimar o gás por transação com `eth_estimateGas` e uma margem: descartada nesta versão porque acrescenta um contrato RPC novo, mais uma chamada por envio e mais um ponto de falha antes de assinar. Fica como melhoria futura.
- Escolher o limite pelo estado do destino (21.000 ou ~210.000): descartada porque exige consultar a conta de destino antes de assinar e quebra se a regra da rede mudar de novo.

**Consequências:**

- Positivas: envios a contas novas e existentes funcionam com a mesma regra; os envios a conta existente continuam gastando 21.000 de fato (conferido em seis transações reais); nenhum contrato novo.
- Negativas: a rede exige que o saldo cubra o valor mais 500.000 × preço do gás antes de aceitar, então o usuário nunca consegue enviar o saldo inteiro, e o Frontend não oferece "enviar tudo"; se a rede encarecer além de 500.000, a transação reverte (`FAILED/TRANSACTION_REVERTED`) e o usuário perde só a taxa; a P8 e o passo 8 do fluxo E8 da Especificação ficam desatualizados.
