package com.lemma.blockchain.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa o Web3jEthereumClient contra um servidor HTTP local do próprio JDK,
 * sem rede externa. A URL tem um "token" falso no caminho, como os provedores
 * pagos fazem, para provar que ele nunca aparece na mensagem de erro.
 */
class Web3jEthereumClientTest {

    private static final String TOKEN = "token-secreto-de-teste";
    private static final String ADDRESS = "0x71c7656ec7ab88b098defb751b7401b5f6d8976f";
    private static final String HASH = "0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060";
    private static final String SIGNED_HEX = "0xf86c0785";
    private static final int TIMEOUT_SECONDS = 1;

    /** Uma chamada do EthereumClient, para repetir o mesmo cenário de erro em todas. */
    private interface RpcCall {
        Object call(EthereumClient client) throws RpcException;
    }

    private static final Map<String, RpcCall> ALL_CALLS = new LinkedHashMap<>();

    static {
        ALL_CALLS.put("getBalance", client -> client.getBalance(ADDRESS));
        ALL_CALLS.put("getTransactionCount", client -> client.getTransactionCount(ADDRESS));
        ALL_CALLS.put("getGasPrice", EthereumClient::getGasPrice);
        ALL_CALLS.put("sendRawTransaction", client -> client.sendRawTransaction(SIGNED_HEX));
        ALL_CALLS.put("getTransactionReceipt", client -> client.getTransactionReceipt(HASH));
        ALL_CALLS.put("isTransactionKnown", client -> client.isTransactionKnown(HASH));
    }

    private HttpServer server;
    private ExecutorService serverThreads;
    /** Corpo da última requisição recebida pelo RPC falso. */
    private volatile String lastRequest = "";

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            serverThreads.shutdownNow(); // interrompe o handler que estiver dormindo
        }
    }

    private String startRpc(int status, String body) throws IOException {
        return startRpc(status, body, 0);
    }

    /** Sobe um RPC falso em 127.0.0.1 que espera delayMillis e responde sempre o mesmo status e corpo. */
    private String startRpc(int status, String body, long delayMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverThreads = Executors.newCachedThreadPool();
        server.setExecutor(serverThreads);
        server.createContext("/", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                lastRequest = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/" + TOKEN;
    }

    private static String result(String value) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"" + value + "\"}";
    }

    /** Resposta JSON-RPC com result em JSON cru (objeto ou null). */
    private static String rawResult(String json) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":" + json + "}";
    }

    /** Recibo mínimo com os campos que o sistema usa (CT16); status null = campo ausente. */
    private static String receipt(String status, String blockNumber) {
        String statusField = status == null ? "" : ",\"status\":\"" + status + "\"";
        return rawResult("{\"transactionHash\":\"" + HASH + "\",\"blockNumber\":\"" + blockNumber + "\""
                + statusField + "}");
    }

    private static void assertMessageHidesUrl(RpcException e, String rpcUrl) {
        String message = e.getMessage();
        assertFalse(message.contains(TOKEN), "mensagem não pode conter o token");
        assertFalse(message.contains(rpcUrl), "mensagem não pode conter a URL");
        assertFalse(message.contains("127.0.0.1"), "mensagem não pode conter o host");
    }

    /** Todas as chamadas lançam RpcUnavailableException sem expor a URL. */
    private static void assertAllUnavailable(String url) {
        EthereumClient client = new Web3jEthereumClient(url, TIMEOUT_SECONDS);
        ALL_CALLS.forEach((name, call) -> {
            RpcUnavailableException e = assertThrows(RpcUnavailableException.class, () -> call.call(client), name);
            assertMessageHidesUrl(e, url);
        });
    }

    // ---------- respostas válidas ----------

    @Test
    void returnsBalanceInWei() throws Exception {
        String url = startRpc(200, result("0x3782dace9d900000"));

        BigInteger wei = new Web3jEthereumClient(url, TIMEOUT_SECONDS).getBalance(ADDRESS);

        assertEquals(new BigInteger("4000000000000000000"), wei);
    }

    @Test
    void returnsNonceFromPendingBlock() throws Exception {
        String url = startRpc(200, result("0x7"));

        BigInteger nonce = new Web3jEthereumClient(url, TIMEOUT_SECONDS).getTransactionCount(ADDRESS);

        assertEquals(BigInteger.valueOf(7), nonce);
        assertTrue(lastRequest.contains("\"eth_getTransactionCount\""), lastRequest);
        assertTrue(lastRequest.contains("\"pending\""), "CT21 usa o bloco pending: " + lastRequest);
    }

    @Test
    void returnsGasPriceInWei() throws Exception {
        String url = startRpc(200, result("0x3b9aca00"));

        BigInteger gasPrice = new Web3jEthereumClient(url, TIMEOUT_SECONDS).getGasPrice();

        assertEquals(BigInteger.valueOf(1_000_000_000L), gasPrice);
        assertTrue(lastRequest.contains("\"eth_gasPrice\""), lastRequest);
    }

    @Test
    void sendReturnsHashFromNode() throws Exception {
        String url = startRpc(200, result(HASH));

        String hash = new Web3jEthereumClient(url, TIMEOUT_SECONDS).sendRawTransaction(SIGNED_HEX);

        assertEquals(HASH, hash);
        assertTrue(lastRequest.contains("\"eth_sendRawTransaction\""), lastRequest);
        assertTrue(lastRequest.contains(SIGNED_HEX), lastRequest);
    }

    @Test
    void successfulReceiptReturnsSuccessAndBlock() throws Exception {
        String url = startRpc(200, receipt("0x1", "0x5f3a21"));

        Optional<ReceiptInfo> receipt = new Web3jEthereumClient(url, TIMEOUT_SECONDS).getTransactionReceipt(HASH);

        assertEquals(Optional.of(new ReceiptInfo(true, BigInteger.valueOf(0x5f3a21))), receipt);
        assertTrue(lastRequest.contains("\"eth_getTransactionReceipt\""), lastRequest);
        assertTrue(lastRequest.contains(HASH), lastRequest);
    }

    @Test
    void revertedReceiptReturnsFailure() throws Exception {
        String url = startRpc(200, receipt("0x0", "0x10"));

        Optional<ReceiptInfo> receipt = new Web3jEthereumClient(url, TIMEOUT_SECONDS).getTransactionReceipt(HASH);

        assertEquals(Optional.of(new ReceiptInfo(false, BigInteger.valueOf(16))), receipt);
    }

    @Test
    void nullReceiptIsEmpty() throws Exception {
        String url = startRpc(200, rawResult("null"));

        Optional<ReceiptInfo> receipt = new Web3jEthereumClient(url, TIMEOUT_SECONDS).getTransactionReceipt(HASH);

        assertSame(Optional.empty(), receipt);
    }

    @Test
    void receiptWithoutStatusIsUnavailable() throws Exception {
        // isStatusOK() do Web3j diria true aqui; o client não pode confirmar sem status.
        String url = startRpc(200, receipt(null, "0x10"));

        assertThrows(RpcUnavailableException.class,
                () -> new Web3jEthereumClient(url, TIMEOUT_SECONDS).getTransactionReceipt(HASH));
    }

    @Test
    void receiptWithUnknownStatusIsUnavailable() throws Exception {
        String url = startRpc(200, receipt("0x2", "0x10"));

        assertThrows(RpcUnavailableException.class,
                () -> new Web3jEthereumClient(url, TIMEOUT_SECONDS).getTransactionReceipt(HASH));
    }

    @Test
    void transactionFoundByHashIsKnown() throws Exception {
        String url = startRpc(200, rawResult("{\"hash\":\"" + HASH + "\",\"nonce\":\"0x7\"}"));

        boolean known = new Web3jEthereumClient(url, TIMEOUT_SECONDS).isTransactionKnown(HASH);

        assertTrue(known);
        assertTrue(lastRequest.contains("\"eth_getTransactionByHash\""), lastRequest);
        assertTrue(lastRequest.contains(HASH), lastRequest);
    }

    @Test
    void nullTransactionByHashIsUnknown() throws Exception {
        String url = startRpc(200, rawResult("null"));

        assertFalse(new Web3jEthereumClient(url, TIMEOUT_SECONDS).isTransactionKnown(HASH));
    }

    // ---------- sem resposta utilizável: RpcUnavailableException ----------

    @Test
    void http429IsUnavailable() throws Exception {
        assertAllUnavailable(startRpc(429, "{\"error\":\"rate limited\"}"));
    }

    @Test
    void http500IsUnavailable() throws Exception {
        assertAllUnavailable(startRpc(500, "erro interno do provedor"));
    }

    @Test
    void connectionRefusedIsUnavailable() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort(); // ao sair do try, a porta fica fechada
        }
        assertAllUnavailable("http://127.0.0.1:" + closedPort + "/" + TOKEN);
    }

    @Test
    void slowerThanTimeoutIsUnavailable() throws Exception {
        long delayMillis = (TIMEOUT_SECONDS + 2) * 1000L;
        assertAllUnavailable(startRpc(200, result("0x1"), delayMillis));
    }

    @Test
    void illegibleResultIsUnavailable() throws Exception {
        assertAllUnavailable(startRpc(200, result("nao-e-hexadecimal")));
    }

    // ---------- objeto "error" do JSON-RPC: RpcRejectedException ----------

    @Test
    void jsonRpcErrorIsRejectedWithNodeCodeAndMessage() throws Exception {
        String nodeMessage = "insufficient funds for gas * price + value";
        String url = startRpc(200,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32000,\"message\":\"" + nodeMessage + "\"}}");
        EthereumClient client = new Web3jEthereumClient(url, TIMEOUT_SECONDS);

        ALL_CALLS.forEach((name, call) -> {
            RpcRejectedException e = assertThrows(RpcRejectedException.class, () -> call.call(client), name);
            assertEquals(-32000, e.getRpcCode(), name);
            assertEquals(nodeMessage, e.getNodeMessage(), name);
            assertMessageHidesUrl(e, url);
        });
    }
}
