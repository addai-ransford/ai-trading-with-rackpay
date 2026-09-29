package com.rackpay.api.shared.infrastructure.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class AuditEventLogger {
    private static final Logger log = LoggerFactory.getLogger(AuditEventLogger.class);

    public void record(
        String event,
        String aggregateType,
        String aggregateId,
        String outcome
    ) {
        log.info(
            "audit event={} aggregate_type={} aggregate_id={} outcome={} correlation_id={}",
            safe(event),
            safe(aggregateType),
            safe(aggregateId),
            safe(outcome),
            safe(MDC.get(CorrelationIdFilter.MDC_KEY))
        );
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) return "-";
        return value.length() <= 128 ? value : value.substring(0, 128);
    }
}
