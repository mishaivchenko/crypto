package com.crypto.funding.infrastructure.source;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record FundingApiEntry(
    Long id,
    String symbol,
    String coin,
    String exchange,
    @JsonProperty("exchange_name") String exchangeName,
    String price,
    String funding,
    @JsonProperty("funding_prev") String fundingPrev,
    @JsonProperty("funding_24") String funding24,
    @JsonProperty("funding_3d") String funding3d,
    @JsonProperty("funding_7d") String funding7d,
    @JsonProperty("funding_interval") Integer fundingInterval,
    @JsonProperty("updated_at") String updatedAt
)
{
}
