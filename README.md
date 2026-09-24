# RackPay

Wallet-first financial platform for remittance and AI-assisted trading.

## Architecture

RackPay uses a shared wallet and authoritative financial ledger for both remittance and AI-assisted trading.

- **Mobile:** React + TypeScript + Capacitor for iOS and Android
- **Financial backend:** Java + Spring Boot
- **AI/ML:** Python
- **Database:** PostgreSQL
- **Core principle:** Python proposes, Java decides, the ledger records, and the wallet remains the source of truth.

See the full architecture documentation:

- [Architecture](docs/architecture.md)
