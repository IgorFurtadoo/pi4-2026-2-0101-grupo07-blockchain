package com.lemma.socket;

/**
 * Aviso de que uma task chegou ao estado final (CONFIRMED ou FAILED). Mesmos campos
 * do corpo do webhook CT15 (CT27). Arquivo idêntico no Core API.
 */
public class TaskCompletedNotice extends Message {

    private static final long serialVersionUID = 1L;

    private final String taskId;
    private final String status;
    private final String transactionHash; // pode ser null
    private final String failureReason;   // pode ser null
    private final String updatedAt;       // ISO-8601 UTC (Instant.toString())

    public TaskCompletedNotice(String taskId, String status, String transactionHash,
                               String failureReason, String updatedAt) {
        this.taskId = taskId;
        this.status = status;
        this.transactionHash = transactionHash;
        this.failureReason = failureReason;
        this.updatedAt = updatedAt;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getStatus() {
        return status;
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "TaskCompletedNotice[taskId=" + taskId + ", status=" + status + "]";
    }
}
