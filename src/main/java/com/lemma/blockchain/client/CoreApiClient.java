package com.lemma.blockchain.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemma.blockchain.dto.TaskResponse;
import com.lemma.blockchain.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Webhook CT15 notifyTaskCompleted: avisa a Core API de que uma task chegou ao
 * estado final (seção 7.5 do desenho).
 *
 * Sem retentativa (P4 em aberto): qualquer falha só vai ao log, e a Core API
 * recupera o estado pelo CT14. Por isso {@link #notifyTaskCompleted} nunca lança.
 *
 * A URL e a X-Service-Key nunca vão ao log. Das exceções, só o nome da classe é
 * registrado: a mensagem pode trazer o host.
 */
public class CoreApiClient {

    private static final Logger log = LoggerFactory.getLogger(CoreApiClient.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private final URI webhookUri;
    private final String serviceKey;
    private final Duration timeout;
    private final HttpClient httpClient;

    public CoreApiClient(String webhookUrl, String serviceKey, Duration timeout) {
        try {
            this.webhookUri = URI.create(webhookUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("CORE_API_WEBHOOK_URL inválida");
        }
        this.serviceKey = serviceKey;
        this.timeout = timeout;
        // HTTP/1.1: sem tentativa de upgrade para HTTP/2 em http:// simples.
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
    }

    /**
     * POST com o mesmo corpo do CT14 (taskId, status, transactionHash e
     * failureReason quando existirem, updatedAt). Chamado pelo worker depois de
     * gravar o estado final, fora de transação de banco. Nunca lança.
     */
    public void notifyTaskCompleted(Task task) {
        String taskId = task.getTaskId();
        try {
            String body = JSON.writeValueAsString(TaskResponse.from(task));
            HttpRequest request = HttpRequest.newBuilder(webhookUri)
                    .timeout(timeout)
                    .header("X-Service-Key", serviceKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            int status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();

            if (status >= 200 && status < 300) {
                log.info("Task {}: webhook CT15 entregue ({}, HTTP {})", taskId, task.getStatus(), status);
            } else if (status == 404) {
                // Corrida conhecida (seção 7.5): a Core API ainda não gravou a transação.
                log.info("Task {}: Core API ainda não conhece a task (HTTP 404); ela recupera pelo CT14", taskId);
            } else {
                log.warn("Task {}: webhook CT15 recusado (HTTP {}); sem retentativa, a Core API recupera pelo CT14",
                        taskId, status);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // devolve o pedido de interrupção a quem chamou
            log.warn("Task {}: webhook CT15 interrompido; sem retentativa, a Core API recupera pelo CT14", taskId);
        } catch (IOException | RuntimeException e) {
            // IOException: conexão recusada, host inexistente, tempo esgotado.
            log.warn("Task {}: webhook CT15 falhou ({}); sem retentativa, a Core API recupera pelo CT14",
                    taskId, e.getClass().getSimpleName());
        }
    }
}
