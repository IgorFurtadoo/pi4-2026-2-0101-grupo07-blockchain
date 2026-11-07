package com.lemma.blockchain.service;

import com.lemma.blockchain.crypto.KeyCipher;
import com.lemma.blockchain.model.Wallet;
import com.lemma.blockchain.repository.WalletRepository;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;

/**
 * Regras das carteiras custodiais (CT4, CT5).
 */
public class WalletService {

    private static final int PRIVATE_KEY_BYTES = 32;

    private final WalletRepository walletRepository;
    private final KeyCipher keyCipher;

    public WalletService(WalletRepository walletRepository, KeyCipher keyCipher) {
        this.walletRepository = walletRepository;
        this.keyCipher = keyCipher;
    }

    /**
     * CT4: gera um par de chaves, grava a chave privada cifrada (ADR 0003) e
     * devolve só o endereço, no formato com checksum EIP-55.
     * A chave privada nunca sai deste método.
     */
    public String createWallet() {
        ECKeyPair keyPair;
        try {
            keyPair = Keys.createEcKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("falha ao gerar o par de chaves");
        }

        // Forma canônica no banco: "0x" + 40 hex em minúsculo. Também é o AAD da cifra.
        String address = toCanonicalAddress(Keys.getAddress(keyPair));

        byte[] privateKey = Numeric.toBytesPadded(keyPair.getPrivateKey(), PRIVATE_KEY_BYTES);
        String encryptedPrivateKey;
        try {
            encryptedPrivateKey = keyCipher.encrypt(privateKey, address);
        } finally {
            Arrays.fill(privateKey, (byte) 0); // não deixa a chave em claro na memória
        }

        walletRepository.save(new Wallet(address, encryptedPrivateKey, Instant.now()));
        return Keys.toChecksumAddress(address);
    }

    /** Keys.getAddress devolve 40 hex sem "0x". */
    static String toCanonicalAddress(String addressWithoutPrefix) {
        return "0x" + addressWithoutPrefix.toLowerCase(Locale.ROOT);
    }
}
