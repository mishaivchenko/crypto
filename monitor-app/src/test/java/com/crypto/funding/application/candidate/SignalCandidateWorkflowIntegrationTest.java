package com.crypto.funding.application.candidate;

import com.crypto.funding.domain.candidate.SignalCandidateStatus;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.SignalCandidateJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:sqlite:./build/test-signal-candidate-workflow.sqlite",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.datasource.hikari.maximum-pool-size=1",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect"
})
class SignalCandidateWorkflowIntegrationTest
{
    @Autowired
    private SignalCandidateIngestService ingestService;

    @Autowired
    private SignalCandidateReviewService reviewService;

    @Autowired
    private SignalCandidateJpaRepository candidateRepository;

    @Autowired
    private FundingEventJpaRepository fundingEventRepository;

    @BeforeEach
    void clean()
    {
        fundingEventRepository.deleteAll();
        candidateRepository.deleteAll();
    }

    @Test
    void dedupesFundingApiCandidateBySourceIdentity()
    {
        IngestSignalCandidateCommand command = new IngestSignalCandidateCommand(
            "FUNDING_API",
            100L,
            200L,
            "coin: KERNEL/USDT:USDT",
            "bybit",
            "KERNEL/USDT",
            Instant.parse( "2030-01-01T00:00:00Z" ),
            Instant.parse( "2030-01-01T08:00:00Z" ),
            BigDecimal.valueOf( 0.0142 )
        );

        Long firstId = ingestService.ingest( command ).id();
        Long secondId = ingestService.ingest( command ).id();

        assertThat( firstId ).isEqualTo( secondId );
        assertThat( candidateRepository.findAll() ).hasSize( 1 );
        assertThat( candidateRepository.findAll().getFirst().getStatus() ).isEqualTo( SignalCandidateStatus.NORMALIZED );
        assertThat( candidateRepository.findAll().getFirst().getVenueHints() ).containsExactly( "bybit" );
    }

    @Test
    void approveCreatesFundingEventFromCandidateSnapshot()
    {
        Long candidateId = ingestService.ingest( new IngestSignalCandidateCommand(
            "FUNDING_API",
            100L,
            300L,
            "coin: KERNEL/USDT:USDT",
            "gate",
            "KERNEL/USDT",
            Instant.parse( "2030-01-01T00:00:00Z" ),
            Instant.parse( "2030-01-01T08:00:00Z" ),
            BigDecimal.valueOf( 0.0142 )
        ) ).id();

        var candidate = reviewService.approve( new ApproveSignalCandidateCommand(
            candidateId,
            "gate",
            null,
            null,
            null,
            "reviewed"
        ) );

        assertThat( candidate.status() ).isEqualTo( SignalCandidateStatus.EVENT_CREATED );
        assertThat( candidate.fundingEventId() ).isNotNull();
        assertThat( fundingEventRepository.findById( candidate.fundingEventId() ) )
            .get()
            .satisfies( event -> {
                assertThat( event.getVenue() ).isEqualTo( "gate" );
                assertThat( event.getSymbol() ).isEqualTo( "KERNEL/USDT" );
                assertThat( event.getFundingTime() ).isEqualTo( Instant.parse( "2030-01-01T08:00:00Z" ) );
                assertThat( event.getFundingRatePct() ).isEqualByComparingTo( BigDecimal.valueOf( 0.0142 ) );
                assertThat( event.getSignalCandidateId() ).isEqualTo( candidateId );
            } );
    }

    @Test
    void candidateWithoutNormalizationCannotBeApprovedWithoutOverrides()
    {
        Long candidateId = ingestService.ingest( new IngestSignalCandidateCommand(
            "FUNDING_API",
            100L,
            400L,
            "coin: ???",
            null,
            "???",
            Instant.parse( "2030-01-01T00:00:00Z" ),
            Instant.parse( "2030-01-01T08:00:00Z" ),
            BigDecimal.valueOf( 0.01 )
        ) ).id();

        assertThatThrownBy( () -> reviewService.approve( new ApproveSignalCandidateCommand(
            candidateId,
            null,
            null,
            Instant.parse( "2030-01-01T08:00:00Z" ),
            null,
            "needs override"
        ) ) )
            .isInstanceOf( RuntimeException.class )
            .hasMessageContaining( "Нужно явно указать символ" );
    }
}
