package com.lemma.blockchain.service;

import com.lemma.blockchain.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AddressValidatorTest {

    private static final String LOWER = "0x71c7656ec7ab88b098defb751b7401b5f6d8976f";
    private static final String UPPER = "0x71C7656EC7AB88B098DEFB751B7401B5F6D8976F";
    /** Exemplo dos contratos, com checksum EIP-55 correto. */
    private static final String CHECKSUM = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F";

    @Test
    void lowerCaseIsAcceptedAsIs() {
        assertEquals(LOWER, AddressValidator.toCanonical(LOWER));
    }

    @Test
    void upperCaseIsAcceptedAndNormalized() {
        assertEquals(LOWER, AddressValidator.toCanonical(UPPER));
    }

    @Test
    void correctChecksumIsAcceptedAndNormalized() {
        assertEquals(LOWER, AddressValidator.toCanonical(CHECKSUM));
    }

    @Test
    void invalidAddressesAreRejected() {
        List<String> invalid = Arrays.asList(
                null,
                "",
                "0x71c7656EC7ab88b098defB751B7401B5f6d8976F",    // caixa mista, checksum errado
                "0x123",                                         // curto
                "71c7656ec7ab88b098defb751b7401b5f6d8976f",      // sem 0x
                "0X71c7656ec7ab88b098defb751b7401b5f6d8976f",    // prefixo 0X
                "0x71c7656ec7ab88b098defb751b7401b5f6d8976g",    // caractere não-hex
                "0x71c7656ec7ab88b098defb751b7401b5f6d8976f00"); // longo

        for (String address : invalid) {
            ApiException e = assertThrows(ApiException.class, () -> AddressValidator.toCanonical(address));
            assertEquals(400, e.getStatus(), "endereço: " + address);
            assertEquals("ADDRESS_INVALID", e.getCode(), "endereço: " + address);
        }
    }
}
