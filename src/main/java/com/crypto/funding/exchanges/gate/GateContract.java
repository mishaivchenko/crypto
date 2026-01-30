package com.crypto.funding.exchanges.gate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties( ignoreUnknown = true )
public class GateContract
{
    public String name;
    public String funding_rate;
    public double funding_next_apply;
    public int funding_interval;
}
