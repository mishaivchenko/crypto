package com.crypto.funding.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class VenueHttpClientConfig
{
    @Bean
    HttpClient venueHttpClient( VenueHttpProperties properties )
    {
        return HttpClient.newBuilder()
                         .connectTimeout( Duration.ofMillis( properties.getConnectTimeoutMs() ) )
                         .followRedirects( HttpClient.Redirect.NEVER )
                         .version( properties.isPreferHttp2() ? HttpClient.Version.HTTP_2 : HttpClient.Version.HTTP_1_1 )
                         .build();
    }
}
