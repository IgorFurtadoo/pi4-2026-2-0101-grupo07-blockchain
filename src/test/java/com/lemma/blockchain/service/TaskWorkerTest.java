package com.lemma.blockchain.service;

import com.lemma.blockchain.client.CoreApiClient;
import com.lemma.blockchain.client.EthereumClient;
import com.lemma.blockchain.client.ReceiptInfo;
import com.lemma.blockchain.client.RpcException;
import com.lemma.blockchain.client.RpcRejectedException;
import com.lemma.blockchain.client.RpcUnavailableException;
import com.lemma.blockchain.config.AppConfig;
import com.lemma.blockchain.crypto.KeyCipher;
import com.lemma.blockchain.model.FailureReason;
import com.lemma.blockchain.model.Task;
import com.lemma.blockchain.model.TaskStatus;
import com.lemma.blockchain.model.Wallet;
import com.lemma.blockchain.repository.TaskRepository;
import com.lemma.blockchain.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa as fases A e B do worker sem banco e sem rede, chamando runCycle() direto.
 *
 * Mesmo padrão do TaskServiceTest e do WalletServiceTest: os repositories são
 * subclasses com super(null) que guardam tudo em memória. O de task guarda um
 * retrato do estado a cada save/update, como o banco: assim os testes de ordem
 * conferem o que foi gravado, e não o objeto alterado em memória. O webhook é
 * uma subclasse do CoreApiClient que só registra, sem HTTP.
 */
class TaskWorkerTest {

    private static final long CHAIN_ID = AppConfig.SEPOLIA_CHAIN_ID;
    private static final String DESTINATION = "0x5b38da6a701c568545dcfcb03fcb875f56beddc4";
    private static final Instant BASE_TIME = Instant.parse("2026-11-08T10:00:00Z");
    private static final BigInteger NONCE = BigInteger.valueOf(7);
    private static final BigInteger GAS_PRICE = BigInteger.valueOf(1_000_000_000L);

    /** Estado de uma task como estaria gravado no banco. */
    record Stored(TaskStatus status, String transactionHash, String signedTransaction,
                  FailureReason failureReason, Instant updatedAt) {

        static Stored of(Task task) {
            return new Stored(task.getStatus(), task.getTransactionHash(), task.getSignedTransaction(),
                    task.getFailureReason(), task.getUpdatedAt());
        }
    }

    private static class InMemoryTaskRepository extends TaskRepository {
        final Map<String, Task> tasks = new LinkedHashMap<>();
        final Map<String, Stored> stored = new HashMap<>();

        InMemoryTaskRepository() {
            super(null);
        }

        @Override
        public void save(Task task) {
            tasks.put(task.getTaskId(), task);
            stored.put(task.getTaskId(), Stored.of(task));
        }

        @Override
        public void update(Task task) {
            stored.put(task.getTaskId(), Stored.of(task));
        }

        @Override
        public Optional<Task> findById(String taskId) {
            return Optional.ofNullable(tasks.get(taskId));
        }

        @Override
        public List<Task> findPendingWithoutHash() {
            return tasks.values().stream()
                    .filter(task -> stored.get(task.getTaskId()).status() == TaskStatus.PENDING)
                    .filter(task -> stored.get(task.getTaskId()).transactionHash() == null)
                    .sorted(Comparator.comparing(Task::getCreatedAt))
                    .toList();
        }

        @Override
        public List<Task> findPendingWithHash() {
            return tasks.values().stream()
                    .filter(task -> stored.get(task.getTaskId()).status() == TaskStatus.PENDING)
                    .filter(task -> stored.get(task.getTaskId()).transactionHash() != null)
                    .sorted(Comparator.comparing(Task::getCreatedAt))
                    .toList();
        }

        @Override
        public boolean existsPendingWithHashFrom(String sourceAddress) {
            return tasks.values().stream()
                    .filter(task -> task.getSourceAddress().equals(sourceAddress))
                    .map(task -> stored.get(task.getTaskId()))
                    .anyMatch(s -> s.status() == TaskStatus.PENDING && s.transactionHash() != null);
        }

        /** Retrato gravado da task que tem esta transação assinada, ou null. */
        Stored storedWithSignedTransaction(String signedHex) {
            return stored.values().stream()
                    .filter(s -> signedHex.equals(s.signedTransaction()))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static class InMemoryWalletRepository extends WalletRepository {
        final List<Wallet> saved = new ArrayList<>();
        /** Endereços cuja leitura lança exceção, como um banco fora do ar. */
        final Set<String> broken = new HashSet<>();

        InMemoryWalletRepository() {
            super(null);
        }

        @Override
        public void save(Wallet wallet) {
            saved.add(wallet);
        }

        @Override
        public Optional<Wallet> findByAddress(String ethereumAddress) {
            if (broken.contains(ethereumAddress)) {
                throw new RuntimeException("falha simulada de banco");
            }
            return saved.stream()
                    .filter(wallet -> wallet.getEthereumAddress().equals(ethereumAddress))
                    .findFirst();
        }
    }

    /**
     * RPC falso. No envio, guarda o hex recebido e o retrato da task gravado
     * naquele instante (para o teste de ordem), e então responde ou falha.
     * Recibos, hashes conhecidos e falhas da Fase A são configurados por hash;
     * falhas de reenvio, pela transação assinada.
     */
    private static class FakeEthereumClient implements EthereumClient {
        private final InMemoryTaskRepository taskRepository;
        RpcException nonceFailure;
        RpcException gasFailure;
        RpcException sendFailure;
        /** Hash que o nó devolve; null = o hash verdadeiro da transação recebida. */
        String nodeHash;
        final List<String> sent = new ArrayList<>();
        final List<Stored> storedAtSend = new ArrayList<>();
        final Map<String, RpcException> sendFailures = new HashMap<>();

        final Map<String, ReceiptInfo> receipts = new HashMap<>();
        final Map<String, RpcException> receiptFailures = new HashMap<>();
        final List<String> receiptQueries = new ArrayList<>();

        final Set<String> knownHashes = new HashSet<>();
        final Map<String, RpcException> knownFailures = new HashMap<>();
        final List<String> knownQueries = new ArrayList<>();

        FakeEthereumClient(InMemoryTaskRepository taskRepository) {
            this.taskRepository = taskRepository;
        }

        @Override
        public BigInteger getBalance(String address) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BigInteger getTransactionCount(String address) throws RpcException {
            if (nonceFailure != null) {
                throw nonceFailure;
            }
            return NONCE;
        }

        @Override
        public BigInteger getGasPrice() throws RpcException {
            if (gasFailure != null) {
                throw gasFailure;
            }
            return GAS_PRICE;
        }

        @Override
        public String sendRawTransaction(String signedTransactionHex) throws RpcException {
            sent.add(signedTransactionHex);
            storedAtSend.add(taskRepository.storedWithSignedTransaction(signedTransactionHex));
            if (sendFailures.containsKey(signedTransactionHex)) {
                throw sendFailures.get(signedTransactionHex);
            }
            if (sendFailure != null) {
                throw sendFailure;
            }
            return nodeHash != null ? nodeHash : hashOf(signedTransactionHex);
        }

        @Override
        public Optional<ReceiptInfo> getTransactionReceipt(String txHash) throws RpcException {
            receiptQueries.add(txHash);
            if (receiptFailures.containsKey(txHash)) {
                throw receiptFailures.get(txHash);
            }
            return Optional.ofNullable(receipts.get(txHash));
        }

        @Override
        public boolean isTransactionKnown(String txHash) throws RpcException {
            knownQueries.add(txHash);
            if (knownFailures.containsKey(txHash)) {
                throw knownFailures.get(txHash);
            }
            return knownHashes.contains(txHash);
        }
    }

    /**
     * Webhook falso: em cada aviso, guarda o retrato da task gravado naquele
     * instante (para conferir que o estado final já estava no banco) e, se
     * configurado, lança a falha.
     */
    private static class RecordingCoreApiClient extends CoreApiClient {
        private final InMemoryTaskRepository taskRepository;
        final List<String> notifiedTaskIds = new ArrayList<>();
        final List<Stored> storedAtNotify = new ArrayList<>();
        RuntimeException failure;

        RecordingCoreApiClient(InMemoryTaskRepository taskRepository) {
            super("http://127.0.0.1:1/webhooks/tasks", "chave-de-teste", Duration.ofSeconds(1));
            this.taskRepository = taskRepository;
        }

        @Override
        public void notifyTaskCompleted(Task task) {
            notifiedTaskIds.add(task.getTaskId());
            storedAtNotify.add(taskRepository.stored.get(task.getTaskId()));
            if (failure != null) {
                throw failure;
            }
        }
    }

    private final InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
    private final InMemoryWalletRepository walletRepository = new InMemoryWalletRepository();
    private final KeyCipher keyCipher = new KeyCipher(randomBytes(32));
    private final FakeEthereumClient ethereumClient = new FakeEthereumClient(taskRepository);
    private final RecordingCoreApiClient coreApiClient = new RecordingCoreApiClient(taskRepository);
    private final TaskWorker worker =
            new TaskWorker(taskRepository, walletRepository, keyCipher, ethereumClient, coreApiClient, CHAIN_ID);

    private int tasksCreated = 0;

    // ---------- apoio ----------

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private static String hashOf(String signedHex) {
        return Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(signedHex)));
    }

    /** Endereço canônico ("0x" + minúsculo) de uma chave privada. */
    private static String addressOf(byte[] privateKey) {
        return "0x" + Keys.getAddress(ECKeyPair.create(privateKey)).toLowerCase(Locale.ROOT);
    }

    /** Carteira gravada como o WalletService grava: chave cifrada com o endereço como AAD. */
    private String storeWallet() {
        return storeWallet(keyCipher, null);
    }

    /**
     * Grava uma carteira cifrando a chave com o cipher e o AAD dados
     * (aad null = o próprio endereço, que é o correto).
     */
    private String storeWallet(KeyCipher cipher, String aad) {
        byte[] privateKey = randomBytes(32);
        String address = addressOf(privateKey);
        String encrypted = cipher.encrypt(privateKey, aad == null ? address : aad);
        walletRepository.save(new Wallet(address, encrypted, BASE_TIME));
        return address;
    }

    /** Task PENDING de 0.01 ETH; cada nova é um segundo mais nova que a anterior. */
    private Task storeTask(String source) {
        Task task = new Task("task-" + tasksCreated, source, DESTINATION,
                new BigDecimal("0.010000000000000000"), BASE_TIME.plusSeconds(tasksCreated));
        tasksCreated++;
        taskRepository.save(task);
        return task;
    }

    /**
     * Task já assinada pela Fase B: PENDING com hash e transação assinada gravados.
     * O hex é fictício (a Fase A não o decodifica), mas o hash é o dele.
     */
    private Task storeSentTask(String source) {
        Task task = storeTask(source);
        String signedHex = String.format("0xf86c07%02x", tasksCreated);
        task.markSigned(hashOf(signedHex), signedHex, task.getCreatedAt());
        taskRepository.update(task);
        return task;
    }

    private Stored stored(Task task) {
        return taskRepository.stored.get(task.getTaskId());
    }

    /** A task foi avisada uma única vez, e o aviso viu este estado já gravado. */
    private void assertNotifiedOnce(Task task) {
        assertEquals(List.of(task.getTaskId()), coreApiClient.notifiedTaskIds);
        assertEquals(stored(task), coreApiClient.storedAtNotify.get(0), "aviso deve sair depois de gravar");
    }

    private void assertNoNotification() {
        assertTrue(coreApiClient.notifiedTaskIds.isEmpty(), "nenhum aviso esperado");
    }

    private void assertFailedWithoutSending(Task task, FailureReason reason) {
        Stored s = stored(task);
        assertEquals(TaskStatus.FAILED, s.status());
        assertEquals(reason, s.failureReason());
        assertNull(s.transactionHash());
        assertNull(s.signedTransaction());
        assertTrue(s.updatedAt().isAfter(task.getCreatedAt()), "updatedAt deve mudar");
        assertTrue(ethereumClient.sent.isEmpty(), "nada pode ser enviado");
        assertNotifiedOnce(task);
    }

    // ---------- caminho feliz ----------

    @Test
    void signsLegacyTransactionWithTheWalletKey() throws Exception {
        String source = storeWallet();
        Task task = storeTask(source);

        worker.runCycle();

        assertEquals(1, ethereumClient.sent.size());
        String hex = ethereumClient.sent.get(0);
        RawTransaction decoded = TransactionDecoder.decode(hex);
        SignedRawTransaction signed = assertInstanceOf(SignedRawTransaction.class, decoded);

        assertEquals(source, signed.getFrom().toLowerCase(Locale.ROOT));
        assertEquals(DESTINATION, signed.getTo().toLowerCase(Locale.ROOT));
        assertEquals(new BigInteger("10000000000000000"), signed.getValue()); // 0.01 ETH em wei
        assertEquals(NONCE, signed.getNonce());
        assertEquals(GAS_PRICE, signed.getGasPrice());
        assertEquals(TaskWorker.GAS_LIMIT, signed.getGasLimit());
        assertEquals(BigInteger.valueOf(500_000), signed.getGasLimit());
        assertEquals(CHAIN_ID, signed.getChainId());

        Stored s = stored(task);
        assertEquals(TaskStatus.PENDING, s.status());
        assertEquals(hashOf(hex), s.transactionHash());
        assertEquals(hex, s.signedTransaction());
        assertNull(s.failureReason());
        assertNoNotification();
    }

    @Test
    void hashAndSignedTransactionAreStoredBeforeSending() {
        Task task = storeTask(storeWallet());

        worker.runCycle();

        assertEquals(1, ethereumClient.storedAtSend.size());
        Stored atSend = ethereumClient.storedAtSend.get(0);
        assertNotNull(atSend, "no momento do envio, a transação assinada já devia estar gravada");
        String hex = ethereumClient.sent.get(0);
        assertEquals(TaskStatus.PENDING, atSend.status());
        assertEquals(hex, atSend.signedTransaction());
        assertEquals(hashOf(hex), atSend.transactionHash());
        assertTrue(atSend.updatedAt().isAfter(task.getCreatedAt()), "updatedAt deve mudar ao gravar o hash");
    }

    // ---------- envio ----------

    @Test
    void networkRejectionFailsAndErasesHashAndSignedTransaction() {
        Task task = storeTask(storeWallet());
        ethereumClient.sendFailure = new RpcRejectedException(-32000, "insufficient funds for gas * price + value");

        worker.runCycle();

        assertEquals(1, ethereumClient.sent.size());
        Stored s = stored(task);
        assertEquals(TaskStatus.FAILED, s.status());
        assertEquals(FailureReason.REJECTED_BY_NETWORK, s.failureReason());
        assertNull(s.transactionHash());
        assertNull(s.signedTransaction());
        assertNotifiedOnce(task); // o aviso sai sem hash
    }

    @Test
    void connectionFailureOnSendKeepsPendingWithHash() {
        Task task = storeTask(storeWallet());
        ethereumClient.sendFailure = new RpcUnavailableException("falha simulada");

        worker.runCycle();

        String hex = ethereumClient.sent.get(0);
        Stored s = stored(task);
        assertEquals(TaskStatus.PENDING, s.status());
        assertNull(s.failureReason());
        assertEquals(hashOf(hex), s.transactionHash());
        assertEquals(hex, s.signedTransaction());
        assertNoNotification();
    }

    @Test
    void differentHashFromNodeKeepsTheLocalHash() {
        Task task = storeTask(storeWallet());
        ethereumClient.nodeHash = "0x" + "ab".repeat(32);

        worker.runCycle();

        assertEquals(hashOf(ethereumClient.sent.get(0)), stored(task).transactionHash());
    }

    // ---------- falhas antes do envio ----------

    @Test
    void nonceFailureFailsWithInternalError() {
        Task task = storeTask(storeWallet());
        ethereumClient.nonceFailure = new RpcUnavailableException("falha simulada");

        worker.runCycle();

        assertFailedWithoutSending(task, FailureReason.INTERNAL_ERROR);
    }

    @Test
    void gasPriceFailureFailsWithInternalError() {
        Task task = storeTask(storeWallet());
        ethereumClient.gasFailure = new RpcRejectedException(-32603, "internal error");

        worker.runCycle();

        assertFailedWithoutSending(task, FailureReason.INTERNAL_ERROR);
    }

    @Test
    void keyEncryptedWithAnotherAddressAsAadFailsWithInternalError() {
        String otherAddress = "0x0000000000000000000000000000000000000001";
        Task task = storeTask(storeWallet(keyCipher, otherAddress));

        worker.runCycle();

        assertFailedWithoutSending(task, FailureReason.INTERNAL_ERROR);
    }

    @Test
    void keyEncryptedWithAnotherEncryptionKeyFailsWithInternalError() {
        KeyCipher otherCipher = new KeyCipher(randomBytes(32));
        Task task = storeTask(storeWallet(otherCipher, null));

        worker.runCycle();

        assertFailedWithoutSending(task, FailureReason.INTERNAL_ERROR);
    }

    @Test
    void missingWalletFailsWithInternalError() {
        Task task = storeTask("0x0000000000000000000000000000000000000002");

        worker.runCycle();

        assertFailedWithoutSending(task, FailureReason.INTERNAL_ERROR);
    }

    // ---------- uma task por carteira ----------

    @Test
    void secondTaskOfSameWalletWaitsWhileFirstHasHash() {
        String source = storeWallet();
        Task first = storeTask(source);
        Task second = storeTask(source);

        worker.runCycle();
        // Segundo ciclo: a primeira está na rede, sem recibo; continua PENDING com hash
        // e a segunda segue esperando.
        ethereumClient.knownHashes.add(stored(first).transactionHash());
        worker.runCycle();

        assertEquals(1, ethereumClient.sent.size());
        assertNotNull(stored(first).transactionHash());
        assertEquals(TaskStatus.PENDING, stored(second).status());
        assertNull(stored(second).transactionHash());
    }

    @Test
    void differentWalletsAdvanceInTheSameCycle() {
        Task first = storeTask(storeWallet());
        Task second = storeTask(storeWallet());

        worker.runCycle();

        assertEquals(2, ethereumClient.sent.size());
        assertNotNull(stored(first).transactionHash());
        assertNotNull(stored(second).transactionHash());
    }

    @Test
    void unexpectedFailureInOneTaskDoesNotStopTheOthers() {
        String brokenSource = storeWallet();
        walletRepository.broken.add(brokenSource);
        Task broken = storeTask(brokenSource);
        Task healthy = storeTask(storeWallet());
        Stored brokenBefore = stored(broken);

        worker.runCycle();

        assertEquals(brokenBefore, stored(broken), "estado mantido: PENDING sem hash, para o próximo ciclo");
        assertEquals(1, ethereumClient.sent.size());
        assertNotNull(stored(healthy).transactionHash());
    }

    // ---------- Fase A: recibo ----------

    @Test
    void successfulReceiptConfirmsKeepsHashAndNotifies() {
        Task task = storeSentTask(storeWallet());
        Stored before = stored(task);
        ethereumClient.receipts.put(before.transactionHash(), new ReceiptInfo(true, BigInteger.valueOf(100)));

        worker.runCycle();

        Stored s = stored(task);
        assertEquals(TaskStatus.CONFIRMED, s.status());
        assertNull(s.failureReason());
        assertEquals(before.transactionHash(), s.transactionHash());
        assertEquals(before.signedTransaction(), s.signedTransaction());
        assertTrue(s.updatedAt().isAfter(before.updatedAt()), "updatedAt deve mudar");
        assertNotifiedOnce(task);
        assertEquals(TaskStatus.CONFIRMED, coreApiClient.storedAtNotify.get(0).status());
        assertTrue(ethereumClient.sent.isEmpty(), "com recibo, nada é reenviado");
        assertTrue(ethereumClient.knownQueries.isEmpty(), "com recibo, não consulta o hash");
    }

    @Test
    void revertedReceiptFailsWithTransactionRevertedKeepingHash() {
        Task task = storeSentTask(storeWallet());
        Stored before = stored(task);
        ethereumClient.receipts.put(before.transactionHash(), new ReceiptInfo(false, BigInteger.valueOf(100)));

        worker.runCycle();

        Stored s = stored(task);
        assertEquals(TaskStatus.FAILED, s.status());
        assertEquals(FailureReason.TRANSACTION_REVERTED, s.failureReason());
        assertEquals(before.transactionHash(), s.transactionHash());
        assertTrue(s.updatedAt().isAfter(before.updatedAt()), "updatedAt deve mudar");
        assertNotifiedOnce(task);
    }

    @Test
    void notificationSeesTheFinalStateAlreadyStored() {
        Task task = storeSentTask(storeWallet());
        ethereumClient.receipts.put(stored(task).transactionHash(), new ReceiptInfo(true, BigInteger.ONE));

        worker.runCycle();

        Stored atNotify = coreApiClient.storedAtNotify.get(0);
        assertEquals(TaskStatus.CONFIRMED, atNotify.status(), "o aviso só sai depois de gravar o estado final");
        assertEquals(stored(task), atNotify);
    }

    // ---------- Fase A: sem recibo ----------

    @Test
    void knownTransactionWithoutReceiptChangesNothing() {
        Task task = storeSentTask(storeWallet());
        Stored before = stored(task);
        ethereumClient.knownHashes.add(before.transactionHash());

        worker.runCycle();

        assertEquals(before, stored(task));
        assertEquals(List.of(before.transactionHash()), ethereumClient.knownQueries);
        assertTrue(ethereumClient.sent.isEmpty(), "a rede conhece: nada é reenviado");
        assertNoNotification();
    }

    @Test
    void unknownTransactionIsResentOnceWithTheSameSignedHex() {
        Task task = storeSentTask(storeWallet());
        Stored before = stored(task);

        worker.runCycle();

        assertEquals(List.of(before.signedTransaction()), ethereumClient.sent, "reenvia a MESMA transação, uma vez");
        assertEquals(before, stored(task), "continua PENDING com hash e transação assinada");
        assertNoNotification();
    }

    @Test
    void resendAnsweredAlreadyKnownKeepsPending() {
        assertResendErrorKeepsPending("already known");
    }

    @Test
    void resendAnsweredNonceTooLowKeepsPending() {
        assertResendErrorKeepsPending("nonce too low: next nonce 8, tx nonce 7");
    }

    @Test
    void resendAnsweredKnownTransactionKeepsPending() {
        assertResendErrorKeepsPending("Known transaction: 5c504ed4");
    }

    /** O nó diz que a transação já está ou esteve na rede: nada muda, nada é avisado. */
    private void assertResendErrorKeepsPending(String nodeMessage) {
        Task task = storeSentTask(storeWallet());
        Stored before = stored(task);
        ethereumClient.sendFailures.put(before.signedTransaction(), new RpcRejectedException(-32000, nodeMessage));

        worker.runCycle();

        assertEquals(1, ethereumClient.sent.size());
        assertEquals(before, stored(task), "PENDING, hash e transação assinada intactos");
        assertNoNotification();
    }

    @Test
    void resendRejectedForOtherReasonFailsErasesHashAndNotifies() {
        Task task = storeSentTask(storeWallet());
        Stored before = stored(task);
        ethereumClient.sendFailures.put(before.signedTransaction(),
                new RpcRejectedException(-32000, "insufficient funds for gas * price + value"));

        worker.runCycle();

        Stored s = stored(task);
        assertEquals(TaskStatus.FAILED, s.status());
        assertEquals(FailureReason.REJECTED_BY_NETWORK, s.failureReason());
        assertNull(s.transactionHash());
        assertNull(s.signedTransaction());
        assertNotifiedOnce(task);
        assertNull(coreApiClient.storedAtNotify.get(0).transactionHash(), "o aviso sai sem hash");
    }

    @Test
    void unknownTransactionWithoutSignedHexOnlyLogs() {
        Task task = storeTask(storeWallet());
        task.markSigned("0x" + "cd".repeat(32), null, task.getCreatedAt());
        taskRepository.update(task);
        Stored before = stored(task);

        worker.runCycle();

        assertEquals(before, stored(task));
        assertTrue(ethereumClient.sent.isEmpty());
        assertNoNotification();
    }

    // ---------- Fase A: falhas de RPC ----------

    @Test
    void rpcFailureOnReceiptKeepsPendingAndNextTaskContinues() {
        Task failing = storeSentTask(storeWallet());
        Task healthy = storeSentTask(storeWallet());
        Stored before = stored(failing);
        ethereumClient.receiptFailures.put(before.transactionHash(), new RpcUnavailableException("falha simulada"));
        ethereumClient.receipts.put(stored(healthy).transactionHash(), new ReceiptInfo(true, BigInteger.ONE));

        worker.runCycle();

        assertEquals(before, stored(failing));
        assertTrue(ethereumClient.knownQueries.isEmpty(), "sem recibo legível, não segue para a consulta");
        assertEquals(TaskStatus.CONFIRMED, stored(healthy).status());
        assertNotifiedOnce(healthy);
    }

    @Test
    void jsonRpcErrorOnReceiptKeepsPending() {
        Task task = storeSentTask(storeWallet());
        Stored before = stored(task);
        ethereumClient.receiptFailures.put(before.transactionHash(), new RpcRejectedException(-32603, "internal error"));

        worker.runCycle();

        assertEquals(before, stored(task));
        assertTrue(ethereumClient.sent.isEmpty());
        assertNoNotification();
    }

    @Test
    void rpcFailureOnHashQueryKeepsPendingAndNextTaskContinues() {
        Task failing = storeSentTask(storeWallet());
        Task healthy = storeSentTask(storeWallet());
        Stored before = stored(failing);
        ethereumClient.knownFailures.put(before.transactionHash(), new RpcUnavailableException("falha simulada"));
        ethereumClient.receipts.put(stored(healthy).transactionHash(), new ReceiptInfo(true, BigInteger.ONE));

        worker.runCycle();

        assertEquals(before, stored(failing));
        assertTrue(ethereumClient.sent.isEmpty(), "sem saber se a rede conhece, não reenvia");
        assertEquals(TaskStatus.CONFIRMED, stored(healthy).status());
        assertNotifiedOnce(healthy);
    }

    @Test
    void rpcFailureOnResendKeepsPendingAndNextTaskContinues() {
        Task failing = storeSentTask(storeWallet());
        Task healthy = storeSentTask(storeWallet());
        Stored before = stored(failing);
        ethereumClient.sendFailures.put(before.signedTransaction(), new RpcUnavailableException("falha simulada"));
        ethereumClient.receipts.put(stored(healthy).transactionHash(), new ReceiptInfo(true, BigInteger.ONE));

        worker.runCycle();

        assertEquals(before, stored(failing));
        assertEquals(List.of(before.signedTransaction()), ethereumClient.sent);
        assertEquals(TaskStatus.CONFIRMED, stored(healthy).status());
        assertNotifiedOnce(healthy);
    }

    // ---------- Fase A e Fase B no mesmo ciclo ----------

    @Test
    void confirmationInPhaseAFreesTheWalletForPhaseBInTheSameCycle() {
        String source = storeWallet();
        Task first = storeSentTask(source);
        Task second = storeTask(source);
        ethereumClient.receipts.put(stored(first).transactionHash(), new ReceiptInfo(true, BigInteger.ONE));

        worker.runCycle();

        assertEquals(TaskStatus.CONFIRMED, stored(first).status());
        assertEquals(1, ethereumClient.sent.size(), "a segunda é enviada no mesmo ciclo");
        assertEquals(hashOf(ethereumClient.sent.get(0)), stored(second).transactionHash());
        assertEquals(TaskStatus.PENDING, stored(second).status());
    }

    @Test
    void phaseADoesNotTouchTasksWithoutHashNorFinishedOnes() {
        String source = storeWallet();
        Task notSent = storeTask(source);
        Task alreadyFailed = storeTask(storeWallet());
        alreadyFailed.markFailed(FailureReason.INTERNAL_ERROR, alreadyFailed.getCreatedAt());
        taskRepository.update(alreadyFailed);
        Stored failedBefore = stored(alreadyFailed);

        worker.runCycle();

        assertTrue(ethereumClient.receiptQueries.isEmpty(), "Fase A só acompanha tasks PENDING com hash");
        assertTrue(ethereumClient.knownQueries.isEmpty());
        assertNotNull(stored(notSent).transactionHash(), "a Fase B assinou e enviou a task sem hash");
        assertEquals(failedBefore, stored(alreadyFailed));
        assertNoNotification();
    }

    @Test
    void phaseBFailureByNonceAlsoNotifies() {
        Task task = storeTask(storeWallet());
        ethereumClient.nonceFailure = new RpcUnavailableException("falha simulada");

        worker.runCycle();

        assertNotifiedOnce(task);
        assertEquals(FailureReason.INTERNAL_ERROR, coreApiClient.storedAtNotify.get(0).failureReason());
    }

    @Test
    void phaseBRejectionAlsoNotifiesWithoutHash() {
        Task task = storeTask(storeWallet());
        ethereumClient.sendFailure = new RpcRejectedException(-32000, "insufficient funds for gas * price + value");

        worker.runCycle();

        assertNotifiedOnce(task);
        Stored atNotify = coreApiClient.storedAtNotify.get(0);
        assertEquals(FailureReason.REJECTED_BY_NETWORK, atNotify.failureReason());
        assertNull(atNotify.transactionHash());
    }

    @Test
    void notificationFailureKeepsStateAndNextTaskContinues() {
        Task first = storeSentTask(storeWallet());
        Task second = storeSentTask(storeWallet());
        ethereumClient.receipts.put(stored(first).transactionHash(), new ReceiptInfo(true, BigInteger.ONE));
        ethereumClient.receipts.put(stored(second).transactionHash(), new ReceiptInfo(false, BigInteger.ONE));
        coreApiClient.failure = new RuntimeException("falha simulada no aviso");

        worker.runCycle();

        assertEquals(TaskStatus.CONFIRMED, stored(first).status(), "estado final gravado antes do aviso");
        assertEquals(TaskStatus.FAILED, stored(second).status());
        assertEquals(FailureReason.TRANSACTION_REVERTED, stored(second).failureReason());
        assertEquals(List.of(first.getTaskId(), second.getTaskId()), coreApiClient.notifiedTaskIds);
    }

    @Test
    void finishedTaskIsNotTrackedAgain() {
        Task task = storeSentTask(storeWallet());
        ethereumClient.receipts.put(stored(task).transactionHash(), new ReceiptInfo(true, BigInteger.ONE));

        worker.runCycle();
        worker.runCycle();

        assertEquals(1, ethereumClient.receiptQueries.size());
        assertNotifiedOnce(task);
    }
}
