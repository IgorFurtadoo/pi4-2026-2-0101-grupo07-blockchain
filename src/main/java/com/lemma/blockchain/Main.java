package com.lemma.blockchain;

import com.lemma.blockchain.client.CoreApiClient;
import com.lemma.blockchain.client.EthereumClient;
import com.lemma.blockchain.client.Web3jEthereumClient;
import com.lemma.blockchain.config.AppConfig;
import com.lemma.blockchain.config.JsonConfig;
import com.lemma.blockchain.controller.ErrorHandler;
import com.fasterxml.jackson.core.JacksonException;
import com.lemma.blockchain.controller.ServiceKeyFilter;
import com.lemma.blockchain.controller.TaskController;
import com.lemma.blockchain.controller.TransactionController;
import com.lemma.blockchain.controller.WalletController;
import com.lemma.blockchain.crypto.KeyCipher;
import com.lemma.blockchain.exception.ApiException;
import com.lemma.blockchain.repository.Database;
import com.lemma.blockchain.repository.TaskRepository;
import com.lemma.blockchain.repository.WalletRepository;
import com.lemma.blockchain.service.TaskService;
import com.lemma.blockchain.service.TaskWorker;
import com.lemma.blockchain.service.WalletService;
import io.javalin.Javalin;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Ponto de entrada do Blockchain Service.
 *
 * Lê a configuração, abre o acesso ao banco, sobe o Javalin e agenda o worker
 * (seção 7.1 do desenho). Se faltar variável,
 * alguma for inválida ou o banco não bater com as entidades, encerra com código 1
 * e uma mensagem clara, antes de abrir a porta. As peças são montadas à mão aqui.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    /** Quanto o desligamento espera o ciclo em andamento terminar. */
    private static final int WORKER_SHUTDOWN_WAIT_SECONDS = 30;

    /** Tempo máximo de cada chamada do webhook CT15 (conexão e resposta). Sem retentativa (P4). */
    private static final int WEBHOOK_TIMEOUT_SECONDS = 5;

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
        TaskRepository taskRepository = new TaskRepository(sessionFactory);
        TaskService taskService = new TaskService(taskRepository, walletRepository);
        TransactionController transactionController = new TransactionController(taskService);
        TaskController taskController = new TaskController(taskService);
        CoreApiClient coreApiClient = new CoreApiClient(config.coreApiWebhookUrl(), config.serviceKey(),
                Duration.ofSeconds(WEBHOOK_TIMEOUT_SECONDS));
        TaskWorker taskWorker = new TaskWorker(taskRepository, walletRepository, keyCipher,
                ethereumClient, coreApiClient, config.chainId());

        Javalin app = Javalin.create(javalin -> {
            javalin.jsonMapper(JsonConfig.jsonMapper());
            // Rota existente com método errado responde 405, não 404.
            javalin.http.prefer405over404 = true;
            javalin.routes.before(new ServiceKeyFilter(config.serviceKey()));
            javalin.routes.exception(ApiException.class, ErrorHandler::handleApiException);
            javalin.routes.exception(JacksonException.class, ErrorHandler::handleInvalidBody);
            javalin.routes.exception(Exception.class, ErrorHandler::handleUnexpected);

            javalin.routes.post("/wallets", walletController::create); // CT4
            javalin.routes.get("/wallets/{ethereumAddress}/balance", walletController::balance); // CT7
            javalin.routes.post("/transactions", transactionController::create); // CT10
            javalin.routes.get("/tasks/{taskId}", taskController::get); // CT14
        }).start(config.serverPort());

        // Worker: uma única thread; o próximo ciclo começa WORKER_INTERVAL_SECONDS depois
        // do fim do anterior, então dois ciclos nunca rodam ao mesmo tempo.
        ScheduledExecutorService workerExecutor =
                Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "task-worker"));
        workerExecutor.scheduleWithFixedDelay(() -> runWorkerCycle(taskWorker),
                config.workerIntervalSeconds(), config.workerIntervalSeconds(), TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.stop();
            stopWorker(workerExecutor);
            sessionFactory.close(); // só depois de o ciclo terminar: ele ainda pode gravar no banco
        }));

        log.info("Blockchain Service no ar na porta {}; worker a cada {} s",
                config.serverPort(), config.workerIntervalSeconds());
    }

    /**
     * Um ciclo do worker com try/catch geral: uma exceção que escapasse daqui
     * cancelaria em silêncio todos os ciclos seguintes do agendador.
     */
    private static void runWorkerCycle(TaskWorker taskWorker) {
        try {
            taskWorker.runCycle();
        } catch (RuntimeException e) {
            log.error("Ciclo do worker falhou ({}: {}); tenta de novo no próximo",
                    e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /** Não agenda novos ciclos e espera o ciclo em andamento terminar. */
    private static void stopWorker(ScheduledExecutorService workerExecutor) {
        workerExecutor.shutdown();
        try {
            if (!workerExecutor.awaitTermination(WORKER_SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Ciclo do worker não terminou em {} s; interrompendo", WORKER_SHUTDOWN_WAIT_SECONDS);
                workerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
