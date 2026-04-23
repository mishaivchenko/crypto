package com.crypto.funding.application.engine.planning;

import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.domain.trade.ArmedTrade;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import org.springframework.stereotype.Component;

@Component
public class EnginePlanSummaryFormatter
{
    public String format( ArmedTrade trade, FundingEventEntity fundingEvent, EnginePlanStatus status )
    {
        return switch( status )
        {
            case WAITING_ENTRY -> "Ожидаем вход по " + fundingEvent.getSymbol() + " на " + fundingEvent.getVenue();
            case ENTRY_WINDOW -> "Окно входа активно для " + fundingEvent.getSymbol() + " (" + trade.intendedSide() + ")";
            case WAITING_EXIT -> "Сделка ждёт планового выхода";
            case EXIT_WINDOW -> "Окно выхода активно для " + fundingEvent.getSymbol();
            case OVERDUE -> "Окно входа пропущено, нужен разбор оператора";
            case CLOSED -> "Сделка уже закрыта";
            case INVALID -> "План сделки неполный и не готов к исполнению";
        };
    }
}
