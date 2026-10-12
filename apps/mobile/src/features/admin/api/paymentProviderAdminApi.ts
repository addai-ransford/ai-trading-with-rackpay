import { apiFetch } from "../../../shared/api/httpClient";

export type PaymentProvider = {
  id: string;
  provider: "MOLLIE" | "STRIPE" | "ADYEN" | "PAYPAL" | string;
  enabled: boolean;
  active: boolean;
  environment: string;
  updatedAt: string;
};

export function listPaymentProviders(accessToken: string) {
  return apiFetch<PaymentProvider[]>("/api/v1/admin/payment-providers", {}, accessToken);
}

export function activatePaymentProvider(accessToken: string, provider: PaymentProvider["provider"]) {
  return apiFetch<PaymentProvider>(
    "/api/v1/admin/payment-providers/active",
    { method: "PUT", body: JSON.stringify({ provider }) },
    accessToken,
  );
}

export function setPaymentProviderEnabled(
  accessToken: string,
  id: string,
  enabled: boolean,
) {
  return apiFetch<PaymentProvider>(
    `/api/v1/admin/payment-providers/${encodeURIComponent(id)}/enabled`,
    { method: "PUT", body: JSON.stringify({ enabled }) },
    accessToken,
  );
}
