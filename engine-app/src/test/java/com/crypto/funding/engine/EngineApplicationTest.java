package com.crypto.funding.engine;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

class EngineApplicationTest
{
    // REQ: ENG-ACC-011
    @Test
    void mainDelegatesToSpringApplicationRun()
    {
        try( MockedStatic<SpringApplication> springApplication = mockStatic( SpringApplication.class ) )
        {
            String[] args = {"--spring.profiles.active=local-safe"};

            EngineApplication.main( args );

            springApplication.verify( () -> SpringApplication.run( EngineApplication.class, args ) );
        }
    }
}
