package com.lemma.blockchain.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra e decifra a chave privada das carteiras conforme o ADR 0003.
 *
 * AES-256-GCM, IV aleatório de 12 bytes por registro, tag de 128 bits.
 * Valor gravado: Base64 de IV || texto cifrado || tag.
 * AAD: o endereço da carteira exatamente como gravado no banco ("0x" + minúsculo),
 * para que um valor copiado para outra linha não decifre.
 *
 * As mensagens de erro nunca carregam a chave nem o valor cifrado.
 */
public class KeyCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / 8;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public KeyCipher(byte[] encryptionKey) {
        if (encryptionKey == null || encryptionKey.length != KEY_BYTES) {
            throw new IllegalArgumentException("a chave de cifragem precisa ter 32 bytes");
        }
        this.key = new SecretKeySpec(encryptionKey, "AES");
    }

    public String encrypt(byte[] privateKey, String aad) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            // O GCM do JDK devolve o texto cifrado já seguido da tag.
            byte[] cipherTextAndTag = cipher.doFinal(privateKey);

            byte[] stored = ByteBuffer.allocate(IV_BYTES + cipherTextAndTag.length)
                    .put(iv)
                    .put(cipherTextAndTag)
                    .array();
            return Base64.getEncoder().encodeToString(stored);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("falha ao cifrar a chave da carteira");
        }
    }

    public byte[] decrypt(String stored, String aad) {
        byte[] data;
        try {
            data = Base64.getDecoder().decode(stored);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("falha ao decifrar a chave da carteira");
        }
        if (data.length < IV_BYTES + TAG_BYTES) {
            throw new IllegalStateException("falha ao decifrar a chave da carteira");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
        } catch (GeneralSecurityException e) {
            // Tag inválida: texto adulterado, AAD diferente ou chave de cifragem errada.
            throw new IllegalStateException("falha ao decifrar a chave da carteira");
        }
    }
}
