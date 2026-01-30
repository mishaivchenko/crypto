package com.crypto.funding;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:./build/test-db.sqlite",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
    "spring.jpa.hibernate.ddl-auto=update"
})
class CryptoApplicationTests
{

    @Test
    void contextLoads()
    {
    }

}
