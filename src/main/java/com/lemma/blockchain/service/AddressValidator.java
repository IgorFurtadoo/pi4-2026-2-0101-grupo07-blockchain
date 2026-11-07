package com.lemma.blockchain.service;

import com.lemma.blockchain.exception.ApiException;
import org.web3j.crypto.Keys;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Regra única de endereço Ethereum do serviço (CT7, CT10).
 *
 * Aceita "0x" + 40 hex. Se as letras estiverem todas em minúsculo ou todas em
 * maiúsculo, passa. Em caixa mista, a caixa é o checksum EIP-55 e precisa estar
 * certa: assim um endereço digitado com erro é recusado em vez de virar outro.
 */
public final class AddressValidator {

    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private AddressValidator() {
    }

    /**
     * Valida o endereço e devolve a forma canônica do banco ("0x" + minúsculo).
     *
     * @throws ApiException 400 ADDRESS_INVALID se o formato ou o checksum estiver errado
     */
    public static String toCanonical(String address) {
        if (address == null || !ADDRESS_PATTERN.matcher(address).matches()) {
            throw invalid();
        }

        String hex = address.substring(2);
        boolean singleCase = hex.equals(hex.toLowerCase(Locale.ROOT))
                || hex.equals(hex.toUpperCase(Locale.ROOT));
        if (!singleCase && !Keys.toChecksumAddress(address).equals(address)) {
            throw invalid();
        }

        return address.toLowerCase(Locale.ROOT);
    }

    private static ApiException invalid() {
        return new ApiException(400, "ADDRESS_INVALID", "Endereço Ethereum com formato inválido.");
    }
}
