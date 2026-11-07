package com.lemma.blockchain;

import com.lemma.blockchain.config.AppConfig;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ponto de entrada do Blockchain Service.
 *
 * Lê a configuração e sobe o Javalin. Se faltar variável ou alguma for inválida,
 * encerra com código 1 e uma mensagem clara, antes de abrir a porta.
 * Rotas, worker e banco entram nos cards seguintes (seção 13 do desenho).
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

        Javalin app = Javalin.create().start(config.serverPort());
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));

        log.info("Blockchain Service no ar na porta {}", config.serverPort());
    }
}
