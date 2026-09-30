# RackPay AI Trading API Contract

This contract defines the mobile-facing boundary for the separate Python AI trading service. The service is responsible for strategy execution; the Java financial API remains authoritative for wallet balances, money movement, limits and financial ledger state.

## Boundary

The mobile app never submits executable strategy code and never updates a balance locally.

The trading service may produce trading decisions and execution status, but any transfer of customer money must go through the Java financial API and its financial invariants.

## Account

### Get trading account

`GET /api/v1/trading/account`

Response:

```json
{
  "accountId": "uuid",
  "currency": "EUR",
  "availableAmount": 500.00,
  "allocatedAmount": 100.00,
  "status": "ACTIVE"
}
```

Amounts are informational server values. The client must not derive a replacement available balance.

## Risk limit

### Get risk limit

`GET /api/v1/trading/risk-limit`

### Set risk limit

`PUT /api/v1/trading/risk-limit`

Request:

```json
{
  "maximumAmount": 250.00,
  "currency": "EUR"
}
```

The backend validates the limit before accepting a change. A trading operation must never allocate more than the effective server-side limit.

## Trading operations

### Start trading

`POST /api/v1/trading/sessions`

Request:

```json
{
  "maximumAmount": 250.00,
  "currency": "EUR"
}
```

Response:

```json
{
  "sessionId": "uuid",
  "status": "STARTING",
  "maximumAmount": 250.00,
  "currency": "EUR"
}
```

### Get trading session

`GET /api/v1/trading/sessions/{sessionId}`

### Stop trading

`POST /api/v1/trading/sessions/{sessionId}/stop`

The client requests a state transition. It does not mark a session stopped until the server reports the resulting state.

## Positions and history

### Active positions

`GET /api/v1/trading/positions`

### Trading history

`GET /api/v1/trading/trades?page=0&size=25`

Trading history uses the same explicit pagination envelope as wallet transactions.

## States

Session states:

```
STARTING
RUNNING
STOP_REQUESTED
STOPPED
FAILED
RECOVERY_REQUIRED
```

Trade states:

```
REQUESTED
OPEN
PARTIALLY_CLOSED
CLOSED
FAILED
UNKNOWN
```

An UNKNOWN provider/execution result is reconciled by the backend/service. The mobile client must not infer failure from a network timeout.

## Financial boundary

Trading may request allocation or release of funds, but the Java financial core owns:

- wallet balances
- reservations
- ledger entries
- idempotency
- financial transaction state
- reconciliation
- final settlement

The Python service therefore cannot directly mutate PostgreSQL financial balances.

## Contract status

This is the mobile-facing contract boundary. The Python execution implementation must conform to it before the corresponding mobile trading screens are enabled.
