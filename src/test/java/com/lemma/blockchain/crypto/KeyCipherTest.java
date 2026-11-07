package com.lemma.blockchain.crypto;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comparações de chave usam assertTrue(Arrays.equals(...)) de propósito:
 * assim, uma falha não imprime bytes de chave na saída do teste.
 */
class KeyCipherTest {

    private static final String ADDRESS = "0x71c7656ec7ab88b098defb751b7401b5f6d8976f";
    private static final String OTHER_ADDRESS = "0x5b38da6a701c568545dcfcb03fcb875f56beddc4";

    private final SecureRandom random = new SecureRandom();
    private final KeyCipher cipher = new KeyCipher(randomBytes(32));

    private byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        random.nextBytes(bytes);
        return bytes;
    }

    @Test
    void decryptReturnsOriginalKey() {
        byte[] privateKey = randomBytes(32);

        String stored = cipher.encrypt(privateKey, ADDRESS);

        assertTrue(Arrays.equals(privateKey, cipher.decrypt(stored, ADDRESS)));
    }

    @Test
    void storedValueIsBase64OfIvCipherTextAndTag() {
        String stored = cipher.encrypt(randomBytes(32), ADDRESS);

        // 12 (IV) + 32 (chave cifrada) + 16 (tag) = 60 bytes = 80 caracteres em Base64.
        assertEquals(80, stored.length());
        assertEquals(60, Base64.getDecoder().decode(stored).length);
    }

    @Test
    void tamperedCipherTextFails() {
        String stored = cipher.encrypt(randomBytes(32), ADDRESS);
        byte[] data = Base64.getDecoder().decode(stored);
        data[20] ^= 0x01; // altera um bit do texto cifrado
        String tampered = Base64.getEncoder().encodeToString(data);

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered, ADDRESS));
    }

    @Test
    void otherAadFails() {
        String stored = cipher.encrypt(randomBytes(32), ADDRESS);

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(stored, OTHER_ADDRESS));
    }

    @Test
    void otherEncryptionKeyFails() {
        String stored = cipher.encrypt(randomBytes(32), ADDRESS);
        KeyCipher otherCipher = new KeyCipher(randomBytes(32));

        assertThrows(IllegalStateException.class, () -> otherCipher.decrypt(stored, ADDRESS));
    }

    @Test
    void sameKeyEncryptedTwiceUsesDifferentIvs() {
        byte[] privateKey = randomBytes(32);

        byte[] first = Base64.getDecoder().decode(cipher.encrypt(privateKey, ADDRESS));
        byte[] second = Base64.getDecoder().decode(cipher.encrypt(privateKey, ADDRESS));

        assertFalse(Arrays.equals(Arrays.copyOf(first, 12), Arrays.copyOf(second, 12)));
    }

    @Test
    void invalidStoredValueFails() {
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("não é base64!", ADDRESS));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("AAAA", ADDRESS));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("", ADDRESS));
    }

    @Test
    void rejectsEncryptionKeyWithWrongSize() {
        assertThrows(IllegalArgumentException.class, () -> new KeyCipher(randomBytes(16)));
    }
}
