package com.lemma.blockchain.config;

import com.fasterxml.jackson.core.JsonGenerator;
import io.javalin.json.JavalinJackson;

/**
 * Configuração do JSON das respostas, usada pelo Main e pelo teste.
 *
 * BigDecimal sai sempre em notação plana (toPlainString): 1 wei vira
 * 0.000000000000000001, nunca 1E-18. Os demais padrões do Javalin são mantidos.
 */
public final class JsonConfig {

    private JsonConfig() {
    }

    public static JavalinJackson jsonMapper() {
        return new JavalinJackson().updateMapper(
                mapper -> mapper.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN));
    }
}
