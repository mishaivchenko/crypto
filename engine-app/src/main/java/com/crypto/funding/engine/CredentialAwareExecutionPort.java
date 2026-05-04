package com.crypto.funding.engine;

import com.crypto.funding.engine.exchange.LiveExchangeExecutionPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class CredentialAwareExecutionPort extends LiveExchangeExecutionPort
{
    @Autowired
    public CredentialAwareExecutionPort( Environment environment )
    {
        super( environment );
    }
}
