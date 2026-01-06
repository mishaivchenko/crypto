package com.crypto.funding.api;

import com.crypto.funding.trading.PlaceTestOrderCommand;
import com.crypto.funding.trading.TestOrderEngine;
import com.crypto.funding.trading.TestOrderResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test-orders")
public class TestOrderController
{
    private final TestOrderEngine engine;

    public TestOrderController(TestOrderEngine engine) {
        this.engine = engine;
    }

    @PostMapping
    @ResponseStatus( HttpStatus.CREATED)
    public TestOrderResult place(@RequestBody PlaceTestOrderCommand cmd) {
        return engine.placeTestOrder(cmd);
    }
}
