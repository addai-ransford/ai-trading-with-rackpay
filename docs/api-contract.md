# RackPay API v1 Contract Snapshot

This document records the backend contract used by the RackPay mobile client.

## Boundary

The mobile client is a presentation/client-state layer over `/api/v1`. The backend remains authoritative for authentication, wallet balances, ledger state, payment state, remittance state, provider selection, retries, failover and reconciliation.

The mobile app may own forms, drafts, navigation, presentation state and cached server data. It must not decide financial finality or calculate replacement balances.

## Authentication

All endpoints are authenticated unless explicitly marked public.

### Register

`POST /api/v1/auth/register` — public.

Request fields:

- `email`: required email, max 320
- `firstName`: required, max 100
- `lastName`: required, max 100
- `phone`: optional, max 30
- `password`: required, 12-128 characters

Returns `201 Created` with:

```json
{
  "userId": "uuid",
  "walletId": "uuid",
  "currency": "GHS"
}
```

### Current user

`GET /api/v1/me`

Returns:

```json
{
  "userId": "uuid",
  "email": "user@example.com",
  "firstName": "Nana",
  "lastName": "Osei"
}
```

### Admin creation

`POST /api/v1/admin/admins`

Platform-admin protected. Keycloak users are not automatically platform admins.

## Wallet

### Get wallet

`GET /api/v1/wallet`

Returns the backend-authoritative wallet and all opened currency balances.

```json
{
  "walletId": "uuid",
  "ownerId": "uuid",
  "balances": [
    {
      "balanceId": "uuid",
      "currency": "GHS",
      "balance": 100.00
    }
  ]
}
```

### Open currency balance

`POST /api/v1/wallet/balances`

Request:

```json
{ "currency": "GHS" }
```

### Wallet transactions

`GET /api/v1/wallet/transactions?page=0&size=25`

Current limits: page >= 0, size 1-100.

The response uses the stable pagination DTO:

```json
{
  "content": [],
  "page": 0,
  "size": 25,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

The mobile client must not depend on Spring Data's `Page` metadata.

## Payments

### Create payment

`POST /api/v1/wallet/payments`

Request:

```json
{
  "reference": "client-reference",
  "amount": 100.00,
  "currency": "EUR",
  "description": "Wallet funding",
  "returnUrl": "https://...",
  "webhookUrl": "https://...",
  "customerEmail": "user@example.com"
}
```

Response:

```json
{
  "transactionId": "uuid",
  "provider": "MOLLIE",
  "providerPaymentId": "provider-id",
  "checkoutUrl": "https://...",
  "status": "PENDING"
}
```

Provider choice is backend configuration; the mobile client does not select Mollie/Stripe.

### Provider administration

- `GET /api/v1/admin/payment-providers`
- `PUT /api/v1/admin/payment-providers/active`
- `PUT /api/v1/admin/payment-providers/{id}/enabled`

All are platform-admin protected.

### Payment webhooks

`POST /api/v1/payments/webhooks/{provider}` is a provider callback endpoint and is not called by the mobile app.

## Remittance

### Configuration discovery

The mobile app must not hardcode supported countries or mobile-money networks.

`GET /api/v1/remittances/config/countries?direction=RECEIVE`

Supported directions:

- `SEND`
- `RECEIVE`

Country response:

```json
{
  "code": "GH",
  "name": "Ghana",
  "dialCode": "+233",
  "currency": "GHS",
  "sendEnabled": true,
  "receiveEnabled": true
}
```

`GET /api/v1/remittances/config/countries/{countryCode}/networks`

Returns enabled mobile-money networks for an enabled receiving country:

```json
{
  "id": "uuid",
  "countryCode": "GH",
  "code": "MTN",
  "name": "MTN Mobile Money"
}
```

### Recipient verification

`POST /api/v1/remittances/recipients/verify`

Request:

```json
{
  "countryCode": "GH",
  "countryDialCode": "+233",
  "currency": "GHS",
  "payoutMethod": "MOBILE_MONEY",
  "networkCode": "MTN",
  "phoneNumber": "024..."
}
```

The backend validates country/network configuration, normalizes the phone number and asks a configured payout provider to verify the recipient.

The frontend should display the returned `verifiedName` and require user confirmation before funding.

### Quote

`POST /api/v1/remittances/quotes`

Request:

```json
{
  "recipientId": "uuid",
  "sourceCountryCode": "BE",
  "sourceCurrency": "EUR",
  "destinationCurrency": "GHS",
  "sourceAmount": 100.00
}
```

Response includes quote ID, source/destination amounts and currencies, fee, FX rate and `expiresAt`.

The current configured quote lifetime is two minutes. The backend is authoritative; the client must obtain a new quote after expiry.

### Fund

`POST /api/v1/remittances/fund`

Required header:

```
Idempotency-Key: <unique-client-key>
```

Request:

```json
{ "quoteId": "uuid" }
```

The same idempotency key returns the existing remittance when its request hash matches. Reusing the key for a different request is rejected.

### Payout

`POST /api/v1/remittances/{remittanceId}/payout`

The backend owns payout provider selection, retry, failover, reconciliation and state transitions.

The client must never decide that a provider failed merely because a request timed out.

### Remittance status

`GET /api/v1/remittances/{remittanceId}`

Authenticated and scoped to the current user.

This endpoint is the authoritative polling/read endpoint for an individual remittance.

### History

`GET /api/v1/remittances`

Returns the current user's remittances ordered newest first.

## Remittance states

The current backend states are:

```
CREATED
RECIPIENT_VERIFIED
QUOTED
FUNDS_RESERVED
PAYOUT_PENDING
PAYOUT_PROCESSING
COMPLETED
FAILED
CANCELLED
RECOVERY_REQUIRED
```

An ambiguous provider response may remain UNKNOWN internally and enter reconciliation/recovery. The client must not interpret a timeout as proof that money was not sent.

## Headers

Authenticated requests use:

```
Authorization: Bearer <access-token>
```

Financial idempotent commands use:

```
Idempotency-Key: <client-generated-key>
```

The backend supports `X-Correlation-Id` and returns a correlation ID for tracing.

## Error contract

Application errors use one envelope:

```json
{
  "code": "REMITTANCE_QUOTE_EXPIRED",
  "message": "The remittance quote has expired.",
  "correlationId": "uuid",
  "details": {}
}
```

The mobile client must branch on `code`, not exception message text.

```json
{
  "code": "REMITTANCE_QUOTE_EXPIRED",
  "message": "The remittance quote has expired.",
  "correlationId": "uuid",
  "details": {}
}
```

Stable error and pagination contracts are now part of v1.

Endpoint-level MockMvc coverage covers the pagination boundary and stable error responses. PostgreSQL integration tests continue to cover the financial state machine and payout ledger/recovery paths.

Payment checkout remains provider-neutral at the API boundary: the backend selects Mollie/Stripe, while the mobile client receives a generic `checkoutUrl`, provider identifier, provider payment ID and backend status. No provider-specific mobile integration is required.

The AI trading execution engine remains a separate Python service. Its mobile-facing contract is defined in `docs/ai-trading-api-contract.md`; the Java financial API does not fabricate trading results or balances.

## Mobile architecture

```
React + TypeScript + Capacitor
        |
        | HTTPS / JSON
        v
RackPay API v1
        |
        +--> PostgreSQL / Flyway
        +--> Keycloak
        +--> Mollie / Stripe
        +--> Flutterwave / Paystack
        +--> AI trading service
```

TanStack Query owns server state. Zustand is reserved for local UI state and drafts.
