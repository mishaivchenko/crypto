package com.crypto.funding.application.candidate;

import com.crypto.funding.application.ai.AiSignalAdvisorService;
import com.crypto.funding.domain.candidate.SignalCandidate;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;
import com.crypto.funding.infrastructure.persistence.mapper.SignalCandidateMapper;
import com.crypto.funding.infrastructure.persistence.model.SignalCandidateEntity;
import com.crypto.funding.infrastructure.persistence.repository.SignalCandidateJpaRepository;
import com.crypto.funding.config.CandidateProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class SignalCandidateIngestService
{
    private final SignalCandidateJpaRepository candidateRepository;
    private final SymbolNormalizationService symbolNormalizationService;
    private final CandidateProperties candidateProperties;
    private final SignalLiquidityService signalLiquidityService;
    private final AiSignalAdvisorService aiSignalAdvisorService;

    public SignalCandidateIngestService(
        SignalCandidateJpaRepository candidateRepository,
        SymbolNormalizationService symbolNormalizationService,
        CandidateProperties candidateProperties,
        SignalLiquidityService signalLiquidityService,
        AiSignalAdvisorService aiSignalAdvisorService
    )
    {
        this.candidateRepository = candidateRepository;
        this.symbolNormalizationService = symbolNormalizationService;
        this.candidateProperties = candidateProperties;
        this.signalLiquidityService = signalLiquidityService;
        this.aiSignalAdvisorService = aiSignalAdvisorService;
    }

    @Transactional
    public SignalCandidate ingest( IngestSignalCandidateCommand command )
    {
        if( command.sourceMessageId() != null )
        {
            SignalCandidateEntity existing = candidateRepository.findBySourceTypeAndSourceChatIdAndSourceMessageId(
                normalizeSourceType( command.sourceType() ),
                command.sourceChatId(),
                command.sourceMessageId()
            ).orElse( null );
            if( existing != null )
            {
                return refreshExistingCandidate( existing, command );
            }
        }
        else
        {
            Instant threshold = command.detectedAt()
                                       .minus( Duration.ofMinutes( candidateProperties.getDedupeWindowMinutes() ) );
            SignalCandidateEntity existing = candidateRepository.findFirstBySourceTypeAndRawSymbolAndDetectedAtGreaterThanEqualOrderByDetectedAtDesc(
                normalizeSourceType( command.sourceType() ),
                normalizeRawSymbol( command.rawSymbol() ),
                threshold
            ).orElse( null );
            if( existing != null )
            {
                return refreshExistingCandidate( existing, command );
            }
        }

        SignalCandidateEntity entity = new SignalCandidateEntity();
        entity.setSourceType( normalizeSourceType( command.sourceType() ) );
        entity.setSourceChatId( command.sourceChatId() );
        entity.setSourceMessageId( command.sourceMessageId() );
        applyObservation( entity, command );

        SignalCandidate saved = SignalCandidateMapper.toDomain( candidateRepository.save( entity ) );
        if( saved.status() == SignalCandidateStatus.NORMALIZED )
        {
            signalLiquidityService.assessAsync( saved );
            aiSignalAdvisorService.analyzeAsync( saved.id() );
        }
        return saved;
    }

    private SignalCandidate refreshExistingCandidate( SignalCandidateEntity existing, IngestSignalCandidateCommand command )
    {
        if( existing.getStatus() == SignalCandidateStatus.REJECTED
            || existing.getStatus() == SignalCandidateStatus.EVENT_CREATED
            || existing.getStatus() == SignalCandidateStatus.DELETED )
        {
            return SignalCandidateMapper.toDomain( existing );
        }

        applyObservation( existing, command );
        return SignalCandidateMapper.toDomain( candidateRepository.save( existing ) );
    }

    private void applyObservation( SignalCandidateEntity entity, IngestSignalCandidateCommand command )
    {
        CandidateNormalizationResult normalizationResult = symbolNormalizationService.normalize( command.rawSymbol(), command.sourceVenue() );

        entity.setRawPayload( normalizeNullable( command.rawPayload() ) );
        entity.setSourceVenue( normalizeVenue( command.sourceVenue() ) );
        entity.setRawSymbol( normalizeRawSymbol( command.rawSymbol() ) );
        entity.setNormalizedSymbol( normalizationResult.normalizedSymbol() );
        entity.setVenueHints( normalizationResult.venueHints() );
        entity.setDetectedAt( command.detectedAt() );
        entity.setSourceFundingTime( command.sourceFundingTime() );
        entity.setSourceFundingRatePct( command.sourceFundingRatePct() );
        entity.setStatus( normalizationResult.isNormalized() ? SignalCandidateStatus.NORMALIZED : SignalCandidateStatus.FAILED );
        entity.setNormalizationFailureReason( normalizationResult.failureReason() );
    }

    private static String normalizeSourceType( String sourceType )
    {
        if( sourceType == null || sourceType.isBlank() )
        {
            throw new IllegalArgumentException( "sourceType must not be blank" );
        }
        return sourceType.trim().toUpperCase( Locale.ROOT );
    }

    private static String normalizeRawSymbol( String rawSymbol )
    {
        if( rawSymbol == null || rawSymbol.isBlank() )
        {
            throw new IllegalArgumentException( "rawSymbol must not be blank" );
        }
        return rawSymbol.trim().toUpperCase( Locale.ROOT );
    }

    private static String normalizeNullable( String value )
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeVenue( String rawVenue )
    {
        return rawVenue == null || rawVenue.isBlank() ? null : rawVenue.trim().toLowerCase( Locale.ROOT );
    }
}
