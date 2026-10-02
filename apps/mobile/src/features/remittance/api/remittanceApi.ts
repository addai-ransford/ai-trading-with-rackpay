import { apiFetch } from "../../../shared/api/httpClient";

export type RemittanceCountry = {
  code: string;
  name: string;
  dialCode: string;
  currency: string;
  sendEnabled: boolean;
  receiveEnabled: boolean;
};

export type MobileMoneyNetwork = {
  id: string;
  countryCode: string;
  code: string;
  name: string;
};

export type RecipientVerificationRequest = {
  countryCode: string;
  countryDialCode: string;
  currency: string;
  payoutMethod: "MOBILE_MONEY";
  networkCode: string;
  phoneNumber: string;
};

export type RecipientVerification = {
  recipientId: string;
  verified: boolean;
  normalizedPhoneNumber: string;
  verifiedName?: string;
  providerRecipientReference?: string;
  failureReason?: string;
};

export type QuoteRequest = {
  recipientId: string;
  sourceCountryCode: string;
  sourceCurrency: string;
  destinationCurrency: string;
  sourceAmount: string;
};

export type RemittanceQuote = {
  quoteId: string;
  sourceAmount: string;
  sourceCurrency: string;
  feeAmount: string;
  destinationAmount: string;
  destinationCurrency: string;
  fxRate: string;
  expiresAt: string;
};

export type Remittance = {
  remittanceId: string;
  recipientName: string;
  status: string;
  sourceAmount: string;
  sourceCurrency: string;
  feeAmount: string;
  destinationAmount: string;
  destinationCurrency: string;
  payoutProvider?: string;
  providerTransferId?: string;
  createdAt: string;
  updatedAt: string;
};

export function listRemittanceCountries(
  accessToken: string,
  direction: "SEND" | "RECEIVE" = "RECEIVE",
) {
  return apiFetch<RemittanceCountry[]>(
    `/api/v1/remittances/config/countries?direction=${direction}`,
    {},
    accessToken,
  );
}

export function listMobileMoneyNetworks(
  accessToken: string,
  countryCode: string,
) {
  return apiFetch<MobileMoneyNetwork[]>(
    `/api/v1/remittances/config/countries/${encodeURIComponent(countryCode)}/networks`,
    {},
    accessToken,
  );
}

export function verifyRecipient(
  accessToken: string,
  request: RecipientVerificationRequest,
) {
  return apiFetch<RecipientVerification>(
    "/api/v1/remittances/recipients/verify",
    {
      method: "POST",
      body: JSON.stringify(request),
    },
    accessToken,
  );
}

export function createQuote(accessToken: string, request: QuoteRequest) {
  return apiFetch<RemittanceQuote>(
    "/api/v1/remittances/quotes",
    {
      method: "POST",
      body: JSON.stringify(request),
    },
    accessToken,
  );
}

export function fundRemittance(
  accessToken: string,
  quoteId: string,
  idempotencyKey: string,
) {
  return apiFetch<Remittance>(
    "/api/v1/remittances/fund",
    {
      method: "POST",
      headers: {
        "Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify({ quoteId }),
    },
    accessToken,
  );
}

export function triggerPayout(accessToken: string, remittanceId: string) {
  return apiFetch<Remittance>(
    `/api/v1/remittances/${encodeURIComponent(remittanceId)}/payout`,
    { method: "POST" },
    accessToken,
  );
}

export function getRemittance(
  accessToken: string,
  remittanceId: string,
) {
  return apiFetch<Remittance>(
    `/api/v1/remittances/${encodeURIComponent(remittanceId)}`,
    {},
    accessToken,
  );
}

export function listRemittances(accessToken: string) {
  return apiFetch<Remittance[]>("/api/v1/remittances", {}, accessToken);
}
