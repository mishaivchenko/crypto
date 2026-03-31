package com.crypto.funding.application.candidate;

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

    public SignalCandidateIngestService(
        SignalCandidateJpaRepository candidateRepository,
        SymbolNormalizationService symbolNormalizationService,
        CandidateProperties candidateProperties
    )
    {
        this.candidateRepository = candidateRepository;
        this.symbolNormalizationService = symbolNormalizationService;
        this.candidateProperties = candidateProperties;
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
                return SignalCandidateMapper.toDomain( existing );
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
                return SignalCandidateMapper.toDomain( existing );
            }
        }

        CandidateNormalizationResult normalizationResult = symbolNormalizationService.normalize( command.rawSymbol() );

        SignalCandidateEntity entity = new SignalCandidateEntity();
        entity.setSourceType( normalizeSourceType( command.sourceType() ) );
        entity.setSourceChatId( command.sourceChatId() );
        entity.setSourceMessageId( command.sourceMessageId() );
        entity.setRawPayload( normalizeNullable( command.rawPayload() ) );
        entity.setRawSymbol( normalizeRawSymbol( command.rawSymbol() ) );
        entity.setNormalizedSymbol( normalizationResult.normalizedSymbol() );
        entity.setVenueHints( normalizationResult.venueHints() );
        entity.setDetectedAt( command.detectedAt() );
        entity.setStatus( normalizationResult.isNormalized() ? SignalCandidateStatus.NORMALIZED : SignalCandidateStatus.FAILED );
        entity.setNormalizationFailureReason( normalizationResult.failureReason() );

        return SignalCandidateMapper.toDomain( candidateRepository.save( entity ) );
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
}
