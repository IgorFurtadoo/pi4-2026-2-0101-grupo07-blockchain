# CT27: subscribeTaskNotices

| Linha | Valor |
|---|---|
| Fronteira | Socket TCP + serialização de objetos Java |
| Quem chama | Core API |
| Quem responde | Blockchain Service |
| Atende | E5 e E9 |
| Como chamar | Conexão TCP em `blockchain-service:9091` (`TASK_SOCKET_PORT`), só na rede interna `lemma-dev`; a porta não é publicada no host |

Canal de aviso de task concluída, **além** do webhook CT15, que continua igual (ADR 0008). O Core API abre a conexão e assina; depois disso só o Blockchain Service envia. As classes ficam no pacote `com.lemma.socket`, com arquivos idênticos nos dois módulos e `serialVersionUID = 1L`.

## Fluxo

1. O Core API abre o `Socket`, cria o `ObjectOutputStream` **antes** do `ObjectInputStream` (como no exemplo do professor) e envia `SubscribeRequest`.
2. O Blockchain Service lê a primeira mensagem com prazo de 5 s e compara a chave em tempo constante (`MessageDigest.isEqual`, bytes UTF-8). Se a chave estiver correta, registra o assinante.
3. A cada task que chega a `CONFIRMED` ou `FAILED` (estado já gravado em CT11), o Blockchain Service envia `TaskCompletedNotice` a todos os assinantes, antes de chamar o webhook CT15.
4. No desligamento limpo, envia `ShutdownNotice` e fecha a conexão.

## Entrada (Core API → Blockchain Service)

| Mensagem | Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|---|
| `SubscribeRequest` | serviceKey | texto | sim | dev-service-key (a mesma `SERVICE_KEY` do P2) |

`SubscribeRequest.toString()` nunca imprime a chave.

## Saída (Blockchain Service → Core API)

| Mensagem | Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|---|
| `TaskCompletedNotice` | taskId | texto | sim | a3f1c2d4-5b6e-4f70-8a91-0c2d3e4f5a6b |
| | status | um de: CONFIRMED, FAILED | sim | CONFIRMED |
| | transactionHash | texto | não (null se a transação nunca chegou a ser enviada) | 0x5c504ed4…b22060 |
| | failureReason | um de: REJECTED_BY_NETWORK, TRANSACTION_REVERTED, INTERNAL_ERROR | não (preenchido quando `status` é FAILED) | REJECTED_BY_NETWORK |
| | updatedAt | data e hora ISO 8601 UTC (`Instant.toString()`) | sim | 2026-10-20T14:36:10Z |
| `ShutdownNotice` | (sem campos) | | | |

Os campos e os valores são os mesmos do corpo do CT15.

## Erros

Não há mensagem de erro: o servidor fecha a conexão sem responder e registra um aviso no log, sem a chave e sem o endereço do cliente.

| Quando | O que acontece |
|---|---|
| Chave ausente ou incorreta | Conexão fechada |
| Primeira mensagem não é `SubscribeRequest` | Conexão fechada |
| Objeto de classe fora de `com.lemma.socket.*` e `java.lang.String`, ou maior que 4096 bytes (`ObjectInputFilter`) | Conexão fechada |
| Nenhuma mensagem em 5 s | Conexão fechada |

## Segurança

Os dois lados aplicam um `ObjectInputFilter` logo depois de criar o `ObjectInputStream`, aceitando só `com.lemma.socket.*` e `java.lang.String` e recusando o resto (`!*`).

- Servidor: `maxbytes=4096;com.lemma.socket.*;java.lang.String;!*`. Ele só lê o `SubscribeRequest`.
- Cliente: `maxdepth=5;com.lemma.socket.*;java.lang.String;!*`. **Sem `maxbytes` nem `maxrefs`**: no JDK esses dois limites contam o stream inteiro, não cada mensagem (o `reset()` não os zera), e derrubariam a conexão do Core API depois de cerca de 10 avisos. Isso foi conferido em teste.

## Limitações

- Sem confirmação (ack) e sem retentativa. Quem estiver desconectado na hora do aviso não o recebe e recupera o estado pelo CT14 `getTask`.
- Falha no socket nunca altera o estado da task nem impede o webhook CT15.
- O envio é síncrono, na thread do worker. Um assinante travado, conectado mas sem ler, pode segurar o ciclo do worker até o buffer do TCP liberar. É uma limitação conhecida, aceita porque as mensagens são pequenas e o único assinante é o Core API.
