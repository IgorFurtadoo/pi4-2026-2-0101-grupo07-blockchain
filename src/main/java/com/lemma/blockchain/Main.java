package com.lemma.blockchain;

import com.lemma.blockchain.client.EthereumClient;
import com.lemma.blockchain.client.Web3jEthereumClient;
import com.lemma.blockchain.config.AppConfig;
import com.lemma.blockchain.controller.ErrorHandler;
import com.lemma.blockchain.controller.ServiceKeyFilter;
import com.lemma.blockchain.controller.WalletController;
import com.lemma.blockchain.crypto.KeyCipher;
import com.lemma.blockchain.exception.ApiException;
import com.lemma.blockchain.repository.Database;
import com.lemma.blockchain.repository.WalletRepository;
import com.lemma.blockchain.service.WalletService;
import io.javalin.Javalin;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ponto de entrada do Blockchain Service.
 *
 * Lê a configuração, abre o acesso ao banco e sobe o Javalin. Se faltar variável,
 * alguma for inválida ou o banco não bater com as entidades, encerra com código 1
 * e uma mensagem clara, antes de abrir a porta. As peças são montadas à mão aqui.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        AppConfig config;
        try {
            config = AppConfig.fromEnv(System.getenv());
        } catch (IllegalStateException e) {
            log.error("Configuração inválida: {}", e.getMessage());
            System.exit(1);
            return;
        }

        SessionFactory sessionFactory;
        try {
            sessionFactory = Database.createSessionFactory(config);
        } catch (RuntimeException e) {
            log.error("Falha ao iniciar o acesso ao banco: {}", e.getMessage());
            System.exit(1);
            return;
        }

        // Montagem manual: repository e client → service → controller.
        WalletRepository walletRepository = new WalletRepository(sessionFactory);
        KeyCipher keyCipher = new KeyCipher(config.walletEncryptionKey());
        EthereumClient ethereumClient = new Web3jEthereumClient(config.rpcUrl(), config.rpcTimeoutSeconds());
        WalletService walletService = new WalletService(walletRepository, keyCipher, ethereumClient);
        WalletController walletController = new WalletController(walletService);

        Javalin app = Javalin.create(javalin -> {
            javalin.routes.before(new ServiceKeyFilter(config.serviceKey()));
            javalin.routes.exception(ApiException.class, ErrorHandler::handleApiException);
            javalin.routes.exception(Exception.class, ErrorHandler::handleUnexpected);

            javalin.routes.post("/wallets", walletController::create); // CT4
        }).start(config.serverPort());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.stop();
            sessionFactory.close();
        }));

        log.info("Blockchain Service no ar na porta {}", config.serverPort());
    }
}
