package com.crypto.funding.application.port;

import com.crypto.funding.domain.event.FundingEvent;

import java.util.Collection;

public interface FundingEventSourcePort
{
    Collection<FundingEvent> fetchObservedFundingEvents();
}
