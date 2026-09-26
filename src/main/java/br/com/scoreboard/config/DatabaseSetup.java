package br.com.scoreboard.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

@DataSourceDefinition(
        name = "java:app/jdbc/ScoreboardDS",
        className = "org.postgresql.ds.PGSimpleDataSource",
        url = "jdbc:postgresql://${ENV=POSTGRES_HOST:localhost}:5432/${ENV=POSTGRES_DB:scoreboard}",
        user = "${ENV=POSTGRES_USER:sb_user}",
        password = "${ENV=POSTGRES_PASSWORD:sb_password}"
)
@Singleton
@Startup
@TransactionManagement(TransactionManagementType.BEAN) // Impede que o Flyway bloqueie transação JTA
public class DatabaseSetup {

    @Resource(lookup = "java:app/jdbc/ScoreboardDS")
    private DataSource dataSource;

    @PostConstruct
    public void init() {
        Flyway.configure()
                .dataSource(dataSource)
                .baselineOnMigrate(true)
                .validateOnMigrate(false)
                .load()
                .migrate();
    }
}