import { apiFetch } from "./httpClient";

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

export async function getRemittance(
  remittanceId: string,
  accessToken: string,
): Promise<Remittance> {
  return apiFetch<Remittance>(
    `/api/v1/remittances/${encodeURIComponent(remittanceId)}`,
    {},
    accessToken,
  );
}

export async function listRemittances(
  accessToken: string,
): Promise<Remittance[]> {
  return apiFetch<Remittance[]>("/api/v1/remittances", {}, accessToken);
}

export type RemittanceCountry = {
  code: string;
  name: string;
  dialCode: string;
  currencyCode: string;
  sendEnabled: boolean;
  receiveEnabled: boolean;
};

export type MobileMoneyNetwork = {
  id: string;
  countryCode: string;
  code: string;
  name: string;
};

export async function listRemittanceCountries(
  accessToken: string,
  direction: "SEND" | "RECEIVE" = "RECEIVE",
): Promise<RemittanceCountry[]> {
  return apiFetch<RemittanceCountry[]>(
    `/api/v1/remittances/config/countries?direction=${direction}`,
    {},
    accessToken,
  );
}

export async function listMobileMoneyNetworks(
  accessToken: string,
  countryCode: string,
): Promise<MobileMoneyNetwork[]> {
  return apiFetch<MobileMoneyNetwork[]>(
    `/api/v1/remittances/config/countries/${encodeURIComponent(countryCode)}/networks`,
    {},
    accessToken,
  );
}

export type RecipientVerification = {
  verified: boolean;
  recipientId: string;
  countryCode: string;
  currency: string;
  payoutMethod: "MOBILE_MONEY";
  networkCode: string;
  normalizedPhoneNumber: string;
  verifiedName: string;
};

export async function verifyRemittanceRecipient(
  accessToken: string,
  request: {
    countryCode: string;
    countryDialCode: string;
    currency: string;
    payoutMethod: "MOBILE_MONEY";
    networkCode: string;
    phoneNumber: string;
  },
): Promise<RecipientVerification> {
  return apiFetch<RecipientVerification>(
    "/api/v1/remittances/recipients/verify",
    { method: "POST", body: JSON.stringify(request) },
    accessToken,
  );
}

export type RemittanceQuote = {
  quoteId: string;
  recipientId: string;
  sourceCountryCode: string;
  destinationCountryCode: string;
  sourceCurrency: string;
  sourceAmount: number;
  destinationCurrency: string;
  destinationAmount: number;
  feeAmount: number;
  fxRate: number;
  expiresAt: string;
};

export async function createRemittanceQuote(
  accessToken: string,
  request: {
    recipientId: string;
    sourceCountryCode: string;
    sourceCurrency: string;
    destinationCurrency: string;
    sourceAmount: number;
  },
): Promise<RemittanceQuote> {
  return apiFetch<RemittanceQuote>(
    "/api/v1/remittances/quotes",
    { method: "POST", body: JSON.stringify(request) },
    accessToken,
  );
}
