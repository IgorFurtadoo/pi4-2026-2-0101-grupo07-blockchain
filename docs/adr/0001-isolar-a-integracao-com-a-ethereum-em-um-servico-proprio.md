# ADR 0001: isolar a integração com a Ethereum em um serviço próprio

**Status:** aceito

**Contexto:** A aplicação é uma carteira custodial na Ethereum Sepolia Testnet: é o sistema, e não o usuário, que executa as operações de carteira, consulta saldos e envia transações. A comunicação com a rede é feita por JSON-RPC a um provedor externo, cuja disponibilidade não é controlada pela equipe, e a biblioteca Web3j é tecnologia nova para o grupo. As demais partes do sistema tratam de usuários, autenticação e dados de mercado, assuntos sem relação direta com a blockchain. A equipe tem quatro integrantes, com cerca de 15 horas semanais cada, e entrega final prevista para dezembro de 2026.

**Decisão:** Concentrar toda a comunicação com a Ethereum (operações de carteira, consulta de saldo e envio de transações, via Web3j) em um serviço Java/Javalin próprio, executado em container separado. Esse serviço é consumido exclusivamente pela Core API, por REST; o frontend e os demais serviços não acessam a rede nem chamam o serviço de blockchain diretamente.

**Alternativas consideradas:**

- Implementar a integração como módulo interno da Core API: descartada porque colocaria no mesmo processo e no mesmo banco as regras de usuário e a lógica de carteira e transações, que concentra os requisitos de segurança do modelo custodial, e porque uma falha na integração com a rede afetaria diretamente o serviço que atende o frontend.
- Acessar a rede diretamente pelo frontend, com uma biblioteca JavaScript no navegador: descartada porque a assinatura das transações teria de ocorrer no navegador, com a chave privada no lado do cliente, o que contradiz o modelo custodial adotado.

**Consequências:**

- Positivas: Web3j, o acesso ao provedor RPC e a lógica de transações ficam restritos a um único repositório, o que é verificável pelas dependências declaradas nos demais repositórios; uma indisponibilidade do provedor RPC ou do próprio serviço de blockchain não impede login nem consulta de dados de mercado; a troca de provedor RPC ou de biblioteca altera apenas este serviço, desde que o contrato REST com a Core API seja mantido.
- Negativas: há mais um container para configurar, executar e depurar no docker-compose; cada operação de carteira ganha uma chamada de rede extra (Core API → serviço de blockchain), sujeita a falhas e timeouts; os dados da carteira ficam divididos entre dois serviços (a associação usuário–endereço na Core API e as operações no serviço de blockchain), de modo que um cadastro pode ter sucesso em um serviço e falhar no outro, exigindo tratamento explícito dessa inconsistência; e a chamada Core API → serviço de blockchain passa a exigir um mecanismo de autenticação próprio, ainda não definido.
