# Especificação de Requisitos (Entrega 2) — Grupo 07 / Turma 0101
 
Versão consolidada em um único arquivo para contexto. Fonte de verdade: `docs/fluxos/` (repositório frontend) e `docs/contratos/` (repositórios dos módulos). Em caso de divergência, vale o repositório.
 
---
 
# PARTE 1 — Visão geral e fluxos
 
# Visão geral
 
**Projeto:** Carteira Custodial de Criptoativos (Ethereum Sepolia Testnet)
**Equipe:** Grupo 07 · Turma 0101 · PUC-Campinas, Projeto Integrador IV, 2026-2
**Base:** Entrega 1 (`ESCOPO-0101-grupo07.pdf`), entregáveis E1 a E9.
 
## 1. Mudança em relação à Entrega 1
 
Nenhum entregável foi criado, removido ou teve o "feito quando" alterado. Houve uma mudança de arquitetura decidida depois da Entrega 1:
 
| O que mudou | Entregáveis afetados | Por que mudou |
|---|---|---|
| A aplicação passa de 3 para 4 containers de aplicação. A integração com APIs externas de mercado sai do Core API (Container 2) e passa a ser o módulo External Data Service. | E6 e E7: o Core API deixa de chamar a CoinGecko diretamente e passa a chamar o External Data Service. E8 e OE4: o texto cita "três containers", mas o caminho de E8 (Container 2, Container 3, provedor RPC) não passa pelo novo módulo e continua válido. | A equipe decidiu manter um repositório por implementação. A integração externa fica isolada e com nome neutro, porque pode não usar a CoinGecko no futuro. |
 
Correspondência de nomes: Container 1 é o **Frontend**, Container 2 é o **Core API** e Container 3 é o **Blockchain Service**.
 
A revisão formal da arquitetura por causa dessa decisão ainda não foi feita. Esta especificação a trata como decidida e marca como premissa (seção 5) os pontos que dependem dela.
 
## 2. Módulos
 
| Módulo | O que faz | Tecnologia | Repositório | Responsável |
|---|---|---|---|---|
| Frontend | Telas de cadastro, login, carteira, envio de transação, acompanhamento e painel de mercado. Faz polling do estado da transação. Fala somente com o Core API. | React, TypeScript, Tailwind CSS, React Router (SPA, sem SSR) | `pi4-2026-2-0101-grupo07-frontend` | Sem responsável definido |
| Core API | Ponto único de contato do Frontend. Cadastro, autenticação, associação do usuário ao endereço Ethereum, orquestração das operações de carteira, estado das transações para o Frontend e rate limiting do endpoint de mercado. | Java, Javalin, Hibernate | `pi4-2026-2-0101-grupo07-core-api` | Guillermo e Nicollas |
| Banco do Core API | Armazena usuários e transações. Banco próprio do Core API. | MySQL | Esquema definido em `pi4-2026-2-0101-grupo07-core-api` | Guillermo e Nicollas (responsáveis do Core API, dono do banco) |
| Blockchain Service | Toda a comunicação com a Ethereum: gera carteiras, guarda as chaves privadas, consulta saldo, assina e envia transações, acompanha o resultado como task assíncrona e notifica o Core API por webhook. | Java, Javalin, Web3j | `pi4-2026-2-0101-grupo07-blockchain` | Igor |
| Banco do Blockchain Service | Armazena carteiras (com a chave privada protegida) e tasks. Garante que as tasks sobrevivam a reinícios. Banco próprio do Blockchain Service. | MySQL | Esquema definido em `pi4-2026-2-0101-grupo07-blockchain` | Igor (responsável do Blockchain Service, dono do banco) |
| External Data Service | Integração com APIs externas de mercado. Por ora consulta a CoinGecko e devolve preço atual e histórico recente. | Java, Javalin | `pi4-2026-2-0101-grupo07-external-data-service` | Lucas |
| Provedor RPC | Serviço externo que dá acesso à rede Ethereum Sepolia por JSON-RPC. Não há node próprio. Endpoint público gratuito, proposto: PublicNode (https://ethereum-sepolia-rpc.publicnode.com), configurado por variável de ambiente. Escolha final a confirmar. | JSON-RPC sobre HTTPS (terceiro) | Não se aplica (terceiro) | Igor (responde pela integração, feita no Blockchain Service) |
| CoinGecko API | Serviço externo de dados de mercado (preço e histórico), no plano Demo gratuito (chave de API gratuita, base https://api.coingecko.com/api/v3). | API REST sobre HTTPS (terceiro) | Não se aplica (terceiro) | Lucas (responde pela integração, feita no External Data Service) |
 
A pessoa que usa o sistema é o **Usuário iniciante**, definido na Entrega 1 (hipótese de projeto, sem evidência de campo).
 
## 3. Diagrama de contêineres
 
```mermaid
flowchart TB
    U(["<b>Usuário iniciante</b>"])
 
    subgraph SIS["Sistema: Carteira Custodial de Criptoativos"]
        FE["<b>Frontend</b><br/>React, TypeScript<br/>Telas do usuário e polling"]
        CORE["<b>Core API</b><br/>Java, Javalin, Hibernate<br/>Usuários, autenticação, orquestração,<br/>estado das transações, rate limiting"]
        DBC[("<b>Banco do Core API</b><br/>MySQL<br/>Usuários e transações")]
        BC["<b>Blockchain Service</b><br/>Java, Javalin, Web3j<br/>Carteiras, saldo, envio e tasks"]
        DBB[("<b>Banco do Blockchain Service</b><br/>MySQL<br/>Carteiras e tasks")]
        EXT["<b>External Data Service</b><br/>Java, Javalin<br/>Integração com API de mercado"]
    end
 
    RPC["<b>Provedor RPC</b><br/>Externo<br/>Acesso à rede Ethereum Sepolia"]
    CG["<b>CoinGecko API</b><br/>Externo<br/>Preço e histórico de mercado"]
 
    U -->|"usa"| FE
    FE -->|"operações, HTTP"| CORE
    CORE -->|"lê e grava, SQL"| DBC
    CORE -->|"operações de carteira, HTTP"| BC
    BC -->|"aviso de task concluída, webhook HTTP"| CORE
    BC -->|"lê e grava, SQL"| DBB
    CORE -->|"consulta de mercado, HTTP"| EXT
    EXT -->|"pede preço e histórico, HTTPS"| CG
    BC -->|"saldo, nonce, gás, envio e recibo, JSON-RPC"| RPC
 
    style SIS fill:#fdf0ff,stroke:#e879f9
```
 
## 4. Convenção de nomes
 
Decidida pela equipe: nomes em inglês, seguindo a convenção Java no backend e a convenção JavaScript no frontend. A documentação continua em português. Esta convenção vale para todos os documentos desta entrega.
 
| Item | Convenção | Exemplo |
|---|---|---|
| Idioma dos nomes | Inglês para campos, contratos, endpoints, enums e códigos de erro | `ethereumAddress`, `getBalance` |
| Campos entre módulos (JSON, parâmetros) | `camelCase` (vale para Java e JavaScript) | `ethereumAddress`, `amountEth`, `taskId` |
| Nomes de contrato e operação | `camelCase`, verbo + objeto. Contratos de serviço externo levam o prefixo do serviço (`rpc`, `coingecko`). Contratos de banco levam o nome da tabela. | `registerUser`, `rpcGetBalance`, `app_user` |
| Endpoints | minúsculas, `kebab-case`, coleções no plural. Parâmetros de caminho entre chaves em `camelCase`. | `POST /transactions`, `GET /transactions/{taskId}` |
| Campos do banco | `snake_case`, tabelas no singular (premissa P9). O mapeamento `camelCase` ↔ `snake_case` é mecânico. As fichas de banco listam o nome da coluna. | `ethereum_address` ↔ `ethereumAddress` |
| Enums e valores fechados | `UPPER_SNAKE_CASE`, como constantes Java, enviados como texto no JSON. O Frontend converte em rótulos em português. | `PENDING`, `CONFIRMED`, `FAILED` |
| Códigos de erro | `UPPER_SNAKE_CASE` | `EMAIL_ALREADY_REGISTERED` |
| Eventos e mensagens | O sistema só tem o webhook do Blockchain Service para o Core API. Ele é um `POST` com corpo JSON, nomeado como contrato em `camelCase`. | `notifyTaskCompleted` |
| Datas e horas | ISO 8601 em UTC | `2026-10-20T14:30:00Z` |
| Valores em ETH | decimal em ETH (não em wei), ponto como separador | `0.01` |
| Erros HTTP | Status HTTP + corpo JSON com `code` (texto) e `message` (texto legível, pode ser exibido ao usuário). Nas fichas, a coluna "O que volta" cita o status e o `code`. | `409`, `EMAIL_ALREADY_REGISTERED` |
| Cabeçalhos | `Authorization: Bearer <token>` (Frontend para Core API). `X-Service-Key` (Core API e Blockchain Service entre si). | |
| Serviços externos | Campos e valores originais do serviço são mantidos e não seguem esta convenção (por exemplo `vs_currency`, `eth_getBalance`, `latest`). | |
| Nome dos arquivos de fluxo | Slug em português, derivado do nome do entregável | `E3-consulta-de-saldo-da-carteira.md` |
 
Mapeamento dos estados para o que o usuário vê (Entrega 1): `PENDING` é "pendente", `CONFIRMED` é "confirmada" e `FAILED` é "falhou".
 
## 5. Premissas desta especificação
 
A Proposta Arquitetural deixou alguns pontos em aberto, e contratos exigem decisões concretas. As premissas abaixo foram assumidas para permitir a especificação e confirmadas pela equipe em 13/10/2026, exceto a escolha final do provedor RPC (P8).
 
| ID | Premissa |
|---|---|
| P1 | A autenticação usa JWT. O login (e-mail e senha) devolve um token JWT assinado pelo Core API. O Frontend o envia em `Authorization: Bearer <token>`. O Core API o valida internamente, sem consultar outro módulo. Todas as rotas do Core API exigem token, exceto `registerUser` e `authenticateUser`. |
| P2 | As chamadas do Core API ao Blockchain Service e o webhook do Blockchain Service ao Core API levam o cabeçalho `X-Service-Key`, com chave compartilhada configurada por variável de ambiente. A chamada do Core API ao External Data Service não tem autenticação (rede interna do docker-compose, sem dado sensível). |
| P3 | Estados da task: `PENDING`, `CONFIRMED`, `FAILED` (correspondem a "pendente", "confirmada" e "falhou" da Entrega 1). A modelagem formal continua em aberto. Uma transação é `CONFIRMED` quando o recibo indica sucesso (1 confirmação). A política de N blocos continua em aberto. |
| P4 | O Frontend encerra o polling quando o estado é `CONFIRMED` ou `FAILED`. Enquanto o estado local for `PENDING`, o Core API consulta ativamente o Blockchain Service a cada polling (consulta ativa, além do webhook). Tempo limite da task e retentativa de webhook continuam em aberto e não estão cobertos por contrato. |
| P5 | O rate limiting fica no Core API, sobre o endpoint de mercado, como na Proposta. O valor do limite é configuração do Core API e deve ficar abaixo do limite do plano Demo da CoinGecko (cerca de 30 chamadas por minuto segundo a documentação oficial). Ao revisar a arquitetura, a equipe pode decidir movê-lo para o External Data Service. |
| P6 | O cadastro é síncrono: o Core API só grava o usuário depois que o Blockchain Service criou a carteira. Se a carteira falhar, o usuário não é gravado. A carteira é sempre criada pelo sistema, sem importação de chave externa (conforme E2 da Entrega 1). |
| P7 | A chave privada fica cifrada na tabela `wallet` do Banco do Blockchain Service e nunca sai do Blockchain Service. O mecanismo de cifragem e recuperação continua em aberto (fora do escopo, Parte 4 da Entrega 1). |
| P8 | O provedor RPC é um endpoint público gratuito da Sepolia (proposta: PublicNode), configurado por variável de ambiente. Os contratos usam JSON-RPC padrão da Ethereum. A transação é do tipo legado (preço de gás por `eth_gasPrice`), com limite de gás 21000 (transferência simples) e chainId da Sepolia (11155111). |
| P9 | A equipe não definiu a convenção do banco. Foi assumido `snake_case` em tabelas e colunas, com tabelas no singular (`app_user` evita a palavra reservada `user`). |
| P10 | A CoinGecko é usada no plano Demo gratuito. O External Data Service envia a chave de API no cabeçalho `x-cg-demo-api-key`, lida de variável de ambiente. A chave nunca vai para o Frontend nem para o repositório. |
 
## 6. Índice de fluxos
 
| Entregável | Arquivo |
|---|---|
| E1 | `E1-cadastro-e-autenticacao-de-usuario.md` |
| E2 | `E2-criacao-automatica-de-carteira-custodial.md` |
| E3 | `E3-consulta-de-saldo-da-carteira.md` |
| E4 | `E4-envio-de-transacao-simples.md` |
| E5 | `E5-acompanhamento-assincrono-da-transacao.md` |
| E6 | `E6-painel-de-informacoes-de-mercado.md` |
| E7 | `E7-rate-limiting-na-integracao-com-coingecko.md` |
| E8 | `E8-comunicacao-rest-entre-os-tres-containers.md` |
| E9 | `E9-persistencia-do-estado-das-tasks.md` |
 
---
 
# Fluxo do E1: Cadastro e autenticação de usuário
 
**Feito quando (da Entrega 1):** O usuário se cadastra com e-mail e senha, consegue fazer login em seguida, e uma senha incorreta exibe mensagem de erro sem permitir o acesso.
 
## Passos
 
1. O Usuário iniciante preenche e-mail e senha na tela de cadastro e confirma.
2. O Frontend envia ao Core API o contrato CT1 `registerUser` (`POST /users`) com `email` e `password`.
3. O Core API valida o formato do e-mail e o tamanho da senha. Depois consulta o Banco do Core API por e-mail (CT2 `app_user`) para garantir que o e-mail ainda não existe.
4. O Core API gera o hash da senha, cria a carteira custodial (detalhado no fluxo E2) e grava o usuário em CT2 `app_user` com o hash da senha e o endereço Ethereum.
5. O Core API responde `201` com `userId`, `email` e `ethereumAddress`. O Frontend mostra que o cadastro foi concluído e abre a tela de login.
6. O Usuário iniciante informa e-mail e senha e confirma o login. O Frontend envia o contrato CT3 `authenticateUser` (`POST /auth/login`).
7. O Core API lê o usuário por e-mail (CT2 `app_user`) e compara a senha informada com o hash armazenado.
8. Senha correta: o Core API emite um token JWT e responde `200` com `token` e `expiresAt`. O Frontend guarda o token para as chamadas seguintes e abre a tela da carteira.
9. Senha incorreta: o Core API responde `401` com `INVALID_CREDENTIALS`. O Frontend exibe a mensagem de erro e não guarda token, então o acesso não é liberado.
## Diagrama
 
```mermaid
sequenceDiagram
    actor U as Usuário iniciante
    participant FE as Frontend
    participant CORE as Core API
    participant DBC as Banco do Core API
 
    U->>FE: preenche e-mail e senha e confirma o cadastro
    FE->>CORE: CT1 registerUser
    CORE->>DBC: CT2 app_user (consulta por e-mail)
    DBC-->>CORE: e-mail ainda não cadastrado
    CORE->>CORE: gera o hash da senha e cria a carteira (detalhado em E2)
    CORE->>DBC: CT2 app_user (grava o usuário)
    DBC-->>CORE: usuário gravado
    CORE-->>FE: resposta do CT1
    FE-->>U: cadastro concluído, abre a tela de login
 
    U->>FE: informa e-mail e senha e confirma o login
    FE->>CORE: CT3 authenticateUser
    CORE->>DBC: CT2 app_user (consulta por e-mail)
    DBC-->>CORE: usuário com o hash da senha
    CORE->>CORE: compara a senha com o hash
    alt senha correta
        CORE-->>FE: resposta do CT3 com token
        FE-->>U: acesso liberado, abre a tela da carteira
    else senha incorreta
        CORE-->>FE: erro INVALID_CREDENTIALS
        FE-->>U: mostra mensagem de erro e não libera o acesso
    end
```
 
**Contratos usados:** CT1 `registerUser`, CT2 `app_user`, CT3 `authenticateUser`.
 
---
 
# Fluxo do E2: Criação automática de carteira custodial
 
**Feito quando (da Entrega 1):** Ao concluir o cadastro, o sistema gera e associa automaticamente um endereço Ethereum ao usuário, sem que ele informe ou visualize qualquer chave privada.
 
## Passos
 
1. O Usuário iniciante confirma o cadastro com e-mail e senha. Em nenhum momento a tela pede ou mostra chave privada.
2. O Frontend envia ao Core API o contrato CT1 `registerUser` (`POST /users`).
3. O Core API valida os dados e consulta o Banco do Core API por e-mail (CT2 `app_user`) para garantir que o e-mail ainda não existe.
4. O Core API pede ao Blockchain Service a criação da carteira com o contrato CT4 `createWallet` (`POST /wallets`), sem corpo e com o cabeçalho `X-Service-Key`.
5. O Blockchain Service gera o par de chaves Ethereum, internamente, e grava o endereço e a chave privada protegida na tabela `wallet` do Banco do Blockchain Service (CT5 `wallet`).
6. O Blockchain Service responde `201` ao Core API somente com `ethereumAddress` e `network`. A chave privada nunca sai do Blockchain Service.
7. O Core API grava o usuário com o hash da senha e o `ethereumAddress` em CT2 `app_user`. Isso associa o endereço ao usuário.
8. O Core API responde `201` com `userId`, `email` e `ethereumAddress`. O Frontend mostra a confirmação do cadastro com o endereço público.
## Diagrama
 
```mermaid
sequenceDiagram
    actor U as Usuário iniciante
    participant FE as Frontend
    participant CORE as Core API
    participant DBC as Banco do Core API
    participant BC as Blockchain Service
    participant DBB as Banco do Blockchain Service
 
    U->>FE: confirma o cadastro com e-mail e senha
    FE->>CORE: CT1 registerUser
    CORE->>DBC: CT2 app_user (consulta por e-mail)
    DBC-->>CORE: e-mail ainda não cadastrado
    CORE->>BC: CT4 createWallet
    BC->>BC: gera o par de chaves Ethereum
    BC->>DBB: CT5 wallet (grava endereço e chave privada protegida)
    DBB-->>BC: carteira gravada
    BC-->>CORE: resposta do CT4 com ethereumAddress
    CORE->>DBC: CT2 app_user (grava usuário com ethereumAddress)
    DBC-->>CORE: usuário gravado
    CORE-->>FE: resposta do CT1
    FE-->>U: cadastro concluído, mostra apenas o endereço público
```
 
**Contratos usados:** CT1 `registerUser`, CT2 `app_user`, CT4 `createWallet`, CT5 `wallet`.
 
---
 
# Fluxo do E3: Consulta de saldo da carteira
 
**Feito quando (da Entrega 1):** O usuário abre a tela da carteira e vê o saldo atual em ETH na rede Sepolia Testnet, obtido em tempo real via provedor RPC.
 
## Passos
 
1. O Usuário iniciante abre a tela da carteira.
2. O Frontend envia ao Core API o contrato CT6 `getBalance` (`GET /wallet/balance`) com o cabeçalho `Authorization: Bearer <token>`.
3. O Core API valida o token e identifica o usuário. Depois lê o usuário no Banco do Core API (CT2 `app_user`, por `id`) para obter o `ethereumAddress`.
4. O Core API pede o saldo ao Blockchain Service com o contrato CT7 `getWalletBalance` (`GET /wallets/{ethereumAddress}/balance`).
5. O Blockchain Service consulta o Provedor RPC com o contrato CT8 `rpcGetBalance` (`eth_getBalance`, bloco `latest`).
6. O Provedor RPC devolve o saldo em wei. O Blockchain Service converte para ETH e responde ao Core API com `ethereumAddress`, `balanceEth` e `network`.
7. O Core API repassa a resposta ao Frontend, que mostra o saldo em ETH na Sepolia Testnet.
## Diagrama
 
```mermaid
sequenceDiagram
    actor U as Usuário iniciante
    participant FE as Frontend
    participant CORE as Core API
    participant DBC as Banco do Core API
    participant BC as Blockchain Service
    participant RPC as Provedor RPC
 
    U->>FE: abre a tela da carteira
    FE->>CORE: CT6 getBalance
    CORE->>CORE: valida o token e identifica o usuário
    CORE->>DBC: CT2 app_user (lê por id)
    DBC-->>CORE: usuário com ethereumAddress
    CORE->>BC: CT7 getWalletBalance
    BC->>RPC: CT8 rpcGetBalance
    RPC-->>BC: saldo em wei
    BC->>BC: converte wei para ETH
    BC-->>CORE: resposta do CT7
    CORE-->>FE: resposta do CT6
    FE-->>U: mostra o saldo em ETH na Sepolia Testnet
```
 
**Contratos usados:** CT2 `app_user`, CT6 `getBalance`, CT7 `getWalletBalance`, CT8 `rpcGetBalance`.
 
---
 
# Fluxo do E4: Envio de transação simples
 
**Feito quando (da Entrega 1):** O usuário informa um endereço de destino e um valor, confirma o envio, e recebe imediatamente um identificador de acompanhamento (task) confirmando que a solicitação foi aceita.
 
## Passos
 
1. O Usuário iniciante informa o endereço de destino e o valor em ETH e confirma o envio.
2. O Frontend envia ao Core API o contrato CT9 `sendTransaction` (`POST /transactions`) com `destinationAddress` e `amountEth`, mais o cabeçalho `Authorization: Bearer <token>`.
3. O Core API valida o token, o formato do endereço e o valor. Depois lê o usuário no Banco do Core API (CT2 `app_user`, por `id`) para obter o `ethereumAddress`, que será o `sourceAddress`.
4. O Core API solicita o envio ao Blockchain Service com o contrato CT10 `requestTransactionSubmission` (`POST /transactions`), com `sourceAddress`, `destinationAddress` e `amountEth`.
5. O Blockchain Service valida a solicitação e grava uma task com estado `PENDING` na tabela `task` do Banco do Blockchain Service (CT11 `task`). O processamento real fica para o segundo plano (fluxos E5 e E8).
6. O Blockchain Service responde `202` ao Core API com `taskId` e `status` `PENDING`.
7. O Core API grava a transação com o `taskId`, o `userId` e o estado `PENDING` no Banco do Core API (CT12 `eth_transaction`).
8. O Core API responde `202` ao Frontend com `taskId` e `status`. O Frontend informa que a solicitação foi aceita e mostra o `taskId`.
## Diagrama
 
```mermaid
sequenceDiagram
    actor U as Usuário iniciante
    participant FE as Frontend
    participant CORE as Core API
    participant DBC as Banco do Core API
    participant BC as Blockchain Service
    participant DBB as Banco do Blockchain Service
 
    U->>FE: informa endereço de destino e valor e confirma o envio
    FE->>CORE: CT9 sendTransaction
    CORE->>CORE: valida token, endereço e valor
    CORE->>DBC: CT2 app_user (lê por id)
    DBC-->>CORE: usuário com ethereumAddress
    CORE->>BC: CT10 requestTransactionSubmission
    BC->>DBB: CT11 task (grava task PENDING)
    DBB-->>BC: task gravada
    BC-->>CORE: resposta do CT10 com taskId
    CORE->>DBC: CT12 eth_transaction (grava transação PENDING)
    DBC-->>CORE: transação gravada
    CORE-->>FE: resposta do CT9 com taskId
    FE-->>U: informa que a solicitação foi aceita e mostra o taskId
```
 
**Contratos usados:** CT2 `app_user`, CT9 `sendTransaction`, CT10 `requestTransactionSubmission`, CT11 `task`, CT12 `eth_transaction`.
 
---
 
# Fluxo do E5: Acompanhamento assíncrono da transação
 
**Feito quando (da Entrega 1):** O usuário acompanha, na mesma tela, o estado da transação evoluindo de "pendente" para "confirmada" (ou "falhou"), atualizado automaticamente por polling, sem recarregar a página.
 
## Passos
 
1. Pré-condição: a transação já foi aceita (fluxo E4) e a task está `PENDING`. O envio à rede acontece em segundo plano (fluxo E8).
2. O Usuário iniciante permanece na tela da transação. O Frontend faz polling com o contrato CT13 `getTransaction` (`GET /transactions/{taskId}`), com o cabeçalho `Authorization: Bearer <token>`.
3. O Core API lê a transação no Banco do Core API (CT12 `eth_transaction`). Como o estado local ainda é `PENDING`, consulta ativamente o Blockchain Service com o contrato CT14 `getTask` (`GET /tasks/{taskId}`).
4. O Blockchain Service lê a task no Banco do Blockchain Service (CT11 `task`) e responde `PENDING`. O Core API devolve `PENDING` ao Frontend, que mostra "pendente".
5. Em segundo plano, o Blockchain Service consulta o Provedor RPC com o contrato CT16 `rpcGetTransactionReceipt` (`eth_getTransactionReceipt`) até haver recibo.
6. Com o recibo, o Blockchain Service atualiza a task no Banco do Blockchain Service (CT11 `task`) para `CONFIRMED`. Se o recibo indicar reversão ou a rede rejeitar a transação, a task vai para `FAILED` com um `failureReason`.
7. O Blockchain Service notifica o Core API por webhook com o contrato CT15 `notifyTaskCompleted` (`POST /webhooks/tasks`). O Core API atualiza a transação em CT12 `eth_transaction` e responde `204`.
8. No polling seguinte, o Frontend chama CT13 `getTransaction` e o Core API lê o estado `CONFIRMED` (ou `FAILED`) em CT12 `eth_transaction`. O Frontend atualiza a tela para "confirmada" (ou "falhou") sem recarregar a página e encerra o polling.
9. Se o webhook não chegar, o estado local continua `PENDING`. A consulta ativa (CT14 `getTask`) do passo 3 repete a cada polling e traz o estado final.
## Diagrama
 
```mermaid
sequenceDiagram
    actor U as Usuário iniciante
    participant FE as Frontend
    participant CORE as Core API
    participant DBC as Banco do Core API
    participant BC as Blockchain Service
    participant DBB as Banco do Blockchain Service
    participant RPC as Provedor RPC
 
    Note over U,RPC: Pré-condição: transação aceita (E4) e enviada à rede (E8), task PENDING
 
    FE->>CORE: CT13 getTransaction
    CORE->>DBC: CT12 eth_transaction (lê por taskId)
    DBC-->>CORE: transação PENDING
    CORE->>BC: CT14 getTask
    BC->>DBB: CT11 task (lê por taskId)
    DBB-->>BC: task PENDING
    BC-->>CORE: resposta do CT14
    CORE-->>FE: resposta do CT13
    FE-->>U: mostra o estado pendente
 
    loop o Blockchain Service acompanha a task em segundo plano
        BC->>RPC: CT16 rpcGetTransactionReceipt
        RPC-->>BC: recibo nulo enquanto a transação não entra em bloco
    end
    RPC-->>BC: recibo com status de sucesso
    BC->>DBB: CT11 task (atualiza para CONFIRMED)
    DBB-->>BC: task atualizada
    BC->>CORE: CT15 notifyTaskCompleted
    CORE->>DBC: CT12 eth_transaction (atualiza para CONFIRMED)
    DBC-->>CORE: transação atualizada
    CORE-->>BC: resposta do CT15
 
    FE->>CORE: CT13 getTransaction
    CORE->>DBC: CT12 eth_transaction (lê por taskId)
    DBC-->>CORE: transação CONFIRMED
    CORE-->>FE: resposta do CT13
    FE-->>U: atualiza a tela para confirmada sem recarregar a página
```
 
**Contratos usados:** CT11 `task`, CT12 `eth_transaction`, CT13 `getTransaction`, CT14 `getTask`, CT15 `notifyTaskCompleted`, CT16 `rpcGetTransactionReceipt`.
 
---
 
# Fluxo do E6: Painel de informações de mercado
 
**Feito quando (da Entrega 1):** O usuário visualiza o preço atual e um gráfico de variação recente de pelo menos um criptoativo, obtidos da API da CoinGecko.
 
## Passos
 
1. O Usuário iniciante abre o painel de mercado e o Frontend escolhe o criptoativo (`ethereum`).
2. O Frontend envia ao Core API o contrato CT17 `getMarketData` (`GET /market/{assetId}`), com o cabeçalho `Authorization: Bearer <token>`.
3. O Core API valida o token e verifica o rate limiting (detalhado no fluxo E7). Dentro do limite, pede os dados ao External Data Service com o contrato CT18 `fetchMarketData` (`GET /market/{assetId}`).
4. O External Data Service consulta a CoinGecko API com o contrato CT19 `coingeckoGetSimplePrice` (`/simple/price`) para o preço atual.
5. O External Data Service consulta a CoinGecko API com o contrato CT20 `coingeckoGetMarketChart` (`/coins/{id}/market_chart`, últimos 7 dias) para a série do gráfico.
6. O External Data Service compõe `currentPrice` e `series` e responde `200` ao Core API, que repassa ao Frontend.
7. O Frontend mostra o preço atual e desenha o gráfico de variação dos últimos 7 dias.
## Diagrama
 
```mermaid
sequenceDiagram
    actor U as Usuário iniciante
    participant FE as Frontend
    participant CORE as Core API
    participant EXT as External Data Service
    participant CG as CoinGecko API
 
    U->>FE: abre o painel de mercado
    FE->>CORE: CT17 getMarketData
    CORE->>CORE: valida o token e aplica o rate limiting
    CORE->>EXT: CT18 fetchMarketData
    EXT->>CG: CT19 coingeckoGetSimplePrice
    CG-->>EXT: preço atual em USD
    EXT->>CG: CT20 coingeckoGetMarketChart
    CG-->>EXT: série de preços dos últimos 7 dias
    EXT->>EXT: compõe currentPrice e series
    EXT-->>CORE: resposta do CT18
    CORE-->>FE: resposta do CT17
    FE-->>U: mostra o preço atual e o gráfico de variação recente
```
 
**Contratos usados:** CT17 `getMarketData`, CT18 `fetchMarketData`, CT19 `coingeckoGetSimplePrice`, CT20 `coingeckoGetMarketChart`.
 
---
 
# Fluxo do E7: Rate limiting na integração com CoinGecko
 
**Feito quando (da Entrega 1):** Uma sequência de requisições simultâneas ao endpoint de mercado é limitada pela própria aplicação antes de atingir o limite gratuito da CoinGecko, verificado em teste manual de carga.
 
## Passos
 
1. O Usuário iniciante (no teste manual de carga, de forma repetida e simultânea) faz várias requisições ao endpoint de mercado pelo Frontend.
2. Cada requisição chega ao Core API pelo contrato CT17 `getMarketData` (`GET /market/{assetId}`), com o cabeçalho `Authorization: Bearer <token>`.
3. O Core API valida o token e consulta seu contador de requisições da janela de tempo. O limite é configuração do Core API e fica abaixo do limite gratuito da CoinGecko (premissa P5).
4. Requisição dentro do limite: o Core API chama o External Data Service (CT18 `fetchMarketData`), que consulta a CoinGecko API (CT19 `coingeckoGetSimplePrice` e CT20 `coingeckoGetMarketChart`). A resposta `200` volta ao Frontend (fluxo E6).
5. Requisição acima do limite: o Core API responde `429` com `RATE_LIMIT_EXCEEDED` e o cabeçalho `Retry-After`. Ele **não** chama o External Data Service nem a CoinGecko API.
6. O Frontend mostra uma mensagem de que há requisições demais e pede para tentar novamente mais tarde.
7. A verificação do teste manual de carga é que a CoinGecko não devolve `429` ao sistema enquanto o Core API devolve `429` às requisições excedentes.
## Diagrama
 
```mermaid
sequenceDiagram
    actor U as Usuário iniciante
    participant FE as Frontend
    participant CORE as Core API
    participant EXT as External Data Service
    participant CG as CoinGecko API
 
    loop requisições simultâneas ao endpoint de mercado
        U->>FE: pede os dados de mercado
        FE->>CORE: CT17 getMarketData
        CORE->>CORE: valida o token e confere o contador da janela
        alt dentro do limite
            CORE->>EXT: CT18 fetchMarketData
            EXT->>CG: CT19 coingeckoGetSimplePrice
            CG-->>EXT: preço atual em USD
            EXT->>CG: CT20 coingeckoGetMarketChart
            CG-->>EXT: série de preços dos últimos 7 dias
            EXT-->>CORE: resposta do CT18
            CORE-->>FE: resposta do CT17
            FE-->>U: mostra o preço e o gráfico
        else limite excedido
            CORE-->>FE: erro RATE_LIMIT_EXCEEDED
            FE-->>U: mostra aviso para tentar novamente mais tarde
        end
    end
```
 
**Contratos usados:** CT17 `getMarketData`, CT18 `fetchMarketData`, CT19 `coingeckoGetSimplePrice`, CT20 `coingeckoGetMarketChart`.
 
---
 
# Fluxo do E8: Comunicação REST entre os três containers
 
**Feito quando (da Entrega 1):** Uma transação enviada pelo frontend percorre Container 2 → Container 3 → provedor RPC e retorna uma resposta ao usuário, com os três containers em execução isolada via docker-compose.
 
## Passos
 
1. Pré-condição: Frontend, Core API (Container 2) e Blockchain Service (Container 3) estão em execução isolada pelo docker-compose, cada um no seu container, e se comunicam pela rede do compose.
2. O Usuário iniciante informa destino e valor e confirma o envio. O Frontend envia ao Core API o contrato CT9 `sendTransaction` (`POST /transactions`), com o cabeçalho `Authorization: Bearer <token>`.
3. O Core API valida o token e lê o usuário no Banco do Core API (CT2 `app_user`) para obter o endereço de origem.
4. O Core API chama o Blockchain Service com o contrato CT10 `requestTransactionSubmission` (`POST /transactions`).
5. O Blockchain Service grava a task `PENDING` (CT11 `task`) e responde `202` com `taskId`. O Core API grava a transação (CT12 `eth_transaction`) e responde `202` ao Frontend, que informa a resposta ao Usuário iniciante.
6. Em segundo plano, o Blockchain Service lê a chave privada protegida da carteira de origem (CT5 `wallet`).
7. O Blockchain Service obtém o nonce com o contrato CT21 `rpcGetTransactionCount` (`eth_getTransactionCount`) e o preço do gás com o contrato CT22 `rpcGetGasPrice` (`eth_gasPrice`), ambos junto ao Provedor RPC.
8. O Blockchain Service monta a transação (limite de gás 21000, chainId da Sepolia), assina com a chave privada e a envia ao Provedor RPC com o contrato CT23 `rpcSendRawTransaction` (`eth_sendRawTransaction`).
9. O Provedor RPC devolve o hash da transação. O Blockchain Service grava o `transactionHash` na task (CT11 `task`). O acompanhamento até a confirmação segue no fluxo E5.
## Diagrama
 
```mermaid
sequenceDiagram
    actor U as Usuário iniciante
    participant FE as Frontend
    participant CORE as Core API
    participant DBC as Banco do Core API
    participant BC as Blockchain Service
    participant DBB as Banco do Blockchain Service
    participant RPC as Provedor RPC
 
    Note over FE,BC: Frontend, Core API e Blockchain Service em execução isolada via docker-compose
    U->>FE: informa destino e valor e confirma o envio
    FE->>CORE: CT9 sendTransaction
    CORE->>DBC: CT2 app_user (lê por id)
    DBC-->>CORE: usuário com ethereumAddress
    CORE->>BC: CT10 requestTransactionSubmission
    BC->>DBB: CT11 task (grava task PENDING)
    DBB-->>BC: task gravada
    BC-->>CORE: resposta do CT10 com taskId
    CORE->>DBC: CT12 eth_transaction (grava transação PENDING)
    DBC-->>CORE: transação gravada
    CORE-->>FE: resposta do CT9 com taskId
    FE-->>U: informa que a solicitação foi aceita e mostra o taskId
 
    Note over BC,RPC: Em segundo plano, depois da resposta ao usuário
    BC->>DBB: CT5 wallet (lê a chave privada protegida)
    DBB-->>BC: chave privada protegida
    BC->>RPC: CT21 rpcGetTransactionCount
    RPC-->>BC: nonce
    BC->>RPC: CT22 rpcGetGasPrice
    RPC-->>BC: preço do gás
    BC->>BC: monta e assina a transação
    BC->>RPC: CT23 rpcSendRawTransaction
    RPC-->>BC: hash da transação
    BC->>DBB: CT11 task (grava transactionHash)
    DBB-->>BC: task atualizada
```
 
**Contratos usados:** CT2 `app_user`, CT5 `wallet`, CT9 `sendTransaction`, CT10 `requestTransactionSubmission`, CT11 `task`, CT12 `eth_transaction`, CT21 `rpcGetTransactionCount`, CT22 `rpcGetGasPrice`, CT23 `rpcSendRawTransaction`.
 
---
 
# Fluxo do E9: Persistência do estado das tasks (Container 3)
 
**Feito quando (da Entrega 1):** Após um reinício do Container 3, uma task registrada antes do reinício continua consultável com o último estado conhecido, sem ficar "órfã".
 
## Passos
 
1. Pré-condição: o Usuário iniciante enviou uma transação (fluxo E4). A task está gravada como `PENDING` no Banco do Blockchain Service (CT11 `task`) e o Core API tem a transação `PENDING` (CT12 `eth_transaction`).
2. O Blockchain Service (Container 3) é reiniciado. O estado das tasks está no Banco do Blockchain Service, não na memória do processo.
3. Ao subir, o Blockchain Service lê no Banco do Blockchain Service as tasks ainda `PENDING` (CT11 `task`) e retoma o acompanhamento de cada uma, consultando o Provedor RPC (CT16 `rpcGetTransactionReceipt`).
4. Com o Blockchain Service já reiniciado, o Frontend faz polling com o contrato CT13 `getTransaction`. O Core API lê a transação em CT12 `eth_transaction` (`PENDING`) e consulta o Blockchain Service com o contrato CT14 `getTask`.
5. O Blockchain Service lê a task em CT11 `task` e responde com o último estado conhecido (`PENDING`). A task continua consultável e não ficou órfã. O Frontend mostra o estado ao Usuário iniciante.
6. Quando o recibo aparece, o Blockchain Service atualiza a task (CT11 `task`) para `CONFIRMED` (ou `FAILED`) e notifica o Core API com o contrato CT15 `notifyTaskCompleted`. O Core API atualiza CT12 `eth_transaction`.
7. No polling seguinte, o Frontend recebe o estado final pelo contrato CT13 `getTransaction`.
## Diagrama
 
```mermaid
sequenceDiagram
    actor U as Usuário iniciante
    participant FE as Frontend
    participant CORE as Core API
    participant DBC as Banco do Core API
    participant BC as Blockchain Service
    participant DBB as Banco do Blockchain Service
    participant RPC as Provedor RPC
 
    Note over U,RPC: Pré-condição: transação enviada (E4), task PENDING gravada no Banco do Blockchain Service
    Note over BC: o Blockchain Service é reiniciado
    BC->>DBB: CT11 task (lê as tasks PENDING ao iniciar)
    DBB-->>BC: tasks PENDING com o último estado
    BC->>RPC: CT16 rpcGetTransactionReceipt
    RPC-->>BC: recibo nulo, transação ainda sem confirmação
 
    FE->>CORE: CT13 getTransaction
    CORE->>DBC: CT12 eth_transaction (lê por taskId)
    DBC-->>CORE: transação PENDING
    CORE->>BC: CT14 getTask
    BC->>DBB: CT11 task (lê por taskId)
    DBB-->>BC: task com o último estado conhecido
    BC-->>CORE: resposta do CT14
    CORE-->>FE: resposta do CT13
    FE-->>U: mostra o último estado conhecido, a task não ficou órfã
 
    BC->>RPC: CT16 rpcGetTransactionReceipt
    RPC-->>BC: recibo com status de sucesso
    BC->>DBB: CT11 task (atualiza para CONFIRMED)
    DBB-->>BC: task atualizada
    BC->>CORE: CT15 notifyTaskCompleted
    CORE->>DBC: CT12 eth_transaction (atualiza para CONFIRMED)
    DBC-->>CORE: transação atualizada
    CORE-->>BC: resposta do CT15
 
    FE->>CORE: CT13 getTransaction
    CORE->>DBC: CT12 eth_transaction (lê por taskId)
    DBC-->>CORE: transação CONFIRMED
    CORE-->>FE: resposta do CT13
    FE-->>U: mostra o estado final confirmada
```
 
**Contratos usados:** CT11 `task`, CT12 `eth_transaction`, CT13 `getTransaction`, CT14 `getTask`, CT15 `notifyTaskCompleted`, CT16 `rpcGetTransactionReceipt`.
 
---
 
# PARTE 2 — Contratos CT1 a CT23
 
# CT1: registerUser
 
| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Frontend |
| Quem responde | Core API |
| Atende | E1 e E2 |
| Como chamar | `POST /users` |
 
Cria o usuário e, na mesma chamada, a carteira custodial associada (CT4 `createWallet`). O usuário só é gravado se a carteira for criada (premissa P6).
 
## Entrada
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| email (corpo) | texto | sim | ana@example.com |
| password (corpo) | texto | sim | Senha@12345 |
 
## Saída
 
Resposta `201`.
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| userId | inteiro | sim | 1 |
| email | texto | sim | ana@example.com |
| ethereumAddress | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |
 
## Erros
 
| Quando | O que volta |
|---|---|
| `email` ausente ou com formato inválido | HTTP 400, `code` EMAIL_INVALID |
| `password` ausente ou com menos de 8 caracteres | HTTP 400, `code` PASSWORD_INVALID |
| Já existe usuário com o mesmo `email` | HTTP 409, `code` EMAIL_ALREADY_REGISTERED |
| Blockchain Service indisponível ou falha ao criar a carteira (o usuário não é gravado) | HTTP 502, `code` WALLET_UNAVAILABLE |
| Falha inesperada, incluindo Banco do Core API indisponível | HTTP 500, `code` INTERNAL_ERROR |
 
---
 
# CT2: app_user
 
| Linha | Valor |
|---|---|
| Fronteira | Banco |
| Quem chama | Core API |
| Quem responde | Banco do Core API |
| Atende | E1, E2, E3, E4 e E8 |
| Como chamar | Tabela `app_user`, chave `id`. Busca alternativa por `email` (único). Operações usadas: gravar, ler por `id`, ler por `email`. |
 
O nome `app_user` evita a palavra reservada `user` (premissa P9).
 
## Entrada
 
Campos para gravar um usuário (nomes de coluna em `snake_case`, conforme a convenção da visão geral).
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| id | inteiro | não (gerado pelo banco) | 1 |
| email | texto | sim | ana@example.com |
| password_hash | texto | sim | $2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW |
| ethereum_address | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |
| created_at | data e hora | sim | 2026-10-20T14:30:00Z |
 
Para ler, a entrada é `id` (inteiro, sim, exemplo 1) ou `email` (texto, sim, exemplo ana@example.com).
 
## Saída
 
Campos devolvidos na leitura de um usuário.
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| id | inteiro | sim | 1 |
| email | texto | sim | ana@example.com |
| password_hash | texto | sim | $2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW |
| ethereum_address | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |
| created_at | data e hora | sim | 2026-10-20T14:30:00Z |
 
## Erros
 
| Quando | O que volta |
|---|---|
| Gravar com `email` já existente (restrição de unicidade) | Violação de unicidade. O Core API converte em HTTP 409, `code` EMAIL_ALREADY_REGISTERED |
| Ler por `id` ou `email` inexistente | Nenhum registro. O Core API trata como usuário não encontrado (no login vira HTTP 401, `code` INVALID_CREDENTIALS) |
| Gravar sem um campo obrigatório | Violação de campo obrigatório. O Core API responde HTTP 500, `code` INTERNAL_ERROR |
| Banco do Core API indisponível | Falha de conexão. O Core API responde HTTP 500, `code` INTERNAL_ERROR |
 
---
 
# CT3: authenticateUser
 
| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Frontend |
| Quem responde | Core API |
| Atende | E1 |
| Como chamar | `POST /auth/login` |
 
Emite um token JWT assinado pelo Core API (premissa P1). O mesmo erro `INVALID_CREDENTIALS` vale para e-mail inexistente e senha incorreta, para não revelar quais e-mails existem.
 
## Entrada
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| email (corpo) | texto | sim | ana@example.com |
| password (corpo) | texto | sim | Senha@12345 |
 
## Saída
 
Resposta `200`.
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| token | texto | sim | eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.Kq8vN2xT5mYpR7wZ1cLd3hJfA9sUeB4oGiVtXyW6nMk |
| expiresAt | data e hora | sim | 2026-10-20T18:30:00Z |
 
## Erros
 
| Quando | O que volta |
|---|---|
| `email` ou `password` ausentes | HTTP 400, `code` REQUEST_INVALID |
| E-mail inexistente ou senha incorreta | HTTP 401, `code` INVALID_CREDENTIALS, `message` "E-mail ou senha incorretos". O Frontend exibe a mensagem e não libera o acesso |
| Falha inesperada, incluindo Banco do Core API indisponível | HTTP 500, `code` INTERNAL_ERROR |
 
---
 
# CT4: createWallet
 
| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Core API |
| Quem responde | Blockchain Service |
| Atende | E2 |
| Como chamar | `POST /wallets` |
 
Gera um par de chaves Ethereum, grava a chave privada cifrada em CT5 `wallet` e devolve apenas o endereço público (premissas P6 e P7). A chave privada nunca sai do Blockchain Service.
 
## Entrada
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| X-Service-Key (cabeçalho) | texto | sim | dev-service-key |
 
## Saída
 
Resposta `201`.
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| ethereumAddress | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |
 
## Erros
 
| Quando | O que volta |
|---|---|
| `X-Service-Key` ausente ou incorreta | HTTP 401, `code` SERVICE_KEY_INVALID |
| Falha inesperada, incluindo Banco do Blockchain Service indisponível | HTTP 500, `code` INTERNAL_ERROR |
 
---
 
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
 
---
 
# CT6: getBalance
 
| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Frontend |
| Quem responde | Core API |
| Atende | E3 |
| Como chamar | `GET /wallet/balance` |
 
O Core API identifica o usuário pelo token e usa o `ethereum_address` guardado em CT2 `app_user`. O Frontend não informa endereço.
 
## Entrada
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| Authorization (cabeçalho) | texto | sim | Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.Kq8vN2xT5mYpR7wZ1cLd3hJfA9sUeB4oGiVtXyW6nMk |
 
## Saída
 
Resposta `200`.
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| ethereumAddress | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |
| balanceEth | decimal | sim | 0.25 |
| network | um de: SEPOLIA | sim | SEPOLIA |
 
## Erros
 
| Quando | O que volta |
|---|---|
| Token ausente, inválido ou expirado | HTTP 401, `code` TOKEN_INVALID |
| Blockchain Service ou Provedor RPC indisponível ou com falha | HTTP 502, `code` BLOCKCHAIN_UNAVAILABLE |
| Falha inesperada, incluindo Banco do Core API indisponível | HTTP 500, `code` INTERNAL_ERROR |
 
---
 
# CT7: getWalletBalance
 
| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Core API |
| Quem responde | Blockchain Service |
| Atende | E3 |
| Como chamar | `GET /wallets/{ethereumAddress}/balance` |
 
Consulta o saldo na rede por CT8 `rpcGetBalance` e converte de wei para ETH em decimal.
 
## Entrada
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| X-Service-Key (cabeçalho) | texto | sim | dev-service-key |
| ethereumAddress (caminho) | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |
 
## Saída
 
Resposta `200`.
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| ethereumAddress | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |
| balanceEth | decimal | sim | 0.25 |
| network | um de: SEPOLIA | sim | SEPOLIA |
 
## Erros
 
| Quando | O que volta |
|---|---|
| `X-Service-Key` ausente ou incorreta | HTTP 401, `code` SERVICE_KEY_INVALID |
| `ethereumAddress` com formato inválido | HTTP 400, `code` ADDRESS_INVALID |
| `ethereumAddress` sem carteira em CT5 `wallet` | HTTP 404, `code` WALLET_NOT_FOUND |
| Provedor RPC indisponível ou com falha | HTTP 502, `code` RPC_UNAVAILABLE |
| Falha inesperada, incluindo Banco do Blockchain Service indisponível | HTTP 500, `code` INTERNAL_ERROR |
 
---
 
# CT8: rpcGetBalance
 
| Linha | Valor |
|---|---|
| Fronteira | JSON-RPC |
| Quem chama | Blockchain Service |
| Quem responde | Provedor RPC |
| Atende | E3 |
| Como chamar | JSON-RPC, método `eth_getBalance` |
 
O endpoint é um provedor público gratuito da Sepolia, configurado por variável de ambiente (premissa P8). Serviço externo: os nomes seguem o padrão do Ethereum JSON-RPC.
 
## Entrada
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| params[0] (endereço) | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |
| params[1] (bloco) | texto | sim | latest |
 
## Saída
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| result | texto (saldo em wei, hexadecimal) | sim | 0x3782dace9d900000 |
 
## Erros
 
| Quando | O que volta |
|---|---|
| Endereço com formato inválido | Objeto `error` do JSON-RPC (código -32602). O Blockchain Service responde HTTP 502 em CT7 |
| Provedor fora do ar, tempo esgotado ou limite do provedor excedido | Falha de conexão ou HTTP 429/5xx. O Blockchain Service responde HTTP 502, `code` RPC_UNAVAILABLE em CT7 |
 
---
 
# CT9: sendTransaction
 
| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Frontend |
| Quem responde | Core API |
| Atende | E4 e E8 |
| Como chamar | `POST /transactions` |
 
A resposta confirma que a solicitação foi **aceita**, não que a transação foi confirmada na rede. O Frontend acompanha o resultado com CT13 `getTransaction`.
 
## Entrada
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| Authorization (cabeçalho) | texto | sim | Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.Kq8vN2xT5mYpR7wZ1cLd3hJfA9sUeB4oGiVtXyW6nMk |
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
| Token ausente, inválido ou expirado | HTTP 401, `code` TOKEN_INVALID |
| `destinationAddress` ausente ou com formato inválido | HTTP 400, `code` ADDRESS_INVALID |
| `amountEth` ausente, zero ou negativo | HTTP 400, `code` AMOUNT_INVALID |
| Blockchain Service indisponível ou recusou a solicitação | HTTP 502, `code` BLOCKCHAIN_UNAVAILABLE |
| Falha inesperada, incluindo Banco do Core API indisponível | HTTP 500, `code` INTERNAL_ERROR |
 
---
 
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
| sourceAddress (corpo) | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |
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
| `sourceAddress` sem carteira em CT5 `wallet` | HTTP 404, `code` WALLET_NOT_FOUND |
| Falha inesperada, incluindo Banco do Blockchain Service indisponível | HTTP 500, `code` INTERNAL_ERROR |
 
---
 
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
 
---
 
# CT12: eth_transaction
 
| Linha | Valor |
|---|---|
| Fronteira | Banco |
| Quem chama | Core API |
| Quem responde | Banco do Core API |
| Atende | E4, E5, E8 e E9 |
| Como chamar | Tabela `eth_transaction`, chave `task_id`. Operações usadas: gravar, atualizar e ler por `task_id`. |
 
Cópia local do estado de cada transação, para o Frontend fazer polling no Core API. É atualizada pelo webhook (CT15) ou pela consulta ativa ao Blockchain Service (CT14). Os estados seguem a premissa P3.
 
## Entrada
 
Campos para gravar ou atualizar uma transação (nomes de coluna em `snake_case`).
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| task_id | texto | sim | a3f1c2d4-5b6e-4f70-8a91-0c2d3e4f5a6b |
| user_id | inteiro | sim | 1 |
| destination_address | texto | sim | 0x5B38Da6a701c568545dCfcB03FcB875f56beddC4 |
| amount_eth | decimal | sim | 0.01 |
| status | um de: PENDING, CONFIRMED, FAILED | sim | PENDING |
| transaction_hash | texto | não (preenchido quando o Blockchain Service informa) | 0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060 |
| failure_reason | um de: REJECTED_BY_NETWORK, TRANSACTION_REVERTED, INTERNAL_ERROR | não (só quando `status` é FAILED) | REJECTED_BY_NETWORK |
| created_at | data e hora | sim | 2026-10-20T14:35:00Z |
| updated_at | data e hora | sim | 2026-10-20T14:35:00Z |
 
## Saída
 
Campos devolvidos na leitura de uma transação.
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| task_id | texto | sim | a3f1c2d4-5b6e-4f70-8a91-0c2d3e4f5a6b |
| user_id | inteiro | sim | 1 |
| destination_address | texto | sim | 0x5B38Da6a701c568545dCfcB03FcB875f56beddC4 |
| amount_eth | decimal | sim | 0.01 |
| status | um de: PENDING, CONFIRMED, FAILED | sim | CONFIRMED |
| transaction_hash | texto | não (ausente antes de o Blockchain Service informar) | 0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060 |
| failure_reason | um de: REJECTED_BY_NETWORK, TRANSACTION_REVERTED, INTERNAL_ERROR | não (só quando `status` é FAILED) | REJECTED_BY_NETWORK |
| created_at | data e hora | sim | 2026-10-20T14:35:00Z |
| updated_at | data e hora | sim | 2026-10-20T14:36:10Z |
 
## Erros
 
| Quando | O que volta |
|---|---|
| Gravar com `task_id` já existente | Violação de unicidade da chave. O Core API responde HTTP 500, `code` INTERNAL_ERROR |
| Ler ou atualizar um `task_id` inexistente | Nenhum registro. Em CT13 e CT15 vira HTTP 404, `code` TRANSACTION_NOT_FOUND |
| Banco do Core API indisponível | Falha de conexão. O Core API responde HTTP 500, `code` INTERNAL_ERROR |
 
---
 
# CT13: getTransaction
 
| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Frontend |
| Quem responde | Core API |
| Atende | E5 e E9 |
| Como chamar | `GET /transactions/{taskId}` |
 
O Frontend chama este contrato em polling até o `status` ser `CONFIRMED` ou `FAILED` (premissa P4). Enquanto o estado local for `PENDING`, o Core API consulta o Blockchain Service (CT14) antes de responder. Se essa consulta falhar, o Core API devolve o último estado local conhecido, sem erro.
 
## Entrada
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| Authorization (cabeçalho) | texto | sim | Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.Kq8vN2xT5mYpR7wZ1cLd3hJfA9sUeB4oGiVtXyW6nMk |
| taskId (caminho) | texto | sim | a3f1c2d4-5b6e-4f70-8a91-0c2d3e4f5a6b |
 
## Saída
 
Resposta `200`.
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| taskId | texto | sim | a3f1c2d4-5b6e-4f70-8a91-0c2d3e4f5a6b |
| status | um de: PENDING, CONFIRMED, FAILED | sim | CONFIRMED |
| destinationAddress | texto | sim | 0x5B38Da6a701c568545dCfcB03FcB875f56beddC4 |
| amountEth | decimal | sim | 0.01 |
| transactionHash | texto | não (ausente antes de o Blockchain Service informar) | 0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060 |
| failureReason | um de: REJECTED_BY_NETWORK, TRANSACTION_REVERTED, INTERNAL_ERROR | não (só quando `status` é FAILED) | REJECTED_BY_NETWORK |
| updatedAt | data e hora | sim | 2026-10-20T14:36:10Z |
 
## Erros
 
| Quando | O que volta |
|---|---|
| Token ausente, inválido ou expirado | HTTP 401, `code` TOKEN_INVALID |
| `taskId` inexistente ou pertencente a outro usuário | HTTP 404, `code` TRANSACTION_NOT_FOUND |
| Falha inesperada, incluindo Banco do Core API indisponível | HTTP 500, `code` INTERNAL_ERROR |
 
---
 
# CT14: getTask
 
| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Core API |
| Quem responde | Blockchain Service |
| Atende | E5 e E9 |
| Como chamar | `GET /tasks/{taskId}` |
 
Consulta ativa do Core API. Serve de plano B quando o webhook CT15 não chega (premissa P4). Enquanto a task está `PENDING`, o Blockchain Service pode verificar o recibo na rede (CT16) antes de responder.
 
## Entrada
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| X-Service-Key (cabeçalho) | texto | sim | dev-service-key |
| taskId (caminho) | texto | sim | a3f1c2d4-5b6e-4f70-8a91-0c2d3e4f5a6b |
 
## Saída
 
Resposta `200`.
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| taskId | texto | sim | a3f1c2d4-5b6e-4f70-8a91-0c2d3e4f5a6b |
| status | um de: PENDING, CONFIRMED, FAILED | sim | CONFIRMED |
| transactionHash | texto | não (ausente antes do envio à rede) | 0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060 |
| failureReason | um de: REJECTED_BY_NETWORK, TRANSACTION_REVERTED, INTERNAL_ERROR | não (só quando `status` é FAILED) | REJECTED_BY_NETWORK |
| updatedAt | data e hora | sim | 2026-10-20T14:36:10Z |
 
## Erros
 
| Quando | O que volta |
|---|---|
| `X-Service-Key` ausente ou incorreta | HTTP 401, `code` SERVICE_KEY_INVALID |
| `taskId` inexistente | HTTP 404, `code` TASK_NOT_FOUND |
| Falha inesperada, incluindo Banco do Blockchain Service indisponível | HTTP 500, `code` INTERNAL_ERROR |
 
---
 
# CT15: notifyTaskCompleted
 
| Linha | Valor |
|---|---|
| Fronteira | HTTP (webhook) |
| Quem chama | Blockchain Service |
| Quem responde | Core API |
| Atende | E5 e E9 |
| Como chamar | `POST /webhooks/tasks` |
 
Notificação rápida de que uma task chegou ao estado final. Pela regra de local dos contratos HTTP, a ficha fica no módulo que responde (Core API). A política de retentativa em caso de falha do webhook continua em aberto (premissa P4). Se o webhook não chegar, o Core API recupera o estado por CT14 `getTask`. Reenviar a mesma notificação é seguro: o Core API responde `204` sem alterar nada.
 
## Entrada
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| X-Service-Key (cabeçalho) | texto | sim | dev-service-key |
| taskId (corpo) | texto | sim | a3f1c2d4-5b6e-4f70-8a91-0c2d3e4f5a6b |
| status (corpo) | um de: CONFIRMED, FAILED | sim | CONFIRMED |
| transactionHash (corpo) | texto | não (ausente se a transação nunca chegou a ser enviada) | 0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060 |
| failureReason (corpo) | um de: REJECTED_BY_NETWORK, TRANSACTION_REVERTED, INTERNAL_ERROR | não (obrigatório quando `status` é FAILED) | REJECTED_BY_NETWORK |
| updatedAt (corpo) | data e hora | sim | 2026-10-20T14:36:10Z |
 
## Saída
 
Saída: não tem (resposta `204`, sem corpo).
 
## Erros
 
| Quando | O que volta |
|---|---|
| `X-Service-Key` ausente ou incorreta | HTTP 401, `code` SERVICE_KEY_INVALID |
| Corpo com campo obrigatório ausente ou `status` fora dos valores aceitos | HTTP 400, `code` REQUEST_INVALID |
| `taskId` não existe em CT12 `eth_transaction` (por exemplo, a notificação chegou antes de o Core API gravar a transação) | HTTP 404, `code` TRANSACTION_NOT_FOUND. O Core API recupera o estado depois por CT14 `getTask` |
| Falha inesperada, incluindo Banco do Core API indisponível | HTTP 500, `code` INTERNAL_ERROR |
 
---
 
# CT16: rpcGetTransactionReceipt
 
| Linha | Valor |
|---|---|
| Fronteira | JSON-RPC |
| Quem chama | Blockchain Service |
| Quem responde | Provedor RPC |
| Atende | E5 e E9 |
| Como chamar | JSON-RPC, método `eth_getTransactionReceipt` |
 
Usado para saber se a transação foi incluída em um bloco e se foi executada com sucesso (premissa P3: uma confirmação).
 
## Entrada
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| params[0] (hash da transação) | texto | sim | 0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060 |
 
## Saída
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| result | objeto (Receipt) | não (nulo enquanto a transação não foi incluída em um bloco) | recibo da transação |
 
Campos de `objeto (Receipt)` usados pelo sistema:
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| status | texto (hexadecimal; 0x1 sucesso, 0x0 revertida) | sim | 0x1 |
| blockNumber | texto (hexadecimal) | sim | 0x5f3a21 |
 
## Erros
 
| Quando | O que volta |
|---|---|
| Transação ainda não incluída em bloco | `result` nulo (não é erro). A task segue `PENDING` |
| `status` igual a 0x0 | A task vai para `FAILED`, `failureReason` TRANSACTION_REVERTED |
| Provedor fora do ar, tempo esgotado ou limite excedido | Falha de conexão ou HTTP 429/5xx. A task segue `PENDING` e a verificação é repetida |
 
---
 
# CT17: getMarketData
 
| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Frontend |
| Quem responde | Core API |
| Atende | E6 e E7 |
| Como chamar | `GET /market/{assetId}` |
 
Devolve o preço atual e a série dos últimos 7 dias, em USD. O Core API aplica rate limiting antes de chamar o External Data Service (CT18). O valor do limite é configuração do Core API e fica abaixo do limite do plano Demo da CoinGecko (premissa P5).
 
## Entrada
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| Authorization (cabeçalho) | texto | sim | Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.Kq8vN2xT5mYpR7wZ1cLd3hJfA9sUeB4oGiVtXyW6nMk |
| assetId (caminho) | texto | sim | ethereum |
 
## Saída
 
Resposta `200`.
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| assetId | texto | sim | ethereum |
| currency | um de: USD | sim | USD |
| currentPrice | decimal | sim | 3200.45 |
| series | lista de (objeto (PricePoint)) | sim | lista de pontos de preço dos últimos 7 dias |
 
Campos de `objeto (PricePoint)`:
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| timestamp | data e hora | sim | 2026-10-19T12:00:00Z |
| price | decimal | sim | 3187.9 |
 
## Erros
 
| Quando | O que volta |
|---|---|
| Token ausente, inválido ou expirado | HTTP 401, `code` TOKEN_INVALID |
| `assetId` desconhecido | HTTP 404, `code` ASSET_NOT_FOUND |
| Limite de requisições do Core API excedido | HTTP 429, `code` RATE_LIMIT_EXCEEDED, com o cabeçalho `Retry-After` (inteiro, em segundos) |
| External Data Service ou CoinGecko API indisponível ou com falha | HTTP 502, `code` MARKET_DATA_UNAVAILABLE |
| Falha inesperada | HTTP 500, `code` INTERNAL_ERROR |
 
---
 
# CT18: fetchMarketData
 
| Linha | Valor |
|---|---|
| Fronteira | HTTP |
| Quem chama | Core API |
| Quem responde | External Data Service |
| Atende | E6 e E7 |
| Como chamar | `GET /market-data/{assetId}` |
 
Reúne preço atual (CT19) e série de 7 dias (CT20) da CoinGecko e devolve tudo em USD. O Core API é quem aplica o rate limiting (premissa P5).
 
## Entrada
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| X-Service-Key (cabeçalho) | texto | sim | dev-service-key |
| assetId (caminho) | texto | sim | ethereum |
 
## Saída
 
Resposta `200`.
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| assetId | texto | sim | ethereum |
| currency | um de: USD | sim | USD |
| currentPrice | decimal | sim | 3200.45 |
| series | lista de (objeto (PricePoint)) | sim | lista de pontos de preço dos últimos 7 dias |
 
Campos de `objeto (PricePoint)`:
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| timestamp | data e hora | sim | 2026-10-19T12:00:00Z |
| price | decimal | sim | 3187.9 |
 
## Erros
 
| Quando | O que volta |
|---|---|
| `X-Service-Key` ausente ou incorreta | HTTP 401, `code` SERVICE_KEY_INVALID |
| `assetId` desconhecido | HTTP 404, `code` ASSET_NOT_FOUND |
| CoinGecko API indisponível, com limite excedido ou chave inválida | HTTP 502, `code` MARKET_DATA_UNAVAILABLE |
| Falha inesperada | HTTP 500, `code` INTERNAL_ERROR |
 
---
 
# CT19: coingeckoGetSimplePrice
 
| Linha | Valor |
|---|---|
| Fronteira | HTTP (API externa) |
| Quem chama | External Data Service |
| Quem responde | CoinGecko API |
| Atende | E6 e E7 |
| Como chamar | `GET https://api.coingecko.com/api/v3/simple/price` |
 
Plano Demo, com a chave no cabeçalho `x-cg-demo-api-key`, lida de variável de ambiente (premissa P10). Serviço externo: os nomes seguem a documentação da CoinGecko.
 
## Entrada
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| x-cg-demo-api-key (cabeçalho) | texto | sim | CG-xxxxxxxxxxxxxxxxxxxx |
| ids (consulta) | texto | sim | ethereum |
| vs_currencies (consulta) | texto | sim | usd |
 
## Saída
 
Resposta `200`: objeto cujo nome de campo é o `id` do ativo.
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| {id}.usd | decimal | sim | 3200.45 |
 
Exemplo: `{"ethereum": {"usd": 3200.45}}`.
 
## Erros
 
| Quando | O que volta |
|---|---|
| Chave ausente ou inválida | HTTP 401. O External Data Service responde HTTP 502, `code` MARKET_DATA_UNAVAILABLE |
| Limite do plano Demo excedido | HTTP 429. O External Data Service responde HTTP 502, `code` MARKET_DATA_UNAVAILABLE |
| `ids` desconhecido | A ser confirmado em teste: provavelmente HTTP 200 com objeto vazio `{}`. O External Data Service trata como HTTP 404, `code` ASSET_NOT_FOUND |
| CoinGecko fora do ar ou tempo esgotado | Falha de conexão ou HTTP 5xx. O External Data Service responde HTTP 502, `code` MARKET_DATA_UNAVAILABLE |
 
---
 
# CT20: coingeckoGetMarketChart
 
| Linha | Valor |
|---|---|
| Fronteira | HTTP (API externa) |
| Quem chama | External Data Service |
| Quem responde | CoinGecko API |
| Atende | E6 e E7 |
| Como chamar | `GET https://api.coingecko.com/api/v3/coins/{id}/market_chart` |
 
Plano Demo, com a chave no cabeçalho `x-cg-demo-api-key` (premissa P10). Com `days` igual a 7, a CoinGecko devolve pontos de hora em hora. Serviço externo: os nomes seguem a documentação da CoinGecko.
 
## Entrada
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| x-cg-demo-api-key (cabeçalho) | texto | sim | CG-xxxxxxxxxxxxxxxxxxxx |
| id (caminho) | texto | sim | ethereum |
| vs_currency (consulta) | texto | sim | usd |
| days (consulta) | inteiro | sim | 7 |
 
## Saída
 
Resposta `200`.
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| prices | lista de (lista de decimal) | sim | [[1760875200000, 3187.9], [1760878800000, 3191.2]] |
 
Cada par é [instante em milissegundos desde 1970, preço em USD]. O External Data Service converte o instante para data e hora ISO 8601 UTC.
 
## Erros
 
| Quando | O que volta |
|---|---|
| Chave ausente ou inválida | HTTP 401. O External Data Service responde HTTP 502, `code` MARKET_DATA_UNAVAILABLE |
| Limite do plano Demo excedido | HTTP 429. O External Data Service responde HTTP 502, `code` MARKET_DATA_UNAVAILABLE |
| `id` inválido | A ser confirmado em teste (provável HTTP 404). O External Data Service trata como HTTP 404, `code` ASSET_NOT_FOUND |
| CoinGecko fora do ar ou tempo esgotado | Falha de conexão ou HTTP 5xx. O External Data Service responde HTTP 502, `code` MARKET_DATA_UNAVAILABLE |
 
---
 
# CT21: rpcGetTransactionCount
 
| Linha | Valor |
|---|---|
| Fronteira | JSON-RPC |
| Quem chama | Blockchain Service |
| Quem responde | Provedor RPC |
| Atende | E8 |
| Como chamar | JSON-RPC, método `eth_getTransactionCount` |
 
Obtém o nonce da conta, necessário para montar a transação antes de assiná-la.
 
## Entrada
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| params[0] (endereço) | texto | sim | 0x71C7656EC7ab88b098defB751B7401B5f6d8976F |
| params[1] (bloco) | texto | sim | pending |
 
## Saída
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| result | texto (nonce, hexadecimal) | sim | 0x7 |
 
## Erros
 
| Quando | O que volta |
|---|---|
| Endereço com formato inválido | Objeto `error` do JSON-RPC (código -32602). A task vai para `FAILED`, `failureReason` INTERNAL_ERROR |
| Provedor fora do ar, tempo esgotado ou limite excedido | Falha de conexão ou HTTP 429/5xx. A task vai para `FAILED`, `failureReason` INTERNAL_ERROR |
 
---
 
# CT22: rpcGetGasPrice
 
| Linha | Valor |
|---|---|
| Fronteira | JSON-RPC |
| Quem chama | Blockchain Service |
| Quem responde | Provedor RPC |
| Atende | E8 |
| Como chamar | JSON-RPC, método `eth_gasPrice` |
 
Obtém o preço do gas em wei, usado para montar a transação antes de assiná-la.
 
## Entrada
 
Entrada: não tem (`params` é uma lista vazia).
 
## Saída
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| result | texto (preço do gas em wei, hexadecimal) | sim | 0x3b9aca00 |
 
## Erros
 
| Quando | O que volta |
|---|---|
| Provedor fora do ar, tempo esgotado ou limite excedido | Falha de conexão ou HTTP 429/5xx. A task vai para `FAILED`, `failureReason` INTERNAL_ERROR |
 
---
 
# CT23: rpcSendRawTransaction
 
| Linha | Valor |
|---|---|
| Fronteira | JSON-RPC |
| Quem chama | Blockchain Service |
| Quem responde | Provedor RPC |
| Atende | E8 |
| Como chamar | JSON-RPC, método `eth_sendRawTransaction` |
 
Envia à rede a transação já assinada pelo Blockchain Service. A chave privada nunca é enviada ao provedor.
 
## Entrada
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| params[0] (transação assinada) | texto (hexadecimal) | sim | 0xf86c0785... |
 
## Saída
 
| Campo | Tipo | Obrigatório | Exemplo |
|---|---|---|---|
| result | texto (hash da transação) | sim | 0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060 |
 
## Erros
 
| Quando | O que volta |
|---|---|
| Transação recusada pela rede (saldo insuficiente, nonce inválido, gas insuficiente) | Objeto `error` do JSON-RPC. A task vai para `FAILED`, `failureReason` REJECTED_BY_NETWORK |
| Provedor fora do ar, tempo esgotado ou limite excedido | Falha de conexão ou HTTP 429/5xx. A task vai para `FAILED`, `failureReason` INTERNAL_ERROR |
 
---