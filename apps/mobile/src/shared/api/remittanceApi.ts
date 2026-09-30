import { apiFetch } from "./httpClient";
import type { PageResponse } from "./apiTypes";

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

export type _UnusedPageTypeGuard = PageResponse<unknown>;
