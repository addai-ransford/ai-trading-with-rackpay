# RackPay Financial Core API

Java 21 + Spring Boot service responsible for RackPay's authoritative financial domain.

## Responsibilities

- Wallets and balances
- Ledger transactions
- Remittance
- Trading
- Risk and policy enforcement
- Authorization
- Idempotency
- Audit records
- Provider integrations

The API is the financial authority. Client applications and Python AI services cannot bypass its controls.

## Local development

Requirements:

- Java 21+
- Gradle 8+

Run:

```bash
gradle bootRun
```

Health endpoint:

```text
GET /api/v1/health
```

Spring Boot actuator health is available at:

```text
GET /actuator/health
```
