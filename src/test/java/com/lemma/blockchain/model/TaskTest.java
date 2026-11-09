package com.lemma.blockchain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Transições da task (seção 7.2 do desenho). */
class TaskTest {

    private static final Instant CREATED = Instant.parse("2026-11-08T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-08T10:00:05Z");
    private static final String HASH = "0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060";
    private static final String SIGNED = "0xf86c0785";

    private static Task newTask() {
        return new Task("a3f1c2d4-5b6e-4f70-8a91-0c2d3e4f5a6b",
                "0x71c7656ec7ab88b098defb751b7401b5f6d8976f",
                "0x5b38da6a701c568545dcfcb03fcb875f56beddc4",
                new BigDecimal("0.01"), CREATED);
    }

    @Test
    void markSignedKeepsPendingAndStoresHashAndSignedTransaction() {
        Task task = newTask();

        task.markSigned(HASH, SIGNED, LATER);

        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertEquals(HASH, task.getTransactionHash());
        assertEquals(SIGNED, task.getSignedTransaction());
        assertNull(task.getFailureReason());
        assertEquals(LATER, task.getUpdatedAt());
        assertEquals(CREATED, task.getCreatedAt());
    }

    @Test
    void markRejectedFailsAndErasesHashAndSignedTransaction() {
        Task task = newTask();
        task.markSigned(HASH, SIGNED, CREATED);

        task.markRejected(LATER);

        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertEquals(FailureReason.REJECTED_BY_NETWORK, task.getFailureReason());
        assertNull(task.getTransactionHash());
        assertNull(task.getSignedTransaction());
        assertEquals(LATER, task.getUpdatedAt());
    }

    @Test
    void markConfirmedKeepsHashAndSignedTransaction() {
        Task task = newTask();
        task.markSigned(HASH, SIGNED, CREATED);

        task.markConfirmed(LATER);

        assertEquals(TaskStatus.CONFIRMED, task.getStatus());
        assertNull(task.getFailureReason());
        assertEquals(HASH, task.getTransactionHash());
        assertEquals(SIGNED, task.getSignedTransaction());
        assertEquals(LATER, task.getUpdatedAt());
    }

    @Test
    void markRevertedFailsAndKeepsHashAndSignedTransaction() {
        Task task = newTask();
        task.markSigned(HASH, SIGNED, CREATED);

        task.markReverted(LATER);

        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertEquals(FailureReason.TRANSACTION_REVERTED, task.getFailureReason());
        assertEquals(HASH, task.getTransactionHash());
        assertEquals(SIGNED, task.getSignedTransaction());
        assertEquals(LATER, task.getUpdatedAt());
    }

    @Test
    void markFailedStoresReason() {
        Task task = newTask();

        task.markFailed(FailureReason.INTERNAL_ERROR, LATER);

        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertEquals(FailureReason.INTERNAL_ERROR, task.getFailureReason());
        assertNull(task.getTransactionHash());
        assertEquals(LATER, task.getUpdatedAt());
    }
}
