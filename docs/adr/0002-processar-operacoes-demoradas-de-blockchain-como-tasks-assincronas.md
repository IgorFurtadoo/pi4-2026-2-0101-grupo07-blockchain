# ADR 0002: processar operações demoradas de blockchain como tasks assíncronas

**Status:** aceito

**Contexto:** O envio de uma transação à Sepolia é rápido, mas a confirmação pela rede pode levar de segundos a minutos, tempo incompatível com uma requisição HTTP mantida aberta entre frontend, Core API e serviço de blockchain (ADR 0001). O escopo exige que o usuário receba imediatamente um identificador de acompanhamento ao enviar uma transação e veja o estado evoluir até "confirmada" ou "falhou" sem recarregar a página (entregáveis E4 e E5). Uma operação em andamento não pode ficar órfã se o serviço de blockchain for reiniciado (entregável E9). A equipe adotou como diretriz não introduzir infraestrutura sem necessidade comprovada.

**Decisão:** O serviço de blockchain registra e persiste cada operação demorada como uma task, responde de imediato com HTTP 202 e o identificador da task, processa a operação em segundo plano e notifica a Core API por webhook ao concluir. O webhook é apenas notificação rápida: a Core API também consulta ativamente o estado da task quando necessário, e o frontend acompanha o andamento por polling na Core API.

**Alternativas consideradas:**

- Requisição síncrona até a confirmação da transação: descartada porque manteria conexões abertas por até minutos em toda a cadeia, sujeitas a timeout, e o usuário ficaria sem retorno imediato.
- Fila de mensageria (RabbitMQ, Kafka ou Redis): descartada neste momento por acrescentar um componente de infraestrutura, com configuração e operação próprias, sem necessidade comprovada para o volume de um MVP acadêmico em rede de testes.
- Webhook como única fonte de verdade: descartada porque uma notificação perdida, por falha de rede ou indisponibilidade da Core API, deixaria a operação sem estado atualizado e sem forma de recuperação.
- Apenas consulta periódica da Core API ao serviço de blockchain, sem webhook: descartada porque a atualização ficaria presa ao intervalo de consulta, com atraso perceptível ou com chamadas frequentes entre os serviços sem mudança de estado.

**Consequências:**

- Positivas: nenhuma requisição HTTP fica bloqueada aguardando confirmação de bloco; o usuário recebe o identificador da task imediatamente (verificável no entregável E4); a perda de um webhook não deixa a operação sem estado, pois a Core API pode consultar a task; nenhum componente de mensageria é adicionado ao docker-compose; e uma task registrada antes de um reinício do serviço continua consultável (verificável no entregável E9).
- Negativas: o serviço de blockchain passa a precisar de persistência própria e de um mecanismo de processamento em segundo plano implementado pela equipe, sem as garantias de entrega de uma fila; a Core API recebe atualizações por dois caminhos (webhook e consulta ativa) e precisa tratá-las de forma idempotente, sem registrar o mesmo resultado duas vezes nem regredir o estado; o polling do frontend gera requisições periódicas à Core API; e o modelo só fica completo quando forem definidos os estados formais da task, o número de confirmações de bloco, a política de retentativa do webhook, o critério de parada do polling e a validação da origem do webhook, hoje em aberto.
