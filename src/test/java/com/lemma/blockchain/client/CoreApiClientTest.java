package com.lemma.blockchain.client;

import com.lemma.blockchain.model.Task;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa o webhook CT15 contra um receptor falso (servidor HTTP do próprio JDK),
 * sem rede externa. O receptor guarda a última requisição recebida.
 */
class CoreApiClientTest {

    private static final String SERVICE_KEY = "chave-de-servico-de-teste";
    private static final String TASK_ID = "a3f1c2d4-5b6e-4f70-8a91-0c2d3e4f5a6b";
    private static final String HASH = "0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060";
    private static final Instant CREATED = Instant.parse("2026-11-09T10:00:00Z");
    /** Com fração de segundo, para conferir que o corpo sai truncado em segundos, como no CT14. */
    private static final Instant FINISHED = Instant.parse("2026-11-09T10:00:15.123456Z");
    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    private HttpServer server;
    private ExecutorService serverThreads;

    private volatile String receivedMethod;
    private volatile String receivedPath;
    private volatile String receivedServiceKey;
    private volatile String receivedContentType;
    private volatile String receivedBody;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            serverThreads.shutdownNow(); // interrompe o handler que estiver dormindo
        }
    }

    /** Sobe o receptor falso, que espera delayMillis e responde o status dado, sem corpo. */
    private String startReceiver(int status, long delayMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverThreads = Executors.newCachedThreadPool();
        server.setExecutor(serverThreads);
        server.createContext("/", exchange -> {
            receivedMethod = exchange.getRequestMethod();
            receivedPath = exchange.getRequestURI().getPath();
            receivedServiceKey = exchange.getRequestHeaders().getFirst("X-Service-Key");
            receivedContentType = exchange.getRequestHeaders().getFirst("Content-Type");
            try (InputStream in = exchange.getRequestBody()) {
                receivedBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            exchange.sendResponseHeaders(status, -1); // sem corpo
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/webhooks/tasks";
    }

    private String startReceiver(int status) throws IOException {
        return startReceiver(status, 0);
    }

    private static CoreApiClient client(String url) {
        return new CoreApiClient(url, SERVICE_KEY, TIMEOUT);
    }

    private static Task sentTask() {
        Task task = new Task(TASK_ID, "0x71c7656ec7ab88b098defb751b7401b5f6d8976f",
                "0x5b38da6a701c568545dcfcb03fcb875f56beddc4", new BigDecimal("0.01"), CREATED);
        task.markSigned(HASH, "0xf86c0785", CREATED);
        return task;
    }

    private static Task confirmedTask() {
        Task task = sentTask();
        task.markConfirmed(FINISHED);
        return task;
    }

    // ---------- requisição ----------

    @Test
    void postsToWebhookWithServiceKeyAndJson() throws Exception {
        String url = startReceiver(204);

        client(url).notifyTaskCompleted(confirmedTask());

        assertEquals("POST", receivedMethod);
        assertEquals("/webhooks/tasks", receivedPath);
        assertEquals(SERVICE_KEY, receivedServiceKey);
        assertEquals("application/json", receivedContentType);
    }

    @Test
    void confirmedBodyHasHashAndNoFailureReason() throws Exception {
        String url = startReceiver(204);

        client(url).notifyTaskCompleted(confirmedTask());

        assertEquals("{\"taskId\":\"" + TASK_ID + "\",\"status\":\"CONFIRMED\",\"transactionHash\":\"" + HASH
                + "\",\"updatedAt\":\"2026-11-09T10:00:15Z\"}", receivedBody);
    }

    @Test
    void revertedBodyHasHashAndFailureReason() throws Exception {
        String url = startReceiver(204);
        Task task = sentTask();
        task.markReverted(FINISHED);

        client(url).notifyTaskCompleted(task);

        assertEquals("{\"taskId\":\"" + TASK_ID + "\",\"status\":\"FAILED\",\"transactionHash\":\"" + HASH
                + "\",\"failureReason\":\"TRANSACTION_REVERTED\",\"updatedAt\":\"2026-11-09T10:00:15Z\"}",
                receivedBody);
    }

    @Test
    void rejectedBodyHasNoHash() throws Exception {
        String url = startReceiver(204);
        Task task = sentTask();
        task.markRejected(FINISHED);

        client(url).notifyTaskCompleted(task);

        assertEquals("{\"taskId\":\"" + TASK_ID + "\",\"status\":\"FAILED\""
                + ",\"failureReason\":\"REJECTED_BY_NETWORK\",\"updatedAt\":\"2026-11-09T10:00:15Z\"}",
                receivedBody);
    }

    // ---------- respostas e falhas: nunca lança ----------

    @Test
    void notFoundDoesNotThrow() throws Exception {
        String url = startReceiver(404);

        assertDoesNotThrow(() -> client(url).notifyTaskCompleted(confirmedTask()));
        assertEquals("POST", receivedMethod);
    }

    @Test
    void serverErrorDoesNotThrow() throws Exception {
        String url = startReceiver(500);

        assertDoesNotThrow(() -> client(url).notifyTaskCompleted(confirmedTask()));
        assertEquals("POST", receivedMethod);
    }

    @Test
    void connectionRefusedDoesNotThrow() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort(); // ao sair do try, a porta fica fechada
        }
        String url = "http://127.0.0.1:" + closedPort + "/webhooks/tasks";

        assertDoesNotThrow(() -> client(url).notifyTaskCompleted(confirmedTask()));
    }

    @Test
    void slowerThanTimeoutReturnsInTimeWithoutThrowing() throws Exception {
        String url = startReceiver(204, 5_000);

        long start = System.nanoTime();
        assertDoesNotThrow(() -> client(url).notifyTaskCompleted(confirmedTask()));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertTrue(elapsedMillis < 3_000, "devia desistir perto de 1 s, levou " + elapsedMillis + " ms");
    }
}
