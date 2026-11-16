package com.lemma.blockchain.controller;

import com.fasterxml.jackson.core.JacksonException;
import com.lemma.blockchain.config.JsonConfig;
import com.lemma.blockchain.exception.ApiException;
import com.lemma.blockchain.repository.TaskRepository;
import com.lemma.blockchain.repository.WalletRepository;
import com.lemma.blockchain.service.TaskService;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CT10 por HTTP, com Javalin de verdade numa porta livre, ligado como no Main
 * (JsonConfig e os três handlers do ErrorHandler), sem filtro de chave e sem banco:
 * os casos daqui são recusados antes de o service consultar um repository.
 */
class TransactionControllerTest {

    private static final String SOURCE = "0x71c7656ec7ab88b098defb751b7401b5f6d8976f";
    private static final String DESTINATION = "0x5b38da6a701c568545dcfcb03fcb875f56beddc4";

    private static Javalin app;
    private static final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    static void startServer() {
        TaskService taskService = new TaskService(new TaskRepository(null), new WalletRepository(null));
        TransactionController controller = new TransactionController(taskService);
        app = Javalin.create(javalin -> {
            javalin.jsonMapper(JsonConfig.jsonMapper());
            javalin.routes.exception(ApiException.class, ErrorHandler::handleApiException);
            javalin.routes.exception(JacksonException.class, ErrorHandler::handleInvalidBody);
            javalin.routes.exception(Exception.class, ErrorHandler::handleUnexpected);
            javalin.routes.post("/transactions", controller::create);
        }).start(0);
    }

    @AfterAll
    static void stopServer() {
        app.stop();
    }

    private static HttpResponse<String> post(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/transactions"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String bodyWithAmount(String amountJson) {
        return "{\"sourceAddress\":\"" + SOURCE + "\",\"destinationAddress\":\"" + DESTINATION + "\","
                + "\"amountEth\":" + amountJson + "}";
    }

    private static void assertError(HttpResponse<String> response, String code) {
        assertEquals(400, response.statusCode(), response.body());
        assertTrue(response.body().startsWith("{\"code\":\"" + code + "\""), response.body());
    }

    @Test
    void extremeNumberIsAmountInvalid() throws Exception {
        assertError(post(bodyWithAmount("1E+2147483647")), "AMOUNT_INVALID");
    }

    /**
     * O Jackson converte texto em BigDecimal, então o valor em texto também chega ao
     * validAmount. A ficha do CT10 trata "campo com tipo errado" como REQUEST_INVALID:
     * divergência registrada no H05.1; se o grupo decidir recusar texto, este teste muda.
     */
    @Test
    void extremeNumberAsTextIsAmountInvalid() throws Exception {
        assertError(post(bodyWithAmount("\"1E+2147483647\"")), "AMOUNT_INVALID");
    }

    @Test
    void malformedJsonIsRequestInvalid() throws Exception {
        assertError(post("{\"sourceAddress\":"), "REQUEST_INVALID");
    }
}
