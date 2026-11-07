package com.lemma.blockchain.service;

import com.lemma.blockchain.crypto.KeyCipher;
import com.lemma.blockchain.model.Wallet;
import com.lemma.blockchain.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa o createWallet sem banco: um repository de teste guarda a Wallet em memória.
 * Nenhuma asserção compara a chave privada com assertEquals, para que uma falha
 * não imprima a chave na saída.
 */
class WalletServiceTest {

    /** Repository de teste: captura o que seria gravado, sem Hibernate. */
    private static class InMemoryWalletRepository extends WalletRepository {
        final List<Wallet> saved = new ArrayList<>();

        InMemoryWalletRepository() {
            super(null);
        }

        @Override
        public void save(Wallet wallet) {
            saved.add(wallet);
        }
    }

    private final InMemoryWalletRepository repository = new InMemoryWalletRepository();
    private final KeyCipher keyCipher = new KeyCipher(randomKey());
    private final WalletService service = new WalletService(repository, keyCipher);

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    @Test
    void savesWalletWithCanonicalAddressAndReturnsChecksumAddress() {
        String returned = service.createWallet();

        assertEquals(1, repository.saved.size());
        Wallet wallet = repository.saved.get(0);
        String stored = wallet.getEthereumAddress();

        assertTrue(stored.matches("^0x[0-9a-f]{40}$"), "endereço gravado deve ser 0x + minúsculo");
        assertEquals(Keys.toChecksumAddress(stored), returned);
        assertEquals(stored, returned.toLowerCase(Locale.ROOT));
        assertNotNull(wallet.getCreatedAt());
    }

    @Test
    void decryptedKeyDerivesTheStoredAddress() {
        service.createWallet();
        Wallet wallet = repository.saved.get(0);

        // O AAD é exatamente o endereço lido da Wallet gravada.
        byte[] privateKey = keyCipher.decrypt(wallet.getEncryptedPrivateKey(), wallet.getEthereumAddress());
        String derived = "0x" + Keys.getAddress(ECKeyPair.create(privateKey));
        Arrays.fill(privateKey, (byte) 0);

        assertEquals(wallet.getEthereumAddress(), derived);
    }

    @Test
    void storedValueIsNotThePrivateKeyInPlainText() {
        service.createWallet();
        Wallet wallet = repository.saved.get(0);

        byte[] privateKey = keyCipher.decrypt(wallet.getEncryptedPrivateKey(), wallet.getEthereumAddress());
        String privateKeyHex = Numeric.toHexStringNoPrefix(privateKey);
        Arrays.fill(privateKey, (byte) 0);

        String stored = wallet.getEncryptedPrivateKey().toLowerCase(Locale.ROOT);
        assertFalse(stored.contains(privateKeyHex), "valor gravado não pode conter a chave em hex");
        assertFalse(stored.matches("^(0x)?[0-9a-f]{64}$"), "valor gravado não pode ser hex de 32 bytes");
    }

    @Test
    void eachWalletHasItsOwnAddress() {
        service.createWallet();
        service.createWallet();

        assertFalse(repository.saved.get(0).getEthereumAddress()
                .equals(repository.saved.get(1).getEthereumAddress()));
    }
}
