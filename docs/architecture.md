# RackPay Architecture

## 1. Product Architecture

RackPay is a wallet-first financial platform combining remittance and AI-assisted trading around one authoritative financial ledger.

The core principle is:

```text
                         RACKPAY
                            │
                    USER WALLET / LEDGER
                            │
             ┌──────────────┴──────────────┐
             │                             │
        REMITTANCE                    AI TRADING
             │                             │
        Payment Flow                Trading Flow
             │                             │
             └──────────────┬──────────────┘
                            │
                       SHARED LEDGER
                            │
                         WALLET
```

There must be no independent financial balances for remittance and trading. Both capabilities consume and record value through the same wallet and ledger.

## 2. System Architecture

The production architecture separates the mobile application, financial backend, and AI/ML services:

```text
                         ┌─────────────────────┐
                         │     RackPay Mobile  │
                         │ React + TypeScript   │
                         │ Capacitor iOS/Android│
                         └──────────┬──────────┘
                                    │ HTTPS / JSON
                                    ▼
                         ┌─────────────────────┐
                         │ Java / Spring Boot  │
                         │ Financial Core API  │
                         └──────────┬──────────┘
                                    │
             ┌──────────────────────┼──────────────────────┐
             ▼                      ▼                      ▼
        Wallet/Ledger           Remittance              Trading
        Accounts                Payments                Orders
             │                      │                      │
             └──────────────────────┼──────────────────────┘
                                    ▼
                             Risk / Policy Engine
                                    │
                         ┌──────────┴──────────┐
                         │                     │
                         ▼                     ▼
                    Python AI/ML          Market Data
                    Signal Service         / Analytics
                         │
                         ▼
                 Signal / Recommendation
                         │
                         ▼
                 Java Risk / Policy
                         │
                  APPROVE / REJECT
                         │
                         ▼
                 Trading Execution
                         │
                         ▼
                       Ledger
                         │
                         ▼
                       Wallet
```

### Responsibility boundary

**Java / Spring Boot is the financial authority.**

It owns:
- Wallets and balances
- Double-entry financial ledger
- Transaction state
- Remittance lifecycle
- Trading accounts and orders
- Risk and policy enforcement
- User trading limits
- Idempotency
- Authorization
- Audit records
- Provider integrations
- Final execution decisions

**Python is the AI/ML system.**

It owns:
- Market-data processing
- Feature engineering
- Model training
- Model inference
- Signal generation
- Backtesting
- Model evaluation
- AI analytics

Python must not directly modify wallet balances, post ledger entries, bypass risk controls, or execute financial transactions.

The rule is:

> **Python proposes. Java decides. The ledger records. The wallet remains the source of truth.**

## 3. Wallet and Ledger

The wallet is a representation of the user's financial position. The ledger is authoritative.

Financial mutations should be represented as immutable ledger entries rather than direct balance edits.

A simplified flow is:

```text
Funding
  │
  ▼
Wallet Account
  │
  ▼
Ledger Entry
  │
  ├──────────────► Remittance
  │
  └──────────────► Trading
                         │
                         ▼
                    Settlement
                         │
                         ▼
                       Ledger
```

The implementation should support:
- Double-entry bookkeeping
- Currency-aware money values
- Immutable posted entries
- Transaction references
- Idempotency keys
- Reconciliation
- Complete audit history

Cached or derived balances may be used for performance, but they must be reconstructable from the ledger.

## 4. Remittance Architecture

The mobile client should not contain a fixed list of supported countries.

Country and corridor support is configuration-driven.

### Country configuration

A country should contain concepts such as:

- ISO country code
- Display name
- Dial code
- Currency
- Flag metadata
- Enabled status
- Send enabled
- Receive enabled
- Supported payment providers/currencies

### Remittance corridor

A corridor defines the supported transfer route:

```text
Source Country
      │
      ▼
Destination Country
      │
      ▼
Provider / FX Configuration
      │
      ▼
Fees + Limits + Compliance Rules
```

Corridors can be enabled or disabled without rebuilding the mobile application.

### Recipient flow

```text
Select destination country
        │
        ▼
Show flag + dial code + currency
        │
        ▼
Enter local phone number
        │
        ▼
Java backend normalizes to E.164
        │
        ▼
Resolve registered recipient
        │
        ▼
Display recipient identity
        │
        ▼
User confirms
        │
        ▼
Authorize + risk checks
        │
        ▼
Ledger transaction
        │
        ▼
Remittance provider
        │
        ▼
Transaction history
```

The user should not enter the country code twice.

## 5. AI-Assisted Trading

Trading limits are enforced server-side.

A user may configure constraints such as:
- Maximum allocated capital
- Maximum single-trade amount
- Maximum daily loss
- Maximum portfolio exposure
- Maximum open positions
- Allowed instruments
- Trading enabled/disabled

The frontend can display and edit these settings, but it is never the enforcement boundary.

### Trading decision flow

```text
Market Data
    │
    ▼
Python AI / ML
    │
    ▼
Trading Signal
    │
    ▼
Java Risk Engine
    │
    ├── Funds available?
    ├── User limit respected?
    ├── Exposure allowed?
    ├── Daily loss limit respected?
    ├── Instrument allowed?
    └── Trading enabled?
          │
      ┌───┴───┐
      ▼       ▼
   REJECT   APPROVE
              │
              ▼
       Trading Execution
              │
              ▼
           Settlement
              │
              ▼
            Ledger
              │
              ▼
            Wallet
```

AI output is therefore advisory/propositional until it passes the Java risk and policy layer.

## 6. Technology Stack

### Mobile
- React
- TypeScript
- Vite
- Capacitor
- Tailwind CSS
- TanStack Query
- Zustand where local client state is appropriate
- Framer Motion
- Lucide

### Financial backend
- Java 21+ LTS
- Spring Boot
- Spring Security
- Spring Data JPA / Hibernate
- PostgreSQL
- Flyway
- Redis where caching/coordination is required
- Gradle
- JUnit
- Testcontainers
- OpenAPI

### AI/ML
- Python
- FastAPI for the service boundary
- PyTorch and/or scikit-learn as required by the models
- pandas / NumPy for data processing
- MLflow when model lifecycle management is introduced

### Messaging
Kafka or another durable event platform can be introduced when asynchronous workflows and scale justify it. It is not required for the first financial core implementation.

## 7. Repository Structure

The repository follows an application/package separation:

```text
rackpay/
├── apps/
│   ├── mobile/             # React + TypeScript + Capacitor
│   ├── api/                # Java + Spring Boot financial backend
│   └── ai/                 # Python AI/ML service
│
├── packages/
│   └── domain/             # Shared client/domain contracts where appropriate
│
├── docs/
│   └── architecture.md    # This document
│
├── package.json
├── pnpm-workspace.yaml
└── README.md
```

The backend domain model remains authoritative in Java. TypeScript types are client contracts, not a trusted financial source of truth.

## 8. Security and Financial Controls

The production system must treat all client input as untrusted.

Required controls include:
- Authentication and authorization
- Server-side trading limits
- Server-side remittance limits
- Idempotency for financial commands
- Immutable ledger records
- Audit logging
- Secure secret management
- Rate limiting
- Fraud/risk controls
- Provider reconciliation
- Transaction state machines
- Strong validation of money and currency
- Monitoring and alerting
- KYC/AML integration points where legally required

Combining stored-value/wallet functionality, money transfer, and automated trading can create significant regulatory obligations. The architecture provides technical control points but does not itself establish licensing or legal compliance.

## 9. Architectural Invariants

These rules should remain true as the system grows:

1. **One financial source of truth:** the ledger.
2. **One wallet balance per account/currency context:** no hidden remittance or trading balances.
3. **Java controls money movement:** clients and Python cannot bypass it.
4. **AI cannot execute financial transactions directly.**
5. **Trading limits are enforced server-side.**
6. **Remittance countries/corridors are configuration-driven.**
7. **Financial writes are idempotent and auditable.**
8. **Posted ledger entries are immutable.**
9. **Client state is never treated as authoritative financial state.**
10. **All financial providers reconcile against RackPay's ledger.**


## Wallet-to-Ledger Atomic Posting

The wallet and accounting ledger are deliberately treated as one financial operation. A remittance, trading allocation, withdrawal, deposit, or other money movement must never update a wallet balance independently of its corresponding accounting entries.

The financial posting flow is:

```text
                    RackPay API
                        │
                 @Transactional
                        │
             ┌──────────▼──────────┐
             │ WalletLedgerService │
             └──────────┬──────────┘
                        │
              Pessimistic wallet lock
                        │
          ┌─────────────┴─────────────┐
          ▼                           ▼
     Wallet balance              Ledger posting
          │                           │
          │                    ┌──────┴──────┐
          │                    ▼             ▼
          │                  DEBIT        CREDIT
          │
          └────────────── PostgreSQL ──────────────
```

### 1. Transaction boundary

The application service uses a single database transaction for the complete financial operation. This means the wallet mutation and its ledger entries are committed together or rolled back together.

For example, if a €100 operation starts successfully but ledger persistence fails, the wallet must not remain €100 lower. The database transaction rolls the wallet change back as well.

### 2. Pessimistic wallet locking

Before changing the wallet balance, the service obtains a database row lock using JPA's pessimistic write lock. This protects against concurrent requests attempting to spend the same available balance.

Conceptually:

```text
Request A ──┐
            ├── lock wallet ──> validate ──> debit ──> post ledger ──> commit
Request B ──┘
                 │
                 └── waits for the wallet lock
```

The lock is important because a normal read followed by a write can allow two concurrent requests to observe the same balance before either request commits.

### 3. Currency enforcement

The wallet, debit ledger account, credit ledger account, and requested money amount must use the same currency for a single-currency posting. Currency conversion is a separate business operation and must not be silently performed by the ledger.

### 4. Double-entry accounting

Every posted ledger transaction contains at least two entries. A balanced transaction has equal total debits and credits:

```text
Ledger Transaction
       │
       ├── DEBIT  €100
       │
       └── CREDIT €100

       DEBITS = CREDITS
```

The ledger therefore records the accounting movement rather than relying only on the wallet's current balance.

### 5. Wallet balance as a current-state projection

The wallet balance is maintained for efficient authorization and user-facing reads, but the ledger is the accounting record. Remittance and trading must not maintain independent authoritative balances.

```text
                         Financial Ledger
                              │
                ┌─────────────┴─────────────┐
                │                           │
          Wallet projection          Financial history
                │                           │
        available balance          immutable postings
                │
        ┌───────┴────────┐
        ▼                ▼
   Remittance         Trading
```

Both domains consume funds from the same wallet and therefore share the same financial source of truth.

### 6. Database as the final consistency boundary

Java domain rules provide business validation, Hibernate maps persistence objects, and PostgreSQL provides structural constraints. Flyway owns the schema lifecycle.

```text
Domain rules
     ↓
Spring service
     ↓
@Transactional
     ↓
Spring Data JPA / Hibernate
     ↓
PostgreSQL constraints + locks
     ↑
Flyway schema migrations
```

This layered approach is intentional: application validation provides clear business behavior, while database constraints protect the persisted financial data from invalid states even when multiple application paths or concurrent transactions are involved.

### Architectural invariant

> **A financial operation is atomic: wallet state and ledger state move together. The wallet is the shared current-state projection; the ledger is the accounting record.**
