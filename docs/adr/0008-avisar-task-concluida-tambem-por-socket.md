# ADR 0008: avisar a task concluída também por Socket TCP

**Status:** aceito

**Contexto:** O Blockchain Service avisa o Core API de que uma task chegou ao estado final pelo webhook HTTP CT15, sem retentativa; se o aviso se perde, o Core API recupera pelo CT14. A disciplina pede comunicação por Socket com serialização de objetos Java, no modelo do exemplo do professor (Servidor, Parceiro, Comunicado, AceitadoraDeConexao, SupervisoraDeConexao). O aviso de task concluída é o ponto natural para isso: uma mensagem pequena, do Blockchain Service para o Core API, que já tem um caminho de recuperação.

**Decisão:** Acrescentar um canal de Socket TCP (CT27) **sem substituir** o webhook. O Blockchain Service faz o papel do "Servidor" do exemplo: escuta em `TASK_SOCKET_PORT` (padrão 9091, só na rede `lemma-dev`), aceita a assinatura do Core API com a `SERVICE_KEY` e, no mesmo ponto em que chama o webhook (estado final já gravado), envia antes um `TaskCompletedNotice` com os mesmos campos do CT15. As mensagens ficam no pacote `com.lemma.socket`, idêntico nos dois módulos.

| Exemplo do professor | Lemma (Blockchain Service) |
|---|---|
| `Comunicado` | `com.lemma.socket.Message` |
| `ComunicadoDeDesligamento` | `com.lemma.socket.ShutdownNotice` |
| (pedidos do cliente) | `com.lemma.socket.SubscribeRequest` |
| `Resultado` | `com.lemma.socket.TaskCompletedNotice` |
| `Parceiro` | `com.lemma.blockchain.socket.Partner` |
| `AceitadoraDeConexao` | `com.lemma.blockchain.socket.ConnectionAcceptor` |
| `SupervisoraDeConexao` | `com.lemma.blockchain.socket.ConnectionSupervisor` |
| lista `usuarios` sincronizada | lista `subscribers` (o mesmo objeto é o monitor do supervisor e do broadcaster) |
| laço do comando "desativar" no `Servidor` | `com.lemma.blockchain.socket.TaskNoticeBroadcaster` (`broadcast` e `shutdown`) |

**Alternativas consideradas:**

- Trocar o webhook pelo socket: descartada porque o webhook já está testado e documentado (CT15), e com o socket sozinho um Core API desconectado não teria outro aviso além do CT14.
- O Core API consultar o CT14 em loop: descartada porque gera chamadas HTTP repetidas para tasks que ainda não mudaram e não exercita o Socket pedido pela disciplina.

**Consequências:**

- Positivas: dois caminhos independentes de aviso, e o CT14 continua sendo a fonte de verdade; a estrutura segue a do exemplo do professor, classe por classe; nenhuma dependência nova.
- Negativas: a desserialização Java pode executar código se aceitar uma classe arbitrária. A mitigação é um `ObjectInputFilter` nos dois lados, aplicado logo depois de criar o `ObjectInputStream`, que aceita só `com.lemma.socket.*` e `java.lang.String` e recusa o resto (`!*`), além de a porta existir só na rede interna e a assinatura exigir a `SERVICE_KEY`. No servidor o filtro também limita a 4096 bytes. No cliente não pode haver `maxbytes` nem `maxrefs`, porque o JDK os conta no stream inteiro (detalhe na CT27).
- Sem confirmação e sem retentativa: quem estiver desconectado na hora não recebe o aviso e recupera pelo CT14.
- Falha no socket nunca altera o estado da task nem impede o webhook: o broadcast fica em try/catch próprio e só depois o webhook é chamado. O broadcast, porém, é síncrono na thread do worker. Um assinante travado, conectado mas sem ler, pode segurar o ciclo até o buffer do TCP liberar. Essa é uma limitação conhecida, aceita porque as mensagens são pequenas e o único assinante é o Core API.
- Mais uma porta interna (9091) e mais uma variável opcional (`TASK_SOCKET_PORT`; 0 desativa).
