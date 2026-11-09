package com.lemma.blockchain.service;

import com.lemma.blockchain.client.EthereumClient;
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
 * Testa a Fase B do worker sem banco e sem rede, chamando runCycle() direto.
 *
 * Mesmo padrão do TaskServiceTest e do WalletServiceTest: os repositories são
 * subclasses com super(null) que guardam tudo em memória. O de task guarda um
 * retrato do estado a cada save/update, como o banco: assim o teste de ordem
 * confere o que foi gravado, e não o objeto alterado em memória.
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
            if (sendFailure != null) {
                throw sendFailure;
            }
            return nodeHash != null ? nodeHash : hashOf(signedTransactionHex);
        }
    }

    private final InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
    private final InMemoryWalletRepository walletRepository = new InMemoryWalletRepository();
    private final KeyCipher keyCipher = new KeyCipher(randomBytes(32));
    private final FakeEthereumClient ethereumClient = new FakeEthereumClient(taskRepository);
    private final TaskWorker worker =
            new TaskWorker(taskRepository, walletRepository, keyCipher, ethereumClient, CHAIN_ID);

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

    private Stored stored(Task task) {
        return taskRepository.stored.get(task.getTaskId());
    }

    private void assertFailedWithoutSending(Task task, FailureReason reason) {
        Stored s = stored(task);
        assertEquals(TaskStatus.FAILED, s.status());
        assertEquals(reason, s.failureReason());
        assertNull(s.transactionHash());
        assertNull(s.signedTransaction());
        assertTrue(s.updatedAt().isAfter(task.getCreatedAt()), "updatedAt deve mudar");
        assertTrue(ethereumClient.sent.isEmpty(), "nada pode ser enviado");
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
        assertEquals(BigInteger.valueOf(21_000), signed.getGasLimit());
        assertEquals(CHAIN_ID, signed.getChainId());

        Stored s = stored(task);
        assertEquals(TaskStatus.PENDING, s.status());
        assertEquals(hashOf(hex), s.transactionHash());
        assertEquals(hex, s.signedTransaction());
        assertNull(s.failureReason());
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
        worker.runCycle(); // a primeira continua PENDING com hash: a segunda segue esperando

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
}
