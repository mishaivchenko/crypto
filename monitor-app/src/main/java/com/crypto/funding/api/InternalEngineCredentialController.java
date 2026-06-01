package com.crypto.funding.api;

import com.crypto.funding.application.security.OperatorCredentialService;
import com.crypto.funding.contract.engine.EngineVenueCredentials;
import com.crypto.funding.domain.venue.VenueAccessMode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/engine")
public class InternalEngineCredentialController
{
    private final OperatorCredentialService credentialService;

    public InternalEngineCredentialController( OperatorCredentialService credentialService )
    {
        this.credentialService = credentialService;
    }

    @GetMapping("/credentials/{venue}")
    public ResponseEntity<EngineVenueCredentials> credentials(
        @PathVariable String venue,
        @RequestParam(defaultValue = "testnet") String mode
    )
    {
        VenueAccessMode accessMode;
        try
        {
            accessMode = VenueAccessMode.valueOf( mode.toUpperCase() );
        }
        catch( IllegalArgumentException e )
        {
            return ResponseEntity.badRequest().build();
        }
        return credentialService.resolveDecryptedForEngine( venue, accessMode )
                                .map( ResponseEntity::ok )
                                .orElseGet( () -> ResponseEntity.notFound().build() );
    }
}
