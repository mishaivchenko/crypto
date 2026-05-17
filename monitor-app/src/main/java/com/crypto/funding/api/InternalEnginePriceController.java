package com.crypto.funding.api;

import com.crypto.funding.application.venue.VenueMarkPriceService;
import com.crypto.funding.contract.engine.MarkPriceResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@RestController
@RequestMapping("/internal/v1/engine")
public class InternalEnginePriceController
{
    private final VenueMarkPriceService markPriceService;

    public InternalEnginePriceController( VenueMarkPriceService markPriceService )
    {
        this.markPriceService = markPriceService;
    }

    @GetMapping("/mark-price")
    public ResponseEntity<MarkPriceResponse> markPrice(
        @RequestParam String venue,
        @RequestParam String symbol
    )
    {
        Optional<BigDecimal> price = markPriceService.fetchMarkPrice( venue, symbol );
        return price.map( p -> ResponseEntity.ok( new MarkPriceResponse( venue, symbol, p, Instant.now() ) ) )
                    .orElse( ResponseEntity.noContent().build() );
    }
}
