package com.lemma.blockchain.service;

import com.lemma.blockchain.client.ReceiptInfo;
import com.lemma.blockchain.client.RpcException;
import com.lemma.blockchain.config.AppConfig;
import com.lemma.blockchain.crypto.KeyCipher;
import com.lemma.blockchain.dto.TaskResponse;
import com.lemma.blockchain.model.FailureReason;
import com.lemma.blockchain.model.Task;
import com.lemma.blockchain.model.TaskStatus;
import com.lemma.blockchain.model.Wallet;
import com.lemma.blockchain.service.TaskWorkerTest.Stored;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static com.lemma.blockchain.service.TaskWorkerTest.hashOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de queda e retomada do worker (H09.1, fluxo E9).
 *
 * "Reinício" aqui é um TaskWorker NOVO sobre o MESMO repositório em memória, sem
 * nenhum estado do worker anterior. O banco (repositórios), a rede (RPC falso) e a
 * Core API falsa sobrevivem; só o worker é recriado, como no container.
 *
 * A queda é um {@link SimulatedCrash}, que estende Error e não RuntimeException:
 * atravessa todos os catch do worker e sai de runCycle(), como um processo que
 * morre no meio do ciclo. Nada depois do ponto da queda roda.
 *
 * Os três casos do card:
 * <ul>
 *   <li>A. Nunca enviada: PENDING sem hash.</li>
 *   <li>B. Assinada e gravada, envio incerto: PENDING com hash, a rede não conhece.</li>
 *   <li>C. Enviada: PENDING com hash, a rede conhece, ainda sem recibo.</li>
 * </ul>
 * Os fakes vêm do TaskWorkerTest; aqui só se acrescenta a queda.
 */
class TaskWorkerRestartTest {

    private static final long CHAIN_ID = AppConfig.SEPOLIA_CHAIN_ID;
    private static final String DESTINATION = "0x5b38da6a701c568545dcfcb03fcb875f56beddc4";
    private static final Instant BASE_TIME = Instant.parse("2026-11-15T10:00:00Z");
    private static final ReceiptInfo SUCCESS = new ReceiptInfo(true, BigInteger.valueOf(100));
    private static final ReceiptInfo REVERTED = new ReceiptInfo(false, BigInteger.valueOf(100));

    /** Queda do processo. É Error para não ser pega pelos catch de RuntimeException do worker. */
    static class SimulatedCrash extends Error {
        SimulatedCrash(String where) {
            super("queda simulada " + where);
        }
    }

    /** RPC falso que pode derrubar o processo no nonce ou no envio, e conta os pedidos de nonce. */
    private static class CrashingEthereumClient extends TaskWorkerTest.FakeEthereumClient {
        boolean crashOnNonce;
        boolean crashOnSend;
        int nonceQueries;

        CrashingEthereumClient(TaskWorkerTest.InMemoryTaskRepository taskRepository) {
            super(taskRepository);
        }

        @Override
        public BigInteger getTransactionCount(String address) throws RpcException {
            nonceQueries++;
            if (crashOnNonce) {
                throw new SimulatedCrash("antes de assinar");
            }
            return super.getTransactionCount(address);
        }

        /** Na queda, a transação chega ao fake (fica em sent) e o processo morre sem a resposta. */
        @Override
        public String sendRawTransaction(String signedTransactionHex) throws RpcException {
            String hash = super.sendRawTransaction(signedTransactionHex);
            if (crashOnSend) {
                throw new SimulatedCrash("no envio, sem resposta");
            }
            return hash;
        }
    }

    /** Core API falsa em que o processo pode cair logo antes de o aviso sair (nada é registrado). */
    private static class CrashingCoreApiClient extends TaskWorkerTest.RecordingCoreApiClient {
        boolean crashOnNotify;

        CrashingCoreApiClient(TaskWorkerTest.InMemoryTaskRepository taskRepository) {
            super(taskRepository);
        }

        @Override
        public void notifyTaskCompleted(Task task) {
            if (crashOnNotify) {
                throw new SimulatedCrash("antes de o aviso sair");
            }
            super.notifyTaskCompleted(task);
        }
    }

    private final TaskWorkerTest.InMemoryTaskRepository taskRepository = new TaskWorkerTest.InMemoryTaskRepository();
    private final TaskWorkerTest.InMemoryWalletRepository walletRepository = new TaskWorkerTest.InMemoryWalletRepository();
    private final KeyCipher keyCipher = new KeyCipher(randomBytes(32));
    private final CrashingEthereumClient network = new CrashingEthereumClient(taskRepository);
    private final CrashingCoreApiClient coreApi = new CrashingCoreApiClient(taskRepository);

    private int tasksCreated = 0;

    // ---------- apoio ----------

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    /** Worker sobre o banco, a rede e a Core API deste teste. Não guarda estado entre ciclos. */
    private TaskWorker newWorker() {
        return new TaskWorker(taskRepository, walletRepository, keyCipher, network, coreApi, CHAIN_ID);
    }

    /**
     * Reinício: confere que nenhuma task tinha estado só na memória (o worker novo
     * lê exatamente o que estava gravado) e devolve um worker novo.
     */
    private TaskWorker restart() {
        for (Task task : taskRepository.tasks.values()) {
            assertEquals(stored(task), Stored.of(task), "task " + task.getTaskId() + " com estado não gravado");
        }
        return newWorker();
    }

    /** Carteira gravada como o WalletService grava: chave cifrada com o endereço como AAD. */
    private String storeWallet() {
        byte[] privateKey = randomBytes(32);
        String address = "0x" + Keys.getAddress(ECKeyPair.create(privateKey)).toLowerCase(Locale.ROOT);
        walletRepository.save(new Wallet(address, keyCipher.encrypt(privateKey, address), BASE_TIME));
        return address;
    }

    /** Task PENDING sem hash, como o CT10 grava; cada nova é um segundo mais nova que a anterior. */
    private Task storeTask(String source) {
        Task task = new Task("task-" + tasksCreated, source, DESTINATION,
                new BigDecimal("0.000500000000000000"), BASE_TIME.plusSeconds(tasksCreated));
        tasksCreated++;
        taskRepository.save(task);
        return task;
    }

    /**
     * Caso C antes da queda: um worker envia a task normalmente, a rede passa a
     * conhecer o hash e o processo cai antes de o recibo aparecer.
     */
    private Task sentBeforeRestart() {
        Task task = storeTask(storeWallet());
        newWorker().runCycle();

        Stored s = stored(task);
        assertEquals(TaskStatus.PENDING, s.status());
        assertNotNull(s.transactionHash());
        network.knownHashes.add(s.transactionHash());
        return task;
    }

    private Stored stored(Task task) {
        return taskRepository.stored.get(task.getTaskId());
    }

    /** Hashes distintos de tudo o que chegou à rede. */
    private Set<String> sentHashes() {
        return network.sent.stream().map(TaskWorkerTest::hashOf).collect(Collectors.toSet());
    }

    private TaskResponse getTask(Task task) {
        return new TaskService(taskRepository, walletRepository).getTask(task.getTaskId());
    }

    /** A task foi avisada uma única vez, e o aviso viu este estado já gravado. */
    private void assertNotifiedOnce(Task task) {
        assertEquals(List.of(task.getTaskId()), coreApi.notifiedTaskIds);
        assertEquals(stored(task), coreApi.storedAtNotify.get(0), "aviso deve sair depois de gravar");
    }

    private void assertNoNotification() {
        assertTrue(coreApi.notifiedTaskIds.isEmpty(), "nenhum aviso esperado");
    }

    // ---------- caso A: nunca enviada ----------

    @Test
    void taskNeverSentIsSignedAndSentOnceAfterRestart() {
        Task task = storeTask(storeWallet());
        Stored beforeCrash = stored(task);
        network.crashOnNonce = true;
        TaskWorker oldWorker = newWorker();

        assertThrows(SimulatedCrash.class, oldWorker::runCycle);

        assertEquals(beforeCrash, stored(task), "a queda antes de assinar não grava nada");
        assertTrue(network.sent.isEmpty(), "nada foi enviado");
        assertNoNotification();

        network.crashOnNonce = false;
        restart().runCycle();

        assertEquals(2, network.nonceQueries, "o worker novo pede o nonce de novo");
        assertEquals(1, network.sent.size(), "assina e envia uma vez");
        String hex = network.sent.get(0);
        Stored s = stored(task);
        assertEquals(TaskStatus.PENDING, s.status());
        assertEquals(hashOf(hex), s.transactionHash(), "o hash gravado é o do hex enviado");
        assertEquals(hex, s.signedTransaction());
        assertNull(s.failureReason());
        assertTrue(s.updatedAt().isAfter(task.getCreatedAt()), "updatedAt deve mudar ao gravar o hash");
        assertNoNotification();
    }

    // ---------- caso B: assinada e gravada, envio incerto ----------

    @Test
    void crashWhileSendingResendsTheSameSignedTransactionAfterRestart() {
        Task task = storeTask(storeWallet());
        network.crashOnSend = true;
        TaskWorker oldWorker = newWorker();

        assertThrows(SimulatedCrash.class, oldWorker::runCycle);

        Stored atCrash = stored(task);
        assertEquals(TaskStatus.PENDING, atCrash.status());
        assertNull(atCrash.failureReason());
        assertNotNull(atCrash.signedTransaction(), "a transação assinada foi gravada antes do envio");
        assertEquals(hashOf(atCrash.signedTransaction()), atCrash.transactionHash());
        assertTrue(atCrash.updatedAt().isAfter(task.getCreatedAt()), "updatedAt deve mudar ao gravar o hash");
        assertEquals(List.of(atCrash.signedTransaction()), network.sent);
        assertNoNotification();

        // Reinício. A rede não conhece o hash: o envio não chegou.
        network.crashOnSend = false;
        TaskWorker worker = restart();
        worker.runCycle();

        assertEquals(List.of(atCrash.signedTransaction(), atCrash.signedTransaction()), network.sent,
                "reenvia a MESMA transação assinada");
        assertEquals(1, network.nonceQueries, "o reenvio não pede nonce novo");
        assertEquals(atCrash, stored(task), "continua PENDING com o mesmo hash e a mesma transação assinada");
        assertNoNotification();

        // A rede passa a conhecer o hash: nada é reenviado.
        network.knownHashes.add(atCrash.transactionHash());
        worker.runCycle();

        assertEquals(2, network.sent.size(), "a rede conhece o hash: sem reenvio");
        assertEquals(atCrash, stored(task));
        assertNoNotification();

        // Recibo 0x1: CONFIRMED e um aviso.
        network.receipts.put(atCrash.transactionHash(), SUCCESS);
        worker.runCycle();

        Stored s = stored(task);
        assertEquals(TaskStatus.CONFIRMED, s.status());
        assertNull(s.failureReason());
        assertEquals(atCrash.transactionHash(), s.transactionHash());
        assertFalse(s.updatedAt().isBefore(atCrash.updatedAt()), "updatedAt não volta no tempo");
        assertNotifiedOnce(task);
        assertEquals(Set.of(atCrash.transactionHash()), sentHashes(), "uma única transação foi à rede");
        assertEquals(1, network.nonceQueries);
    }

    /**
     * E9, passo 5: depois do reinício, o CT14 responde o último estado gravado.
     * O TaskService nem recebe o EthereumClient, então não tem como chamar o RPC;
     * o teste confere também que nenhuma chamada nova chegou ao RPC falso.
     */
    @Test
    void getTaskAfterRestartAnswersTheLastStoredStateWithoutTheRpc() {
        Task task = storeTask(storeWallet());
        network.crashOnSend = true;
        TaskWorker oldWorker = newWorker();
        assertThrows(SimulatedCrash.class, oldWorker::runCycle);
        Stored atCrash = stored(task);
        int sentBefore = network.sent.size();
        int nonceBefore = network.nonceQueries;

        TaskResponse response = getTask(task);

        assertEquals(task.getTaskId(), response.taskId());
        assertEquals(TaskStatus.PENDING, response.status());
        assertEquals(atCrash.transactionHash(), response.transactionHash());
        assertNull(response.failureReason());
        assertEquals(atCrash.updatedAt().truncatedTo(ChronoUnit.SECONDS).toString(), response.updatedAt());
        assertEquals(sentBefore, network.sent.size(), "o CT14 só lê o banco");
        assertEquals(nonceBefore, network.nonceQueries);
        assertTrue(network.receiptQueries.isEmpty());
        assertTrue(network.knownQueries.isEmpty());
    }

    // ---------- caso C: enviada, a rede conhece, sem recibo ----------

    @Test
    void sentTransactionIsNotResentAndConfirmsAfterRestart() {
        Task task = sentBeforeRestart();
        Stored atRestart = stored(task);
        TaskWorker worker = restart();

        worker.runCycle();

        assertEquals(atRestart, stored(task), "sem recibo e conhecida: nada muda");
        assertEquals(List.of(atRestart.transactionHash()), network.receiptQueries);
        assertEquals(List.of(atRestart.transactionHash()), network.knownQueries);
        assertEquals(1, network.sent.size(), "só o envio de antes da queda");
        assertEquals(1, network.nonceQueries);
        assertNoNotification();

        network.receipts.put(atRestart.transactionHash(), SUCCESS);
        worker.runCycle();

        Stored s = stored(task);
        assertEquals(TaskStatus.CONFIRMED, s.status());
        assertNull(s.failureReason());
        assertEquals(atRestart.transactionHash(), s.transactionHash());
        assertFalse(s.updatedAt().isBefore(atRestart.updatedAt()), "updatedAt não volta no tempo");
        assertNotifiedOnce(task);
        assertEquals(1, network.sent.size());
    }

    @Test
    void sentTransactionRevertedAfterRestartFailsKeepingHash() {
        Task task = sentBeforeRestart();
        Stored atRestart = stored(task);
        TaskWorker worker = restart();

        worker.runCycle();

        assertEquals(atRestart, stored(task));
        assertNoNotification();

        network.receipts.put(atRestart.transactionHash(), REVERTED);
        worker.runCycle();

        Stored s = stored(task);
        assertEquals(TaskStatus.FAILED, s.status());
        assertEquals(FailureReason.TRANSACTION_REVERTED, s.failureReason());
        assertEquals(atRestart.transactionHash(), s.transactionHash(), "minerada: o hash fica");
        assertEquals(atRestart.signedTransaction(), s.signedTransaction());
        assertFalse(s.updatedAt().isBefore(atRestart.updatedAt()), "updatedAt não volta no tempo");
        assertNotifiedOnce(task);
        assertEquals(1, network.sent.size(), "nada é reenviado");
    }

    // ---------- mesma carteira: caso C e caso A ----------

    @Test
    void secondTaskOfTheSameWalletWaitsForTheFirstAfterRestart() {
        String source = storeWallet();
        Task first = storeTask(source);
        Task second = storeTask(source);
        newWorker().runCycle();
        Stored firstAtRestart = stored(first);
        Stored secondAtRestart = stored(second);
        assertNotNull(firstAtRestart.transactionHash());
        assertNull(secondAtRestart.transactionHash(), "uma task por carteira: a segunda ainda espera");
        network.knownHashes.add(firstAtRestart.transactionHash());

        TaskWorker worker = restart();
        worker.runCycle();

        assertEquals(1, network.sent.size(), "a segunda não sai enquanto a primeira está PENDING com hash");
        assertEquals(1, network.nonceQueries, "nem pede nonce: evita o nonce repetido");
        assertEquals(firstAtRestart, stored(first));
        assertEquals(secondAtRestart, stored(second));
        assertNoNotification();

        // A primeira confirma: a carteira fica livre e a segunda sai no mesmo ciclo.
        network.receipts.put(firstAtRestart.transactionHash(), SUCCESS);
        worker.runCycle();

        assertEquals(TaskStatus.CONFIRMED, stored(first).status());
        assertNotifiedOnce(first);
        assertEquals(2, network.sent.size());
        assertEquals(2, network.nonceQueries);
        Stored s = stored(second);
        assertEquals(TaskStatus.PENDING, s.status());
        assertEquals(hashOf(network.sent.get(1)), s.transactionHash());
        assertEquals(network.sent.get(1), s.signedTransaction());
        assertTrue(s.updatedAt().isAfter(second.getCreatedAt()), "updatedAt deve mudar ao gravar o hash");
    }

    // ---------- aviso perdido ----------

    /**
     * Documenta o comportamento ATUAL, sem retentativa do webhook (P4 em aberto;
     * decisão 8, limitação 4, do desenho): se o processo cai entre gravar o estado
     * final e avisar, o aviso se perde. O worker novo não acompanha nem avisa a task
     * de novo; a Core API recupera o estado pelo CT14. Se a retentativa for
     * decidida, este teste precisa mudar.
     */
    @Test
    void lostNotificationIsNotResentAfterRestart() {
        Task task = sentBeforeRestart();
        String hash = stored(task).transactionHash();
        network.receipts.put(hash, SUCCESS);
        coreApi.crashOnNotify = true;
        TaskWorker oldWorker = newWorker();

        assertThrows(SimulatedCrash.class, oldWorker::runCycle);

        Stored atCrash = stored(task);
        assertEquals(TaskStatus.CONFIRMED, atCrash.status(), "o estado final foi gravado antes da queda");
        assertEquals(hash, atCrash.transactionHash());
        assertNoNotification();
        int receiptQueriesAtCrash = network.receiptQueries.size();

        // A Core API volta; o worker novo não sabe do aviso perdido.
        coreApi.crashOnNotify = false;
        restart().runCycle();

        assertEquals(atCrash, stored(task));
        assertEquals(receiptQueriesAtCrash, network.receiptQueries.size(), "task final não é acompanhada de novo");
        assertNoNotification();

        TaskResponse response = getTask(task);
        assertEquals(TaskStatus.CONFIRMED, response.status(), "a Core API recupera pelo CT14");
        assertEquals(hash, response.transactionHash());
    }
}
