import { apiFetch } from "../../../shared/api/httpClient";

export const REMITTANCE_CURRENCIES = ["EUR", "USD", "GBP", "GHS", "KES", "NGN", "XOF", "UGX"] as const;
export type RemittanceCurrency = (typeof REMITTANCE_CURRENCIES)[number];

export type RemittanceNetwork = {
  id: string;
  countryCode: string;
  code: string;
  name: string;
  flutterwaveCode?: string | null;
  paystackCode?: string | null;
  enabled: boolean;
};

export type AdminRemittanceCountry = {
  code: string;
  name: string;
  dialCode: string;
  currency: RemittanceCurrency;
  enabled: boolean;
  sendEnabled: boolean;
  receiveEnabled: boolean;
  networks: RemittanceNetwork[];
};

export type RemittanceCorridor = {
  id: string;
  sourceCountryCode: string;
  destinationCountryCode: string;
  sourceCurrency: RemittanceCurrency;
  destinationCurrency: RemittanceCurrency;
  enabled: boolean;
  minAmount: string | number;
  maxAmount?: string | number | null;
  feeFixedAmount: string | number;
  feeCurrency: RemittanceCurrency;
  feeBps: number;
  fxRate?: string | number | null;
};

export function listAdminRemittanceCountries(token: string) {
  return apiFetch<AdminRemittanceCountry[]>("/api/v1/admin/remittance/countries", {}, token);
}

export function saveRemittanceCountry(token: string, country: Omit<AdminRemittanceCountry, "networks">) {
  return apiFetch<AdminRemittanceCountry>("/api/v1/admin/remittance/countries", {
    method: "POST", body: JSON.stringify(country),
  }, token);
}

export function addRemittanceNetwork(token: string, countryCode: string, network: {
  code: string; name: string; flutterwaveCode: string; paystackCode: string; enabled: boolean;
}) {
  return apiFetch<RemittanceNetwork>(`/api/v1/admin/remittance/countries/${encodeURIComponent(countryCode)}/networks`, {
    method: "POST", body: JSON.stringify(network),
  }, token);
}

export function updateRemittanceNetwork(token: string, countryCode: string, network: RemittanceNetwork) {
  return apiFetch<RemittanceNetwork>(`/api/v1/admin/remittance/countries/${encodeURIComponent(countryCode)}/networks/${encodeURIComponent(network.id)}`, {
    method: "PUT", body: JSON.stringify(network),
  }, token);
}

export function listRemittanceCorridors(token: string) {
  return apiFetch<RemittanceCorridor[]>("/api/v1/admin/remittance/corridors", {}, token);
}

export function saveRemittanceCorridor(token: string, corridor: Omit<RemittanceCorridor, "id">) {
  return apiFetch<RemittanceCorridor>("/api/v1/admin/remittance/corridors", {
    method: "POST", body: JSON.stringify(corridor),
  }, token);
}
