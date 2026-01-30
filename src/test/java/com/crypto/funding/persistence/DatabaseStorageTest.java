package com.crypto.funding.persistence;

import com.crypto.funding.persistence.model.ApprovedFundingEntity;
import com.crypto.funding.persistence.repository.ApprovedFundingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "spring.datasource.url=jdbc:sqlite:./build/test-db.sqlite",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect"
})
class DatabaseStorageTest
{
    @Autowired
    ApprovedFundingRepository repo;

    @Test
    void storesAndReadsFundingEntity() throws Exception
    {
        Files.createDirectories( Path.of( "./build" ) );

        ApprovedFundingEntity entity = new ApprovedFundingEntity(
            "BTC/USDT",
            Set.of( "bybit", "binance" ),
            new BigDecimal( "100" ),
            Instant.now().plusSeconds( 3600 )
        );

        ApprovedFundingEntity saved = repo.save( entity );
        assertThat( saved.getId() ).isNotNull();

        var all = repo.findAll();
        assertThat( all ).hasSize( 1 );
        assertThat( all.get( 0 ).getSymbol() ).isEqualTo( "BTC/USDT" );
    }
}
