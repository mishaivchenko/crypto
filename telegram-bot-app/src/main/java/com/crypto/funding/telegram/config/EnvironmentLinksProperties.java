package com.crypto.funding.telegram.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "env.links")
public record EnvironmentLinksProperties(
    EnvLinks staging,
    EnvLinks production
)
{
    public record EnvLinks(
        String ui,
        String grafana,
        String engine
    )
    {
        public boolean isEmpty()
        {
            return ( ui == null || ui.isBlank() )
                && ( grafana == null || grafana.isBlank() )
                && ( engine == null || engine.isBlank() );
        }
    }
}
