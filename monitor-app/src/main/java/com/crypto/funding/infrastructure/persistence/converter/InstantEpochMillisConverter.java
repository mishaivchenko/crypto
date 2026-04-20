package com.crypto.funding.infrastructure.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;

@Converter(autoApply = false)
public class InstantEpochMillisConverter implements AttributeConverter<Instant, Long>
{
    @Override
    public Long convertToDatabaseColumn( Instant attribute )
    {
        return attribute == null ? null : attribute.toEpochMilli();
    }

    @Override
    public Instant convertToEntityAttribute( Long dbData )
    {
        return dbData == null ? null : Instant.ofEpochMilli( dbData );
    }
}
