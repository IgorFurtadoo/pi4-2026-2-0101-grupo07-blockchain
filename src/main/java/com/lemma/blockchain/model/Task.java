package com.lemma.blockchain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pedido de envio de ETH (tabela task, CT11).
 * Endereços em minúsculo; datas em UTC. Esta entidade nunca sai pelo JSON.
 */
@Entity
@Table(name = "task")
public class Task {

    /** UUID em texto. A coluna é CHAR(36); sem o JdbcTypeCode o validate espera VARCHAR. */
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "task_id", length = 36, nullable = false)
    private String taskId;

    @Column(name = "source_address", length = 42, nullable = false)
    private String sourceAddress;

    @Column(name = "destination_address", length = 42, nullable = false)
    private String destinationAddress;

    @Column(name = "amount_eth", precision = 38, scale = 18, nullable = false)
    private BigDecimal amountEth;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private TaskStatus status;

    /** Vazio enquanto a transação não foi enviada à rede (seção 7.2 do desenho). */
    @Column(name = "transaction_hash", length = 66)
    private String transactionHash;

    /** Só preenchido quando status é FAILED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 32)
    private FailureReason failureReason;

    /**
     * Transação assinada em hexadecimal, gravada junto com o hash antes do envio
     * (seção 7.3 do desenho, emenda do CT11). Interna: não sai em CT14 nem em CT15.
     * A coluna é TEXT; o columnDefinition faz o validate esperar TEXT, não VARCHAR.
     */
    @Column(name = "signed_transaction", columnDefinition = "TEXT")
    private String signedTransaction;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Exigido pelo Hibernate. */
    protected Task() {
    }

    /** Task nova (CT10): PENDING, sem hash e sem motivo; criada e atualizada no mesmo instante. */
    public Task(String taskId, String sourceAddress, String destinationAddress, BigDecimal amountEth, Instant now) {
        this.taskId = taskId;
        this.sourceAddress = sourceAddress;
        this.destinationAddress = destinationAddress;
        this.amountEth = amountEth;
        this.status = TaskStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getSourceAddress() {
        return sourceAddress;
    }

    public String getDestinationAddress() {
        return destinationAddress;
    }

    public BigDecimal getAmountEth() {
        return amountEth;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public String getSignedTransaction() {
        return signedTransaction;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
