package com.crypto.funding.infrastructure.ai;

import com.crypto.funding.config.DeepSeekProperties;
import com.crypto.funding.domain.ai.AiRecommendation;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class DeepSeekClient
{
    private static final Logger log = LoggerFactory.getLogger( DeepSeekClient.class );

    private final RestClient restClient;
    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;

    public DeepSeekClient( RestClient.Builder builder, DeepSeekProperties properties, ObjectMapper objectMapper )
    {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = builder
            .baseUrl( properties.getBaseUrl() )
            .defaultHeader( HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE )
            .build();
    }

    public record AdviceResult(
        AiRecommendation recommendation,
        double confidence,
        String reasoning,
        String modelUsed,
        int promptTokens,
        int completionTokens
    )
    {
    }

    public AdviceResult analyze( String prompt )
    {
        Map<String, Object> body = Map.of(
            "model", properties.getModel(),
            "messages", List.of( Map.of( "role", "user", "content", prompt ) ),
            "temperature", 0.3,
            "response_format", Map.of( "type", "json_object" )
        );

        String responseBody = restClient.post()
            .uri( "/v1/chat/completions" )
            .header( HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey() )
            .body( body )
            .retrieve()
            .onStatus( status -> status.is4xxClientError() || status.is5xxServerError(),
                ( req, res ) -> {
                    throw new RuntimeException( "DeepSeek API error: HTTP " + res.getStatusCode() );
                } )
            .body( String.class );

        return parseResponse( responseBody );
    }

    private AdviceResult parseResponse( String responseBody )
    {
        try
        {
            ChatCompletionResponse response = objectMapper.readValue( responseBody, ChatCompletionResponse.class );
            String content = response.choices().get( 0 ).message().content();
            RawAdvice raw = objectMapper.readValue( content, RawAdvice.class );

            AiRecommendation recommendation = AiRecommendation.valueOf( raw.recommendation().trim().toUpperCase() );
            double confidence = Math.max( 0.0, Math.min( 1.0, raw.confidence() ) );

            int promptTokens = response.usage() != null ? response.usage().promptTokens() : 0;
            int completionTokens = response.usage() != null ? response.usage().completionTokens() : 0;

            return new AdviceResult( recommendation, confidence, raw.reasoning(), properties.getModel(), promptTokens, completionTokens );
        }
        catch( Exception e )
        {
            log.warn( "Failed to parse DeepSeek response: {}", e.getMessage() );
            throw new RuntimeException( "Failed to parse DeepSeek response", e );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatCompletionResponse(
        List<Choice> choices,
        Usage usage
    )
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice( Message message )
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Message( String content )
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(
        @com.fasterxml.jackson.annotation.JsonProperty("prompt_tokens") int promptTokens,
        @com.fasterxml.jackson.annotation.JsonProperty("completion_tokens") int completionTokens
    )
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawAdvice(
        String recommendation,
        double confidence,
        String reasoning
    )
    {
    }
}
