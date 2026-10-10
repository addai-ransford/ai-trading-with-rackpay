# RackPay Mobile

React + TypeScript + Vite + Capacitor frontend for RackPay wallet, remittance, payments, AI trading and platform administration.

## Architecture

- React + TypeScript — UI and application composition.
- React Router — navigation.
- TanStack Query — server state, caching, mutations and polling.
- Zustand — local authentication/session state only.
- Keycloak JS — sign-in and token handling.
- Fetch API — thin backend client with the RackPay error envelope and correlation IDs.
- Tailwind CSS v4 — mobile-first UI styling.
- Capacitor — iOS and Android packaging.

The backend remains authoritative for wallet balances, payment confirmation, remittance state, trading limits/session state and ledger mutations. The frontend must not infer a successful payment or payout from a redirect or network timeout.

## Local development

From the repository root:

```bash
pnpm install
cp apps/mobile/.env.example apps/mobile/.env.local
pnpm --filter @rackpay/mobile dev
```

The Vite development server uses port 4000. For the complete local stack (PostgreSQL, Keycloak, Spring API and frontend), configure the root `.env` and run:

```bash
./run.sh
```

If the app was already running before you change environment variables, restart the Vite process so it picks up the new values.

## Environment

See [`.env.example`](.env.example).

- `VITE_API_BASE_URL`: base URL of the Java API, e.g. `http://localhost:8080`.
- `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM`, `VITE_KEYCLOAK_CLIENT_ID`: Keycloak browser client configuration.
- `VITE_KEYCLOAK_REDIRECT_URI`: optional web redirect URI. Native iOS/Android redirect and deep-link configuration must be completed separately.
- `VITE_PAYMENT_WEBHOOK_URL`: public callback URL supplied to the payment provider. The backend exposes provider callbacks at `/api/v1/payments/webhooks/{provider}`. A provider cannot call a developer's localhost address from the public internet; use a secure public development tunnel or a deployed API for end-to-end callback testing. Keep this URL aligned with the configured active provider.

Never put provider API secrets in `VITE_*` variables; Vite embeds those values into the client bundle.

## Implemented screens

| Route | Purpose |
| --- | --- |
| `/login` | Keycloak sign-in and account registration |
| `/wallet` | Swipeable currency balance cards, open a currency balance, recent activity |
| `/wallet/add-money` | Start a provider-hosted wallet-funding checkout |
| `/remittance` | Load configured countries/networks, verify recipient, choose sending country, request an FX quote, fund and request payout |
| `/remittance/history` | List remittances and poll individual transfer status |
| `/trading` | Risk limit, trading session controls, account, positions and history UI |
| `/admin/payment-providers` | Platform-admin provider activation and enable/disable controls |
| `/admin/admins/new` | Bootstrap-admin-only creation of additional platform administrators |

All administrative writes must still be authorised by the backend. Hiding or disabling a frontend control is not a security boundary.

## Quality checks

Run these from the repository root after changes:

```bash
pnpm --filter @rackpay/mobile typecheck
pnpm --filter @rackpay/mobile lint
pnpm --filter @rackpay/mobile test
pnpm --filter @rackpay/mobile build
```

The Vitest script currently allows an empty test suite, so a successful test command alone does not mean component or end-to-end flows have been tested.

## Native setup

After dependencies are installed:

```bash
pnpm --filter @rackpay/mobile exec cap add ios
pnpm --filter @rackpay/mobile exec cap add android
pnpm --filter @rackpay/mobile cap:sync
pnpm --filter @rackpay/mobile cap:open:ios
pnpm --filter @rackpay/mobile cap:open:android
```

The production Keycloak client must be configured for the final mobile redirect/deep-link scheme before native authentication is enabled.

## Important integration boundaries

- Wallet funding is not complete until the payment provider confirms the payment and the backend processes the webhook.
- The remittance UI can only offer countries and networks enabled by backend configuration. Admin mutation endpoints for country/network enablement must exist before a management screen can safely change those settings.
- The trading UI follows the documented API contract. The Python trading service must implement and pass contract/integration tests for those endpoints before the trading flow is considered production-ready.
- Checkout callbacks, live payment credentials, recipient name enquiry, payout-provider credentials, and native Keycloak deep links require environment-specific setup and cannot be verified by a frontend build alone.
