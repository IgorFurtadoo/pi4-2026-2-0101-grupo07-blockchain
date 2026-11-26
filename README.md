# Lemma · Blockchain Service

Carteira de criptoativos **custodial** para a rede de testes **Ethereum Sepolia**. Uma pessoa sem experiência com blockchain cria uma conta com e-mail e senha, recebe automaticamente uma carteira, vê o saldo, envia ETH de teste, acompanha a transação até "confirmada" ou "falhou" e consulta o preço do ETH, sem nunca ver nem guardar uma chave privada. É só rede de testes: o ETH da Sepolia não tem valor.

Projeto Integrador IV · Sistemas de Informação · PUC-Campinas · 2026-2 · Turma 0101 · Grupo 07.

**Não há repositório principal.** O sistema tem quatro módulos, cada um no seu repositório, com o seu `docker-compose.yml` e a sua release `v1.0`. Este README explica primeiro o módulo deste repositório e depois, na seção [Como rodar o sistema inteiro](#como-rodar-o-sistema-inteiro), como subir os quatro juntos.

## Integrantes e papéis

| Nome | RA | Responde por |
|---|---|---|
| Igor de Toledo Furtado | 25008239 | Blockchain Service e Frontend |
| Lucas Athanasio Bueno de Andrade | 25002731 | External Data Service |
| Guillermo Perdomo Sevilla Neto | 25009270 | Core API e seu banco |
| Nicollas Fabbio | 25007563 | Core API e seu banco |

## Repositórios

| Repositório | O que sobe | Porta no computador |
|---|---|---|
| [pi4-2026-2-0101-grupo07-frontend](https://github.com/IgorFurtadoo/pi4-2026-2-0101-grupo07-frontend) | `frontend` (React servido por nginx). Também guarda o documento de projeto e os fluxos em `docs/` | `127.0.0.1:5173` |
| [pi4-2026-2-0101-grupo07-core-api](https://github.com/IgorFurtadoo/pi4-2026-2-0101-grupo07-core-api) | `core-api` (Java) e `core-db` (MySQL) | `127.0.0.1:8080` |
| [pi4-2026-2-0101-grupo07-blockchain](https://github.com/IgorFurtadoo/pi4-2026-2-0101-grupo07-blockchain) | `blockchain-service` (Java, Web3j) e `blockchain-db` (MySQL) | `127.0.0.1:8081` |
| [pi4-2026-2-0101-grupo07-external-data-service](https://github.com/IgorFurtadoo/pi4-2026-2-0101-grupo07-external-data-service) | `external-data-service` (Java) | `127.0.0.1:8082` |

Os bancos não têm porta publicada.

## Este módulo: Blockchain Service

Serviço Java 21 (Javalin, Hibernate e Web3j) com o seu banco MySQL. Concentra toda a comunicação com a Ethereum Sepolia: gera as carteiras, guarda a chave privada cifrada com AES-256-GCM (ela nunca sai deste serviço), consulta saldo, assina e envia transações em segundo plano como tasks, acompanha o recibo e avisa o Core API por webhook e por socket TCP (CT27). As tasks ficam no banco e sobrevivem a reinícios. É chamado só pelo Core API, com `X-Service-Key`.

Contratos em `docs/contratos/`, ADRs 0001, 0002, 0003 e 0005 em `docs/adr/`, desenho em `docs/desenho-blockchain-service.md` e retomada após reinício em `docs/fluxo-e9-retomada.md`.

### Como rodar só o Blockchain Service

Pré-requisito: Docker com o plugin Compose.

1. Crie a rede compartilhada, se ainda não existir:

   ```sh
   docker network inspect lemma-dev >/dev/null 2>&1 || docker network create lemma-dev
   ```

2. Crie o `.env` (fora do git) a partir do exemplo:

   ```sh
   cp .env.example .env
   ```

   - `DB_PASSWORD`: senha do banco (`openssl rand -hex 16`).
   - `SERVICE_KEY`: o mesmo valor do Core API.
   - `WALLET_ENCRYPTION_KEY`: 32 bytes em Base64 (`openssl rand -base64 32`). Perder este valor torna as carteiras já criadas inutilizáveis.
   - `RPC_URL` e `CHAIN_ID` podem ficar como no exemplo (PublicNode, Sepolia). O serviço recusa `CHAIN_ID` diferente de 11155111.

3. Suba o serviço e o banco:

   ```sh
   docker compose up --build -d
   docker compose logs -f blockchain-service
   ```

   A porta fica publicada só em `127.0.0.1:8081`, para testes com `curl`. O banco não tem porta publicada. O webhook vai para `http://core-api:8080/webhooks/tasks` na `lemma-dev`; sem o Core API no ar, o aviso só é registrado no log. O canal de socket (CT27) escuta em `TASK_SOCKET_PORT` (padrão 9091; 0 desativa) só na `lemma-dev`, sem porta publicada no host; o Core API se conecta em `blockchain-service:9091`. No log aparece `socket: servidor aguardando assinantes na porta 9091`.

4. Confira: toda rota exige `X-Service-Key`.

   ```sh
   curl -i http://127.0.0.1:8081/tasks/teste
   ```

   responde 401 `SERVICE_KEY_INVALID`.

5. Para derrubar: `docker compose down`. **`docker compose down -v` apaga as carteiras e as chaves**, e o ETH de teste que estiver nelas fica inacessível.

### Testes

Com Java 21 e Maven, sem rede externa: `mvn clean test`. Sem Maven, pelo Docker:

```sh
docker run --rm -v "$PWD":/app -w /app maven:3.9.16-eclipse-temurin-21 mvn clean test
```

Roteiros contra o serviço no ar, em `scripts/` (não imprimem a `SERVICE_KEY`):

| Roteiro | O que prova |
|---|---|
| `scripts/smoke-wallet.sh` | Criação de carteira: 401 sem chave, 401 com chave errada, 201 com a chave certa |
| `scripts/smoke-balance.sh` | Saldo: 401, 400, 404 e 200 contra o RPC real |
| `scripts/smoke-tasks.sh` | Pedido de envio e consulta de task; carteira sem saldo termina `FAILED/REJECTED_BY_NETWORK` sem gastar ETH |
| `scripts/send-demo.sh <origem> <destino> <valorEth>` | Envio real na Sepolia até `CONFIRMED`, com o link do Etherscan. Com `RESTART_AFTER=accepted` ou `RESTART_AFTER=sent`, reinicia o contêiner no meio do envio (E9) |

## Como rodar o sistema inteiro

Esta seção é igual nos READMEs dos quatro repositórios. Não há repositório principal: clone os quatro e suba o compose de cada um na mesma máquina. Os backends se encontram pela rede Docker `lemma-dev`.

### Pré-requisitos

| Programa | Versão | Para quê |
|---|---|---|
| Docker Engine com o plugin Compose, ou Docker Desktop | Docker 24 ou mais novo, Compose v2 (`docker compose`) | Construir e rodar os seis contêineres |
| Git | qualquer versão recente | Clonar os quatro repositórios |
| OpenSSL | qualquer (já vem no Linux, no macOS e no Git Bash do Windows) | Gerar os segredos dos arquivos `.env` |
| Navegador | Chrome, Edge ou Firefox atual | Usar o sistema |
| Internet | | O build baixa imagens e dependências; o sistema usa a rede Sepolia, a CoinGecko e o Blockscout |
| Chave da CoinGecko | plano Demo, gratuito | Preço e gráfico do ETH. Crie em <https://www.coingecko.com/en/api/pricing> ("Demo API", conta gratuita) |

As portas **5173**, **8080**, **8081** e **8082** do computador precisam estar livres. No Windows, rode os comandos no **Git Bash**.

### Como subir

1. Clone os quatro repositórios na mesma pasta:

   ```sh
   mkdir lemma && cd lemma
   for r in frontend core-api blockchain external-data-service; do
     git clone https://github.com/IgorFurtadoo/pi4-2026-2-0101-grupo07-$r.git
   done
   ```

2. Crie a rede Docker compartilhada (uma vez por máquina):

   ```sh
   docker network create lemma-dev
   ```

3. Crie o `.env` de cada backend a partir do exemplo, com segredos novos. Rode o bloco inteiro na pasta `lemma`, trocando antes `COLE_AQUI_A_SUA_CHAVE` pela sua chave da CoinGecko. A mesma `SERVICE_KEY` vai para os três backends.

   ```sh
   COINGECKO_API_KEY=COLE_AQUI_A_SUA_CHAVE
   SERVICE_KEY=$(openssl rand -hex 32)

   cd pi4-2026-2-0101-grupo07-blockchain
   cp .env.example .env
   sed -i.bak -e "s|^DB_PASSWORD=.*|DB_PASSWORD=$(openssl rand -hex 16)|" \
     -e "s|^SERVICE_KEY=.*|SERVICE_KEY=$SERVICE_KEY|" \
     -e "s|^WALLET_ENCRYPTION_KEY=.*|WALLET_ENCRYPTION_KEY=$(openssl rand -base64 32)|" .env
   cd ..

   cd pi4-2026-2-0101-grupo07-external-data-service
   cp .env.example .env
   sed -i.bak -e "s|^SERVICE_KEY=.*|SERVICE_KEY=$SERVICE_KEY|" \
     -e "s|^COINGECKO_API_KEY=.*|COINGECKO_API_KEY=$COINGECKO_API_KEY|" .env
   cd ..

   cd pi4-2026-2-0101-grupo07-core-api
   cp .env.example .env
   sed -i.bak -e "s|^DB_PASSWORD=.*|DB_PASSWORD=$(openssl rand -hex 16)|" \
     -e "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')|" \
     -e "s|^SERVICE_KEY=.*|SERVICE_KEY=$SERVICE_KEY|" .env
   cd ..
   ```

   O Frontend não precisa de `.env`. Os arquivos `.env` nunca vão para o git, e os serviços se recusam a subir com valor de exemplo. Quem preferir pode editar cada `.env` à mão, seguindo os comentários do `.env.example` de cada repositório.

4. Suba os quatro composes, nesta ordem. A primeira vez leva alguns minutos (baixa imagens e compila):

   ```sh
   for r in blockchain external-data-service core-api frontend; do
     (cd pi4-2026-2-0101-grupo07-$r && docker compose up --build -d)
   done
   ```

5. Confira se os seis contêineres estão no ar (os bancos aparecem como `healthy`):

   ```sh
   docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
   ```

   Se algum não estiver no ar, veja o log dentro da pasta do repositório, por exemplo `cd pi4-2026-2-0101-grupo07-core-api && docker compose logs core-api`. Segredo ausente ou inválido aparece ali com o nome da variável.

6. Abra **<http://localhost:5173>** no navegador. Use `localhost`, não `127.0.0.1`: é a origem liberada no Core API.

Para parar, rode `docker compose down` em cada uma das quatro pastas. `docker compose down -v` também apaga os dados. No `-blockchain`, isso apaga as carteiras e as chaves, e o ETH de teste que estiver nelas fica inacessível.

### Como usar

Não há usuário pré-cadastrado: cada pessoa cria a sua conta.

| Entregável | Passo a passo |
|---|---|
| E1 Cadastro e login | Em "Criar conta", informe um e-mail e uma senha de pelo menos 8 caracteres. Depois entre com os mesmos dados. Uma senha errada mostra "E-mail ou senha incorretos". |
| E2 Carteira automática | Logo após o cadastro, a tela mostra o endereço da carteira criada para você. Nenhuma chave privada é pedida ou mostrada. |
| E3 Saldo | Ao entrar, a tela "Carteira" mostra o saldo em ETH na Sepolia. Uma carteira nova começa com 0. |
| Abastecer a carteira | Em "Receber", copie o endereço. Abra a torneira por mineração <https://sepolia-faucet.pk910.de>, cole o endereço, mantenha a aba aberta até juntar cerca de 0,01 ETH e resgate. Depois clique em "Atualizar saldo". (Outras torneiras costumam exigir saldo na rede principal.) |
| E4 Enviar | Em "Enviar", informe um endereço de destino (`0x…`) e um valor pequeno, como `0.001`. Revise e confirme. O sistema mostra na hora o identificador do envio. Deixe uma folga de saldo para a taxa: a rede exige que o saldo cubra o valor mais o custo máximo de gás. |
| E5 Acompanhar | Depois de confirmar, você vai para a tela da transação, que muda sozinha de "Aguardando confirmação" para "Confirmada" (ou "Falhou", com o motivo), sem recarregar. No estado final aparece o link para o Etherscan. |
| E6 Mercado | Na tela "Carteira", o painel mostra o preço atual do ETH em dólar e o gráfico dos últimos 7 dias. |
| E7 Limite de consultas | O sistema aceita 10 consultas de mercado por minuto, somando todos os usuários. Acima disso, o painel mostra uma contagem até liberar. |
| E8 Contêineres | Todo envio passa por Frontend → Core API → Blockchain Service → provedor RPC, cada um no seu contêiner. |
| E9 Reinício | Com uma transação ainda aguardando confirmação, rode `docker compose restart blockchain-service` na pasta `-blockchain`. A tela continua mostrando o último estado e a transação termina normalmente. |
| E10 Histórico | Na tela "Carteira", a lista "Atividade" mostra as últimas transações já confirmadas da sua carteira, enviadas e recebidas. |

### Problemas comuns

- **`network lemma-dev declared as external, but could not be found`:** falta o passo 2.
- **A tela não carrega dados ou dá erro de CORS:** abra `http://localhost:5173`, não `http://127.0.0.1:5173`.
- **Cadastro responde que a carteira está indisponível:** o `-blockchain` não está no ar, ou a `SERVICE_KEY` difere entre os `.env`.
- **`port is already allocated`:** outro programa usa uma das portas. Feche-o e suba de novo.
- **Painel de mercado indisponível:** confira a `COINGECKO_API_KEY` no `.env` do `-external-data-service` e rode `docker compose up -d` de novo nessa pasta.
- **Envio termina "Falhou" com "a rede recusou":** o saldo não cobre o valor mais a taxa. Envie um valor menor.

## Documentação

- Documento de projeto: [`docs/documento-de-projeto.md`](https://github.com/IgorFurtadoo/pi4-2026-2-0101-grupo07-frontend/blob/main/docs/documento-de-projeto.md), no repositório do Frontend
- Fluxos dos entregáveis: [`docs/fluxos/`](https://github.com/IgorFurtadoo/pi4-2026-2-0101-grupo07-frontend/tree/main/docs/fluxos), no repositório do Frontend
- Contratos e ADRs: `docs/contratos/` e `docs/adr/` de cada repositório de backend
- Desenho de cada módulo: `docs/desenho-*.md` de cada repositório
