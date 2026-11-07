package com.lemma.blockchain.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testa o Web3jEthereumClient contra um servidor HTTP local do próprio JDK,
 * sem rede externa. A URL tem um "token" falso no caminho, como os provedores
 * pagos fazem, para provar que ele nunca aparece na mensagem de erro.
 */
class Web3jEthereumClientTest {

    private static final String TOKEN = "token-secreto-de-teste";
    private static final String ADDRESS = "0x71c7656ec7ab88b098defb751b7401b5f6d8976f";
    private static final int TIMEOUT_SECONDS = 2;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** Sobe um RPC falso em 127.0.0.1 que sempre responde o mesmo status e corpo. */
    private String startRpc(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
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

    private static void assertMessageHidesUrl(RpcException e, String rpcUrl) {
        String message = e.getMessage();
        assertFalse(message.contains(TOKEN), "mensagem não pode conter o token");
        assertFalse(message.contains(rpcUrl), "mensagem não pode conter a URL");
        assertFalse(message.contains("127.0.0.1"), "mensagem não pode conter o host");
    }

    @Test
    void returnsBalanceInWei() throws Exception {
        String url = startRpc(200, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x3782dace9d900000\"}");

        BigInteger wei = new Web3jEthereumClient(url, TIMEOUT_SECONDS).getBalance(ADDRESS);

        assertEquals(new BigInteger("4000000000000000000"), wei);
    }

    @Test
    void http429BecomesRpcException() throws Exception {
        String url = startRpc(429, "{\"error\":\"rate limited\"}");
        EthereumClient client = new Web3jEthereumClient(url, TIMEOUT_SECONDS);

        RpcException e = assertThrows(RpcException.class, () -> client.getBalance(ADDRESS));
        assertMessageHidesUrl(e, url);
    }

    @Test
    void http500BecomesRpcException() throws Exception {
        String url = startRpc(500, "erro interno do provedor");
        EthereumClient client = new Web3jEthereumClient(url, TIMEOUT_SECONDS);

        RpcException e = assertThrows(RpcException.class, () -> client.getBalance(ADDRESS));
        assertMessageHidesUrl(e, url);
    }

    @Test
    void jsonRpcErrorBecomesRpcException() throws Exception {
        String url = startRpc(200,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32602,\"message\":\"invalid argument\"}}");
        EthereumClient client = new Web3jEthereumClient(url, TIMEOUT_SECONDS);

        RpcException e = assertThrows(RpcException.class, () -> client.getBalance(ADDRESS));
        assertMessageHidesUrl(e, url);
    }

    @Test
    void connectionRefusedBecomesRpcException() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort(); // ao sair do try, a porta fica fechada
        }
        String url = "http://127.0.0.1:" + closedPort + "/" + TOKEN;
        EthereumClient client = new Web3jEthereumClient(url, TIMEOUT_SECONDS);

        RpcException e = assertThrows(RpcException.class, () -> client.getBalance(ADDRESS));
        assertMessageHidesUrl(e, url);
    }
}
