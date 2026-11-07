package com.lemma.blockchain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Carteira custodial (tabela wallet, CT5).
 * O endereço fica em minúsculo ("0x" + 40 hex); a chave privada só existe cifrada
 * (ADR 0003). Esta entidade nunca sai pelo JSON.
 */
@Entity
@Table(name = "wallet")
public class Wallet {

    @Id
    @Column(name = "ethereum_address", length = 42, nullable = false)
    private String ethereumAddress;

    @Column(name = "encrypted_private_key", length = 255, nullable = false)
    private String encryptedPrivateKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Exigido pelo Hibernate. */
    protected Wallet() {
    }

    public Wallet(String ethereumAddress, String encryptedPrivateKey, Instant createdAt) {
        this.ethereumAddress = ethereumAddress;
        this.encryptedPrivateKey = encryptedPrivateKey;
        this.createdAt = createdAt;
    }

    public String getEthereumAddress() {
        return ethereumAddress;
    }

    public String getEncryptedPrivateKey() {
        return encryptedPrivateKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
