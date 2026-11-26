package com.lemma.blockchain.socket;

import com.lemma.socket.Message;
import com.lemma.socket.ShutdownNotice;
import com.lemma.socket.SubscribeRequest;
import com.lemma.socket.TaskCompletedNotice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Canal de socket (CT27) com sockets reais em 127.0.0.1, porta livre (0), sem Docker.
 * O cliente de teste faz o que o Core API faz: ObjectOutputStream antes do
 * ObjectInputStream, filtro de desserialização, SubscribeRequest primeiro.
 */
class SocketServerTest {

    private static final String SERVICE_KEY = "chave-de-teste";

    /**
     * Filtro do lado do cliente (o que o Core API deve usar, ver CT27). Sem maxbytes
     * nem maxrefs: no JDK os dois contam o stream inteiro (o reset() não zera) e
     * derrubariam a conexão depois de alguns avisos. maxdepth vale por objeto.
     */
    static final String CLIENT_FILTER = "maxdepth=5;com.lemma.socket.*;java.lang.String;!*";

    private final List<Partner> subscribers = new ArrayList<>();
    private final TaskNoticeBroadcaster broadcaster = new TaskNoticeBroadcaster(subscribers);
    private final List<TestClient> clients = new ArrayList<>();
    private ConnectionAcceptor acceptor;

    @BeforeEach
    void startServer() throws IOException {
        acceptor = new ConnectionAcceptor(0, SERVICE_KEY, subscribers);
        acceptor.start();
    }

    @AfterEach
    void stopServer() {
        acceptor.close();
        broadcaster.shutdown();
        clients.forEach(TestClient::close);
    }

    // ---------- apoio ----------

    /** Cliente do jeito do exemplo do professor. */
    private class TestClient {
        final Socket socket;
        final ObjectOutputStream out;
        final ObjectInputStream in;

        TestClient() throws IOException {
            socket = new Socket("127.0.0.1", acceptor.getPort());
            socket.setSoTimeout(3000);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            in.setObjectInputFilter(java.io.ObjectInputFilter.Config.createFilter(CLIENT_FILTER));
            clients.add(this);
        }

        void send(Object object) throws IOException {
            out.writeObject(object);
            out.flush();
        }

        Object read() throws IOException, ClassNotFoundException {
            return in.readObject();
        }

        void close() {
            try {
                socket.close();
            } catch (IOException e) {
                // nada a fazer
            }
        }
    }

    private TestClient subscribedClient() throws IOException {
        TestClient client = new TestClient();
        client.send(new SubscribeRequest(SERVICE_KEY));
        return client;
    }

    /** O registro acontece na thread da ConnectionSupervisor: espera até 2 s. */
    private void awaitSubscribers(int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            synchronized (subscribers) {
                if (subscribers.size() == expected) {
                    return;
                }
            }
            Thread.sleep(10);
        }
        synchronized (subscribers) {
            assertEquals(expected, subscribers.size(), "número de assinantes");
        }
    }

    /** A conexão foi fechada pelo servidor sem mandar nada. */
    private static void assertClosedWithoutMessage(TestClient client) {
        assertThrows(IOException.class, client::read);
    }

    private static TaskCompletedNotice notice(String taskId) {
        return new TaskCompletedNotice(taskId, "CONFIRMED",
                "0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060", null,
                "2026-10-20T14:36:10Z");
    }

    // ---------- assinatura ----------

    @Test
    void validSubscriptionReceivesBroadcastWithAllFields() throws Exception {
        TestClient client = subscribedClient();
        awaitSubscribers(1);

        broadcaster.broadcast(new TaskCompletedNotice("task-1", "FAILED", "0xabc", "TRANSACTION_REVERTED",
                "2026-10-20T14:36:10Z"));
        broadcaster.broadcast(new TaskCompletedNotice("task-2", "FAILED", null, "INTERNAL_ERROR",
                "2026-10-20T14:36:11Z"));

        TaskCompletedNotice first = assertInstanceOf(TaskCompletedNotice.class, client.read());
        assertEquals("task-1", first.getTaskId());
        assertEquals("FAILED", first.getStatus());
        assertEquals("0xabc", first.getTransactionHash());
        assertEquals("TRANSACTION_REVERTED", first.getFailureReason());
        assertEquals("2026-10-20T14:36:10Z", first.getUpdatedAt());

        TaskCompletedNotice second = assertInstanceOf(TaskCompletedNotice.class, client.read());
        assertEquals("task-2", second.getTaskId());
        assertNull(second.getTransactionHash());
        assertEquals("INTERNAL_ERROR", second.getFailureReason());
    }

    @Test
    void wrongKeyIsClosedAndReceivesNothing() throws Exception {
        TestClient client = new TestClient();
        client.send(new SubscribeRequest("chave-errada"));

        assertClosedWithoutMessage(client);
        broadcaster.broadcast(notice("task-1"));
        awaitSubscribers(0);
    }

    @Test
    void firstMessageOfAnotherTypeIsRefused() throws Exception {
        TestClient client = new TestClient();
        client.send(new ShutdownNotice());

        assertClosedWithoutMessage(client);
        awaitSubscribers(0);
    }

    @Test
    void objectOfNotAllowedClassIsRefusedByTheFilter() throws Exception {
        TestClient client = new TestClient();
        client.send(new ArrayList<>(List.of("a", "b")));

        assertClosedWithoutMessage(client);
        awaitSubscribers(0);
    }

    @Test
    void messageLargerThanTheLimitIsRefused() throws Exception {
        TestClient client = new TestClient();
        client.send(new SubscribeRequest("x".repeat(10_000)));

        assertClosedWithoutMessage(client);
        awaitSubscribers(0);
    }

    @Test
    void notAllowedClassAfterSubscribingClosesTheConnection() throws Exception {
        TestClient client = subscribedClient();
        awaitSubscribers(1);

        try {
            client.send(new java.util.Date());
        } catch (IOException e) {
            // o servidor pode recusar e fechar antes de o cliente terminar de escrever
        }

        assertClosedWithoutMessage(client);
        awaitSubscribers(0);
    }

    // ---------- broadcast ----------

    @Test
    void broadcastWithoutSubscribersDoesNotFail() {
        assertDoesNotThrow(() -> broadcaster.broadcast(notice("task-1")));
    }

    @Test
    void subscriberThatDropsIsRemovedWithoutAffectingTheOthers() throws Exception {
        TestClient dropping = subscribedClient();
        TestClient staying = subscribedClient();
        awaitSubscribers(2);

        dropping.close();
        awaitSubscribers(1);
        broadcaster.broadcast(notice("task-1"));

        TaskCompletedNotice received = assertInstanceOf(TaskCompletedNotice.class, staying.read());
        assertEquals("task-1", received.getTaskId());
    }

    /**
     * Conexões caem enquanto o worker avisa: supervisor e broadcaster sincronizam na
     * mesma lista, então não há ConcurrentModificationException, os mortos saem da
     * lista e o assinante vivo (o último da lista) recebe todos os avisos, em ordem.
     */
    @Test
    void subscribersDroppingDuringBroadcastDoNotDisturbTheLiveOne() throws Exception {
        List<TestClient> dropping = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            dropping.add(subscribedClient());
            awaitSubscribers(i + 1);
        }
        TestClient live = subscribedClient();
        awaitSubscribers(6);

        int rounds = 300; // bem acima do que caberia num maxbytes=4096 acumulado
        Thread worker = new Thread(() -> {
            for (int i = 0; i < rounds; i++) {
                broadcaster.broadcast(notice("task-" + i));
            }
        });
        worker.start();
        for (TestClient client : dropping) {
            client.close();
            Thread.sleep(2);
        }

        for (int i = 0; i < rounds; i++) {
            TaskCompletedNotice received = assertInstanceOf(TaskCompletedNotice.class, live.read());
            assertEquals("task-" + i, received.getTaskId());
        }
        worker.join(5000);
        awaitSubscribers(1);
    }

    // ---------- desligamento ----------

    @Test
    void shutdownSendsShutdownNoticeAndCloses() throws Exception {
        TestClient first = subscribedClient();
        TestClient second = subscribedClient();
        awaitSubscribers(2);

        broadcaster.shutdown();

        for (TestClient client : List.of(first, second)) {
            Message message = assertInstanceOf(Message.class, client.read());
            assertInstanceOf(ShutdownNotice.class, message);
            assertClosedWithoutMessage(client);
        }
        synchronized (subscribers) {
            assertTrue(subscribers.isEmpty());
        }
    }

    @Test
    void subscribeRequestNeverPrintsTheKey() {
        assertTrue(!new SubscribeRequest(SERVICE_KEY).toString().contains(SERVICE_KEY));
        assertEquals("TaskCompletedNotice[taskId=task-1, status=CONFIRMED]", notice("task-1").toString());
    }
}
