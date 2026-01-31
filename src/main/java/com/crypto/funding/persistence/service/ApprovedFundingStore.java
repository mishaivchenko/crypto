package com.crypto.funding.persistence.service;

import com.crypto.funding.persistence.model.ApprovedFundingEntity;
import com.crypto.funding.persistence.repository.ApprovedFundingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ApprovedFundingStore {

    private final ApprovedFundingRepository repo;

    public ApprovedFundingStore(ApprovedFundingRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void approve(
        String symbol,
        Set<String> exchanges,
        BigDecimal usdt,
        Instant nextFundingAt
    ) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (exchanges == null || exchanges.isEmpty()) {
            throw new IllegalArgumentException("exchanges must not be empty");
        }
        if (usdt == null || usdt.signum() <= 0) {
            throw new IllegalArgumentException("usdt must be positive");
        }
        if (nextFundingAt == null) {
            throw new IllegalArgumentException("nextFundingAt must not be null");
        }

        ApprovedFundingEntity entity =
            repo.findBySymbol(symbol)
                .orElseGet(() ->
                    new ApprovedFundingEntity(symbol, exchanges, usdt, nextFundingAt)
                );

        // Never pass immutable collections into entity setters; Hibernate needs a mutable collection instance.
        entity.setExchanges(new HashSet<>(exchanges));
        entity.setUsdtAmount(usdt);
        entity.setNextFundingAt(nextFundingAt);
        entity.setActive(true);
        entity.setExecuted(false);

        repo.save(entity);
    }

    @Transactional(readOnly = true)
    public List<ApprovedFundingEntity> listPending() {
        // for UI: show all active, not executed
        return repo.findByActiveTrueAndExecutedFalse();
    }

    @Transactional(readOnly = true)
    public List<ApprovedFundingEntity> listDue(Instant time) {
        return repo.findByActiveTrueAndExecutedFalseAndNextFundingAtBefore(time);
    }

    @Transactional
    public void unapprove(String symbol) {
        repo.findBySymbol(symbol).ifPresent(e -> {
            e.setActive(false);
            repo.save(e);
        });
    }

    @Transactional
    public void markExecuted(Long id) {
        repo.findById(id).ifPresent(e -> {
            e.setExecuted(true);
            e.setExecutedAt(Instant.now());
            repo.save(e);
        });
    }

    @Transactional
    public void cancel(Long id) {
        repo.findById(id).ifPresent(e -> {
            e.setActive(false);
            repo.save(e);
        });
    }

    @Transactional(readOnly = true)
    public Optional<ApprovedFundingEntity> findBySymbol(String symbol) {
        return repo.findBySymbol(symbol).stream().findAny();
    }

    @Transactional(readOnly = true)
    public Optional<ApprovedFundingEntity> findBySymbolAndActive(String symbol) {
        return repo.findBySymbolAndActive(symbol, true).stream().findAny();
    }
}
