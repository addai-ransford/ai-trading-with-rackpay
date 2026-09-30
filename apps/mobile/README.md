# RackPay Mobile

React + TypeScript + Capacitor application for iOS and Android.

## Architecture

- React + TypeScript — UI and application composition.
- React Router — navigation.
- TanStack Query — server state, caching and request lifecycle.
- Zustand — local client state only.
- Keycloak JS — authentication boundary.
- Fetch API — thin backend client with the stable RackPay error envelope.
- Tailwind CSS v4 — mobile UI styling.
- Capacitor — native iOS/Android packaging.

The backend remains the source of truth for authentication, wallet balances, financial state, remittance state, payments and trading state.

## Local development

From the repository root:

    pnpm install
    cp apps/mobile/.env.example apps/mobile/.env.local
    pnpm --filter @rackpay/mobile dev

## Native setup

After dependencies are installed:

    pnpm --filter @rackpay/mobile exec cap add ios
    pnpm --filter @rackpay/mobile exec cap add android
    pnpm --filter @rackpay/mobile cap:sync

Then open the native projects:

    pnpm --filter @rackpay/mobile cap:open:ios
    pnpm --filter @rackpay/mobile cap:open:android

The generated ios/ and android/ directories are native build artifacts and should be generated from Capacitor configuration rather than manually maintained in this foundation change.

## Environment

See .env.example. The production Keycloak client must be configured for the final RackPay mobile redirect/deep-link scheme before native authentication is enabled.

## Feature structure

    src/
    ├── app/
    │   ├── providers/
    │   ├── router/
    │   └── shell/
    ├── features/
    │   └── home/
    └── shared/
        ├── api/
        ├── auth/
        └── config/

Future features follow the same boundary:

    features/
    ├── auth/
    ├── wallet/
    ├── remittance/
    ├── payments/
    ├── trading/
    └── admin/

Feature code owns its use cases and UI. Shared code contains only genuinely reusable infrastructure.
