# CLAUDE.md — Blockchain Service (Lemma)

Este repositório é o **Blockchain Service** (Container 3) da carteira custodial Lemma, Projeto Integrador do Grupo 07 (PUC-Campinas). É o único módulo que fala com a Ethereum **Sepolia Testnet**. Nunca use mainnet nem valores reais.

Idioma: comunique-se em português do Brasil. Nomes em código (classes, métodos, campos, endpoints, enums, códigos de erro) em inglês. Comentários e documentação em português.

## Leitura obrigatória antes de qualquer código

1. `docs/desenho-blockchain-service.md`: desenho aprovado (stack, camadas, modelo de dados, worker, segurança, ordem de implementação).
2. `docs/especificacao-entrega2.md`: fonte dos contratos CT1 a CT23 e das premissas P1 a P10. As fichas CT4, CT5, CT7, CT8, CT10, CT11, CT14, CT15, CT16, CT21, CT22 e CT23 definem campos, tipos e erros deste serviço. **Não invente formato de campo, status HTTP ou código de erro**: copie da ficha.

Em caso de divergência entre os dois, pare e pergunte.

## Como trabalhar

- **Um card por vez**, na ordem da seção 13 do desenho. Antes de escrever código, apresente um plano curto (arquivos a criar, commits previstos) e espere aprovação.
- **Simplicidade primeiro.** Sem Spring, sem Lombok, sem Flyway, sem interface por service ou repository, sem padrões que o desenho não pede. A única interface prevista é `EthereumClient`. DTOs são `record`.
- **Dependências permitidas:** Javalin, Jackson, Web3j (`core`), Hibernate ORM, driver MySQL, SLF4J + Logback, JUnit 5. Qualquer outra: pergunte antes.
- **Camadas** (`controller → service → repository`, `service → client`), conforme a seção 4 do desenho. Controller não tem regra de negócio; service não conhece Javalin; entidade não sai no JSON.
- **Convenções:** JSON em `camelCase`; banco em `snake_case`, tabelas no singular; enums e códigos de erro em `UPPER_SNAKE_CASE`; datas em ISO 8601 UTC; ETH em `BigDecimal` (nunca `double`), serializado com `toPlainString()`; erros HTTP no formato `{ "code": "...", "message": "..." }`.
- Código que você escreve deve ser simples o bastante para o autor explicá-lo à banca. Prefira clareza a esperteza.

## O que você NÃO decide sozinho (pare e pergunte)

- Qualquer item "[Aberto]" do desenho (seção 12): tempo limite de task, retentativa do webhook, número de confirmações, código de erro para corpo inválido.
- As três mudanças que tocam contratos: coluna `signed_transaction` (emenda do CT11), chamada `eth_getTransactionByHash` (novo CT24) e a divergência do CT23. Só implemente quando o autor disser que o grupo aprovou.
- Onde mora o `docker-compose` do sistema inteiro. Neste repositório, use um compose apenas para desenvolver este serviço com o seu MySQL.
- Mudar versão do Java, build tool ou nome do pacote base.
- Qualquer mudança em contrato (campo, status, código de erro).

## Decisões de ferramenta (travadas)

- Java 21 (LTS), Maven, pacote base `com.lemma.blockchain`.
- Banco: MySQL 8.x com tag fixa; esquema em `schema.sql` montado em `/docker-entrypoint-initdb.d`; Hibernate em `validate`.
- Portas: Blockchain Service 8081 (publicada só em `127.0.0.1` no desenvolvimento). A porta do MySQL não é publicada.

## Segurança (regras duras)

- **Nunca** coloque chave privada, `SERVICE_KEY`, `WALLET_ENCRYPTION_KEY`, senha de banco ou `.env` em código, log, teste, commit ou saída de comando.
- **Nunca** logue corpo de requisição nem chave decifrada.
- `.env` fica no `.gitignore`; só `.env.example` (com valores de exemplo, sem segredo real) é versionado.
- Comparação de `X-Service-Key` em tempo constante (`MessageDigest.isEqual`).
- Não leia nem edite `.env` (está bloqueado em `.claude/settings.json`).

## Git (regras duras)

- A identidade do git já está configurada neste clone pelo autor. **Não altere `git config`**, não use `--author`, não crie identidade nova.
- **Não adicione `Co-Authored-By`, "Generated with Claude Code", URL de sessão nem qualquer assinatura ou menção ao Claude** em commits ou descrições de PR. (Também desligado em `.claude/settings.json`; esta regra vale mesmo assim.)
- **Nunca faça `git push`.** Quem sobe para o GitHub é o autor.
- Não use `--amend`, `rebase`, `reset --hard` nem `push --force` sem pedido explícito.
- **Uma branch por card**, a partir de `main`: `h02-1-create-wallet`, `h01-5-esqueleto`, etc.
- **Commits pequenos**, um por passo lógico dentro do card. Formato da mensagem, em português, no imperativo:

  ```
  H02.1: criar carteira custodial (CT4, CT5)

  OP#868
  ```

- Só commite com `mvn test` verde e o código compilando. Não commite código quebrado "para salvar".
- Antes de cada commit, rode `git status` e confira que não há arquivo inesperado (`.env`, `target/`, `.idea/`).
- Ao terminar um card, informe: o que foi feito, comandos de verificação e o critério "Pronto quando" cumprido. Não faça merge; o autor revisa `git diff main` e faz o merge.

## Cards (OpenProject)

| Passo | Card | OP |
|---|---|---|
| 1 | H01.5 Esqueleto dos serviços; H01.2 compose (parte deste serviço) | #1315, #865 |
| 2 | H02.4 ADR 0003 (chave privada) | #1316 |
| 3 | H02.1 `createWallet` | #868 |
| 4 | H04.1 `getWalletBalance`; H04.4 carteira de demonstração | #873, #1317 |
| 5 | H05.1 pedido de envio (CT10, CT11) e CT14 | #876 |
| 6 | H07.1 worker, Fase B (assinar e enviar) | #883 |
| 7 | H08.1 worker, Fase A (recibo) e webhook | #885 |
| 8 | H09.1 retomada após reinício | #888 |

Critério de pronto de cada passo: coluna "Pronto quando" da seção 13 do desenho.

## Comandos

Pré-requisitos: Java 21, Maven, Docker com Compose (usuário no grupo `docker`).

Configuração inicial (uma vez):

```bash
cp .env.example .env
# no .env, troque DB_PASSWORD e SERVICE_KEY, e gere a chave de cifragem:
openssl rand -base64 32   # cole a saída em WALLET_ENCRYPTION_KEY
```

O `.env.example` traz `WALLET_ENCRYPTION_KEY=GERAR_COM_openssl_rand_-base64_32`, valor propositalmente inválido: o serviço não sobe até a chave ser trocada.

| Para quê | Comando |
|---|---|
| Testes | `mvn test` |
| Build do jar (`target/blockchain-service.jar`) | `mvn package` |
| Build da imagem | `docker compose build` |
| Subir serviço e banco | `docker compose up --build` (ou `-d` em segundo plano) |
| Ver estado e logs | `docker compose ps` / `docker compose logs -f blockchain-service` |
| Testar se está no ar (sem `X-Service-Key`: 401) | `curl -i http://127.0.0.1:8081/` |
| Smoke do `POST /wallets` (sem chave, chave errada, chave certa) | `scripts/smoke-wallet.sh` (com o compose no ar; não imprime a `SERVICE_KEY`) |
| Ver as tabelas | `docker compose exec blockchain-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" blockchain -e "SHOW TABLES"'` |
| Derrubar | `docker compose down` |
| Derrubar e apagar o banco (o `schema.sql` roda de novo no próximo `up`) | `docker compose down -v` |
