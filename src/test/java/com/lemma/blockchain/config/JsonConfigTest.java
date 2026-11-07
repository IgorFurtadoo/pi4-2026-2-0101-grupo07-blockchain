package com.lemma.blockchain.config;

import com.lemma.blockchain.dto.BalanceResponse;
import org.junit.jupiter.api.Test;
import org.web3j.utils.Convert;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Prova que o saldo sai no JSON sem notação científica. */
class JsonConfigTest {

    private static final String ADDRESS = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F";

    private static String toJson(BigDecimal balanceEth) {
        BalanceResponse response = new BalanceResponse(ADDRESS, balanceEth, "SEPOLIA");
        return JsonConfig.jsonMapper().toJsonString(response, BalanceResponse.class);
    }

    @Test
    void oneWeiIsWrittenAsPlainDecimal() {
        // Mesma conversão do WalletService; sem a configuração, sairia 1E-18.
        BigDecimal oneWei = Convert.fromWei(BigDecimal.ONE, Convert.Unit.ETHER);

        assertEquals("{\"ethereumAddress\":\"" + ADDRESS + "\","
                        + "\"balanceEth\":0.000000000000000001,\"network\":\"SEPOLIA\"}",
                toJson(oneWei));
    }

    @Test
    void zeroIsWrittenAsZero() {
        BigDecimal zero = Convert.fromWei(BigDecimal.ZERO, Convert.Unit.ETHER);

        assertEquals("{\"ethereumAddress\":\"" + ADDRESS + "\","
                        + "\"balanceEth\":0,\"network\":\"SEPOLIA\"}",
                toJson(zero));
    }
}
