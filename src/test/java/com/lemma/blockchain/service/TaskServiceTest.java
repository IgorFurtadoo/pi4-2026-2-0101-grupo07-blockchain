package com.lemma.blockchain.service;

import com.lemma.blockchain.dto.TaskAcceptedResponse;
import com.lemma.blockchain.dto.TaskResponse;
import com.lemma.blockchain.exception.ApiException;
import com.lemma.blockchain.model.Task;
import com.lemma.blockchain.model.TaskStatus;
import com.lemma.blockchain.model.Wallet;
import com.lemma.blockchain.repository.TaskRepository;
import com.lemma.blockchain.repository.WalletRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa o TaskService sem banco: os dois repositories guardam tudo em memória e
 * registram as consultas feitas.
 */
class TaskServiceTest {

    /** Carteira custodial gravada, em minúsculo (forma canônica). */
    private static final String SOURCE = "0x71c7656ec7ab88b098defb751b7401b5f6d8976f";
    /** Mesma carteira com checksum EIP-55, como o Core API costuma mandar. */
    private static final String SOURCE_CHECKSUM = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F";
    /** Destino em maiúsculo, para conferir a normalização. */
    private static final String DESTINATION = "0x5B38DA6A701C568545DCFCB03FCB875F56BEDDC4";
    private static final String DESTINATION_LOWER = "0x5b38da6a701c568545dcfcb03fcb875f56beddc4";

    private static class InMemoryWalletRepository extends WalletRepository {
        final List<Wallet> saved = new ArrayList<>();
        final List<String> searched = new ArrayList<>();

        InMemoryWalletRepository() {
            super(null);
        }

        @Override
        public void save(Wallet wallet) {
            saved.add(wallet);
        }

        /** Compara exatamente, como a chave primária: só acha se vier em minúsculo. */
        @Override
        public Optional<Wallet> findByAddress(String ethereumAddress) {
            searched.add(ethereumAddress);
            return saved.stream()
                    .filter(wallet -> wallet.getEthereumAddress().equals(ethereumAddress))
                    .findFirst();
        }
    }

    private static class InMemoryTaskRepository extends TaskRepository {
        final List<Task> saved = new ArrayList<>();

        InMemoryTaskRepository() {
            super(null);
        }

        @Override
        public void save(Task task) {
            saved.add(task);
        }

        @Override
        public Optional<Task> findById(String taskId) {
            return saved.stream().filter(task -> task.getTaskId().equals(taskId)).findFirst();
        }
    }

    private final InMemoryWalletRepository walletRepository = new InMemoryWalletRepository();
    private final InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
    private final TaskService service = new TaskService(taskRepository, walletRepository);

    private void storeSourceWallet() {
        walletRepository.save(new Wallet(SOURCE, "valor-cifrado-de-teste", Instant.now()));
    }

    private TaskAcceptedResponse submit(String amountEth) {
        return service.requestSubmission(SOURCE_CHECKSUM, DESTINATION,
                amountEth == null ? null : new BigDecimal(amountEth));
    }

    private void assertAmountInvalid(String amountEth) {
        storeSourceWallet();
        ApiException e = assertThrows(ApiException.class, () -> submit(amountEth));
        assertEquals(400, e.getStatus(), "valor: " + amountEth);
        assertEquals("AMOUNT_INVALID", e.getCode(), "valor: " + amountEth);
        assertTrue(taskRepository.saved.isEmpty(), "não deve gravar task");
    }

    private BigDecimal savedAmountOf(String amountEth) {
        storeSourceWallet();
        submit(amountEth);
        return taskRepository.saved.get(0).getAmountEth();
    }

    // ---------- requestSubmission (CT10): valor ----------

    @Test
    void usualAmountIsAccepted() {
        assertEquals(0, new BigDecimal("0.01").compareTo(savedAmountOf("0.01")));
    }

    @Test
    void eighteenDecimalPlacesAreAccepted() {
        assertEquals(0, new BigDecimal("1.123456789012345678").compareTo(savedAmountOf("1.123456789012345678")));
    }

    @Test
    void oneWeiIsAccepted() {
        assertEquals("0.000000000000000001", savedAmountOf("0.000000000000000001").toPlainString());
    }

    @Test
    void twentyIntegerDigitsAreAccepted() {
        String max = "99999999999999999999.999999999999999999"; // maior valor de DECIMAL(38,18)
        assertEquals(0, new BigDecimal(max).compareTo(savedAmountOf(max)));
    }

    @Test
    void trailingZerosBeyondEighteenPlacesDoNotCount() {
        assertEquals(0, new BigDecimal("0.01").compareTo(savedAmountOf("0.0100000000000000000000")));
    }

    @Test
    void savedAmountHasTheColumnScale() {
        assertEquals("0.010000000000000000", savedAmountOf("0.01").toPlainString());
    }

    @Test
    void zeroIsInvalid() {
        assertAmountInvalid("0");
    }

    @Test
    void negativeIsInvalid() {
        assertAmountInvalid("-0.01");
    }

    @Test
    void missingAmountIsInvalid() {
        assertAmountInvalid(null);
    }

    @Test
    void nineteenDecimalPlacesAreInvalid() {
        assertAmountInvalid("0.0000000000000000001");
    }

    @Test
    void twentyOneIntegerDigitsAreInvalid() {
        assertAmountInvalid("100000000000000000000");
    }

    // ---------- requestSubmission (CT10): gravação e erros ----------

    @Test
    void savesPendingTaskWithoutHashAndLowerCaseAddresses() {
        storeSourceWallet();

        TaskAcceptedResponse response = submit("0.01");

        assertEquals(1, taskRepository.saved.size());
        Task task = taskRepository.saved.get(0);
        assertEquals(response.taskId(), task.getTaskId());
        assertEquals(TaskStatus.PENDING, response.status());
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertEquals(SOURCE, task.getSourceAddress());
        assertEquals(DESTINATION_LOWER, task.getDestinationAddress());
        assertNull(task.getTransactionHash());
        assertNull(task.getFailureReason());
        assertNotNull(task.getCreatedAt());
        assertEquals(task.getCreatedAt(), task.getUpdatedAt());
        assertEquals(List.of(SOURCE), walletRepository.searched);
    }

    @Test
    void taskIdIsRandomUuidVersion4() {
        storeSourceWallet();

        String first = submit("0.01").taskId();
        String second = submit("0.01").taskId();

        UUID uuid = UUID.fromString(first);
        assertEquals(4, uuid.version());
        assertEquals(uuid.toString(), first);
        assertNotEquals(first, second, "cada task tem o seu taskId");
    }

    @Test
    void sourceEqualToDestinationIsAllowed() {
        storeSourceWallet();

        service.requestSubmission(SOURCE, SOURCE_CHECKSUM, new BigDecimal("0.01"));

        Task task = taskRepository.saved.get(0);
        assertEquals(task.getSourceAddress(), task.getDestinationAddress());
    }

    @Test
    void unknownSourceIsNotFoundAndSavesNothing() {
        ApiException e = assertThrows(ApiException.class, () -> submit("0.01"));

        assertEquals(404, e.getStatus());
        assertEquals("WALLET_NOT_FOUND", e.getCode());
        assertTrue(taskRepository.saved.isEmpty(), "não deve gravar task");
    }

    @Test
    void invalidAddressIsRejectedWithoutQueryingRepository() {
        storeSourceWallet();
        String wrongChecksum = "0x71c7656EC7ab88b098defB751B7401B5f6d8976F";
        BigDecimal amount = new BigDecimal("0.01");

        ApiException invalidSource = assertThrows(ApiException.class,
                () -> service.requestSubmission(wrongChecksum, DESTINATION, amount));
        ApiException invalidDestination = assertThrows(ApiException.class,
                () -> service.requestSubmission(SOURCE, "0x123", amount));
        ApiException missingDestination = assertThrows(ApiException.class,
                () -> service.requestSubmission(SOURCE, null, amount));

        for (ApiException e : List.of(invalidSource, invalidDestination, missingDestination)) {
            assertEquals(400, e.getStatus());
            assertEquals("ADDRESS_INVALID", e.getCode());
        }
        assertTrue(walletRepository.searched.isEmpty(), "não deve consultar o repository");
        assertTrue(taskRepository.saved.isEmpty(), "não deve gravar task");
    }

    @Test
    void addressIsValidatedBeforeAmount() {
        ApiException e = assertThrows(ApiException.class,
                () -> service.requestSubmission("0x123", DESTINATION, BigDecimal.ZERO));

        assertEquals("ADDRESS_INVALID", e.getCode());
    }

    // ---------- getTask (CT14) ----------

    @Test
    void existingTaskIsReturnedWithUpdatedAtInSeconds() {
        String taskId = "a3f1c2d4-5b6e-4f70-8a91-0c2d3e4f5a6b";
        taskRepository.save(new Task(taskId, SOURCE, DESTINATION_LOWER, new BigDecimal("0.01"),
                Instant.parse("2026-10-20T14:36:10.123Z")));

        TaskResponse response = service.getTask(taskId);

        assertEquals(taskId, response.taskId());
        assertEquals(TaskStatus.PENDING, response.status());
        assertNull(response.transactionHash());
        assertNull(response.failureReason());
        assertEquals("2026-10-20T14:36:10Z", response.updatedAt());
    }

    @Test
    void unknownTaskIsNotFound() {
        for (String taskId : List.of("00000000-0000-4000-8000-000000000000", "abc")) {
            ApiException e = assertThrows(ApiException.class, () -> service.getTask(taskId));
            assertEquals(404, e.getStatus(), "taskId: " + taskId);
            assertEquals("TASK_NOT_FOUND", e.getCode(), "taskId: " + taskId);
        }
    }
}
