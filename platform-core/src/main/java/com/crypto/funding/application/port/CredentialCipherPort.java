package com.crypto.funding.application.port;

public interface CredentialCipherPort
{
    String encrypt( String plaintext );

    String decrypt( String ciphertext );
}
