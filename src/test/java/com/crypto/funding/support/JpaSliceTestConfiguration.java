package com.crypto.funding.support;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = {
    "com.crypto.funding.persistence.model",
    "com.crypto.funding.infrastructure.persistence.model"
})
@EnableJpaRepositories(basePackages = {
    "com.crypto.funding.persistence.repository",
    "com.crypto.funding.infrastructure.persistence.repository"
})
public class JpaSliceTestConfiguration
{
}
