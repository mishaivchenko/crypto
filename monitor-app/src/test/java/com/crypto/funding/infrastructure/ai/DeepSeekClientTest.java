package com.crypto.funding.infrastructure.ai;

import com.crypto.funding.config.DeepSeekProperties;
import com.crypto.funding.domain.ai.AiRecommendation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeepSeekClientTest
{
    private DeepSeekClient client;
    private DeepSeekProperties properties;
    private Method parseResponseMethod;

    @BeforeEach
    void setUp() throws Exception
    {
        properties = new DeepSeekProperties();
        properties.setModel( "deepseek-chat" );
        properties.setBaseUrl( "https://api.deepseek.com" );
        properties.setApiKey( "test-key" );

        RestClient.Builder builder = mock( RestClient.Builder.class );
        RestClient restClient = mock( RestClient.class );
        when( builder.baseUrl( any( String.class ) ) ).thenReturn( builder );
        when( builder.defaultHeader( any(), any() ) ).thenReturn( builder );
        when( builder.build() ).thenReturn( restClient );

        client = new DeepSeekClient( builder, properties, new ObjectMapper() );

        parseResponseMethod = DeepSeekClient.class.getDeclaredMethod( "parseResponse", String.class );
        parseResponseMethod.setAccessible( true );
    }

    private DeepSeekClient.AdviceResult parseResponse( String json ) throws Exception
    {
        try
        {
            return (DeepSeekClient.AdviceResult) parseResponseMethod.invoke( client, json );
        }
        catch( InvocationTargetException e )
        {
            if( e.getCause() instanceof RuntimeException re )
            {
                throw re;
            }
            throw new RuntimeException( e.getCause() );
        }
    }

    @Test
    void parsesValidResponse_returnsGoRecommendation() throws Exception
    {
        String json = """
            {
              "choices": [{"message": {"content": "{\\"recommendation\\":\\"GO\\",\\"confidence\\":0.8,\\"reasoning\\":\\"Good signal\\"}"}}],
              "usage": {"prompt_tokens": 100, "completion_tokens": 50}
            }
            """;

        DeepSeekClient.AdviceResult result = parseResponse( json );

        assertThat( result.recommendation() ).isEqualTo( AiRecommendation.GO );
        assertThat( result.confidence() ).isEqualTo( 0.8 );
        assertThat( result.reasoning() ).isEqualTo( "Good signal" );
    }

    @Test
    void parsesValidResponse_clipsConfidenceAboveOne() throws Exception
    {
        String json = """
            {
              "choices": [{"message": {"content": "{\\"recommendation\\":\\"WATCH\\",\\"confidence\\":1.5,\\"reasoning\\":\\"ok\\"}"}}],
              "usage": {"prompt_tokens": 10, "completion_tokens": 5}
            }
            """;

        DeepSeekClient.AdviceResult result = parseResponse( json );

        assertThat( result.confidence() ).isEqualTo( 1.0 );
    }

    @Test
    void parsesValidResponse_clipsConfidenceBelowZero() throws Exception
    {
        String json = """
            {
              "choices": [{"message": {"content": "{\\"recommendation\\":\\"PASS\\",\\"confidence\\":-0.1,\\"reasoning\\":\\"skip\\"}"}}],
              "usage": {"prompt_tokens": 10, "completion_tokens": 5}
            }
            """;

        DeepSeekClient.AdviceResult result = parseResponse( json );

        assertThat( result.confidence() ).isEqualTo( 0.0 );
    }

    @Test
    void parsesValidResponse_setsModelFromProperties() throws Exception
    {
        String json = """
            {
              "choices": [{"message": {"content": "{\\"recommendation\\":\\"GO\\",\\"confidence\\":0.7,\\"reasoning\\":\\"ok\\"}"}}],
              "usage": {"prompt_tokens": 10, "completion_tokens": 5}
            }
            """;

        DeepSeekClient.AdviceResult result = parseResponse( json );

        assertThat( result.modelUsed() ).isEqualTo( "deepseek-chat" );
    }

    @Test
    void parsesValidResponse_countsTokens() throws Exception
    {
        String json = """
            {
              "choices": [{"message": {"content": "{\\"recommendation\\":\\"GO\\",\\"confidence\\":0.9,\\"reasoning\\":\\"strong\\"}"}}],
              "usage": {"prompt_tokens": 200, "completion_tokens": 75}
            }
            """;

        DeepSeekClient.AdviceResult result = parseResponse( json );

        assertThat( result.promptTokens() ).isEqualTo( 200 );
        assertThat( result.completionTokens() ).isEqualTo( 75 );
    }

    @Test
    void parsesNullUsage_returnsZeroTokens() throws Exception
    {
        String json = """
            {
              "choices": [{"message": {"content": "{\\"recommendation\\":\\"PASS\\",\\"confidence\\":0.5,\\"reasoning\\":\\"nope\\"}"}}]
            }
            """;

        DeepSeekClient.AdviceResult result = parseResponse( json );

        assertThat( result.promptTokens() ).isEqualTo( 0 );
        assertThat( result.completionTokens() ).isEqualTo( 0 );
    }

    @Test
    void parsesInvalidRecommendation_throwsRuntime()
    {
        String json = """
            {
              "choices": [{"message": {"content": "{\\"recommendation\\":\\"INVALID\\",\\"confidence\\":0.5,\\"reasoning\\":\\"???\\"}"}}],
              "usage": {"prompt_tokens": 10, "completion_tokens": 5}
            }
            """;

        assertThatThrownBy( () -> parseResponse( json ) )
            .isInstanceOf( RuntimeException.class )
            .hasMessageContaining( "Failed to parse DeepSeek response" );
    }

    @Test
    void parsesMalformedJson_throwsRuntime()
    {
        String json = "not-valid-json";

        assertThatThrownBy( () -> parseResponse( json ) )
            .isInstanceOf( RuntimeException.class )
            .hasMessageContaining( "Failed to parse DeepSeek response" );
    }
}
