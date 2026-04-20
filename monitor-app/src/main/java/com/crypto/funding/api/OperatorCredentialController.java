package com.crypto.funding.api;

import com.crypto.funding.api.dto.OperatorCredentialRequest;
import com.crypto.funding.api.dto.OperatorCredentialResponse;
import com.crypto.funding.application.security.OperatorCredentialService;
import com.crypto.funding.application.security.OperatorCredentialService.UpdateCredentialCommand;
import com.crypto.funding.domain.venue.VenueAccessMode;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/operators/me/credentials")
public class OperatorCredentialController
{
    private final OperatorCredentialService service;

    public OperatorCredentialController( OperatorCredentialService service )
    {
        this.service = service;
    }

    @GetMapping
    public List<OperatorCredentialResponse> list()
    {
        return service.listMine().stream().map( this::toResponse ).toList();
    }

    @PutMapping("/{venue}/{mode}")
    public OperatorCredentialResponse upsert(
        @PathVariable String venue,
        @PathVariable String mode,
        @Valid @RequestBody OperatorCredentialRequest request
    )
    {
        return toResponse( service.upsertMine(
            venue,
            parseMode( mode ),
            new UpdateCredentialCommand( request.apiKey(), request.secretKey(), request.passphrase() )
        ) );
    }

    @DeleteMapping("/{venue}/{mode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete( @PathVariable String venue, @PathVariable String mode )
    {
        service.deleteMine( venue, parseMode( mode ) );
    }

    @PostMapping("/{venue}/{mode}/check")
    public OperatorCredentialResponse check( @PathVariable String venue, @PathVariable String mode )
    {
        return toResponse( service.checkMine( venue, parseMode( mode ) ) );
    }

    private OperatorCredentialResponse toResponse( OperatorCredentialService.CredentialSummary summary )
    {
        return new OperatorCredentialResponse(
            summary.venue(),
            summary.mode(),
            summary.configured(),
            summary.apiKeyMask(),
            summary.secretKeyMask(),
            summary.passphraseMask(),
            summary.connectionStatus(),
            summary.connectionMessage(),
            summary.lastConnectionHttpStatus(),
            summary.lastCheckedAt(),
            summary.updatedAt()
        );
    }

    private VenueAccessMode parseMode( String rawMode )
    {
        if( rawMode == null || rawMode.isBlank() )
        {
            throw new IllegalArgumentException( "mode must not be blank" );
        }
        String normalized = rawMode.trim().toUpperCase( Locale.ROOT );
        if( "PROD".equals( normalized ) )
        {
            normalized = "PRODUCTION";
        }
        return VenueAccessMode.valueOf( normalized );
    }
}
