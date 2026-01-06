package com.crypto.funding.trading;

public interface ExchangeTradingClient
{
    /**
     * Имя биржи в едином формате, например: "binance", "bybit", "gate".
     * Совпадает с name() из твоего ExchangeRestClient.
     */
    String name();

    /**
     * Тестовое открытие ордера.
     * Сейчас — MOCK, позже будет реальный testnet запрос.
     */
    TestOrderResult placeTestOrder(PlaceTestOrderCommand cmd) throws Exception;
}
