package com.lemma.blockchain.repository;

import com.lemma.blockchain.config.AppConfig;
import com.lemma.blockchain.model.Task;
import com.lemma.blockchain.model.Wallet;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;

/**
 * Cria a SessionFactory do Hibernate a partir do AppConfig.
 *
 * O esquema vem do schema.sql; o Hibernate só confere (validate) que as entidades
 * batem com as tabelas e falha na subida se não baterem.
 */
public final class Database {

    private Database() {
    }

    public static SessionFactory createSessionFactory(AppConfig config) {
        return new Configuration()
                .addAnnotatedClass(Wallet.class)
                .addAnnotatedClass(Task.class)
                .setProperty(AvailableSettings.JAKARTA_JDBC_URL, config.dbUrl())
                .setProperty(AvailableSettings.JAKARTA_JDBC_USER, config.dbUser())
                .setProperty(AvailableSettings.JAKARTA_JDBC_PASSWORD, config.dbPassword())
                .setProperty(AvailableSettings.HBM2DDL_AUTO, "validate")
                // Datas sempre em UTC; Instant gravado como DATETIME, como no schema.sql.
                .setProperty(AvailableSettings.JDBC_TIME_ZONE, "UTC")
                .setProperty(AvailableSettings.PREFERRED_INSTANT_JDBC_TYPE, "TIMESTAMP")
                // Pool de conexões HikariCP, pequeno: um único serviço com pouco tráfego.
                .setProperty(AvailableSettings.CONNECTION_PROVIDER,
                        "org.hibernate.hikaricp.internal.HikariCPConnectionProvider")
                .setProperty("hibernate.hikari.maximumPoolSize", "5")
                .buildSessionFactory();
    }
}
