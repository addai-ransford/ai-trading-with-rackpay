import { apiFetch } from "../../../shared/api/httpClient";

import type { Currency } from "../../wallet/api/walletApi";

export type PaymentStatus =
  | "CREATED"
  | "PENDING"
  | "REQUIRES_ACTION"
  | "PAID"
  | "FAILED"
  | "CANCELLED"
  | "REFUNDED"
  | "CHARGED_BACK"
  | "UNKNOWN";

export type CreatePaymentRequest = {
  reference: string;
  amount: string;
  currency: Currency;
  description: string;
  returnUrl: string;
  webhookUrl: string;
  customerEmail?: string;
};

export type PaymentResponse = {
  transactionId: string;
  provider: "MOLLIE" | "STRIPE";
  providerPaymentId: string;
  checkoutUrl: string;
  status: PaymentStatus;
};

export function createPayment(
  accessToken: string,
  request: CreatePaymentRequest,
) {
  return apiFetch<PaymentResponse>(
    "/api/v1/wallet/payments",
    {
      method: "POST",
      body: JSON.stringify(request),
    },
    accessToken,
  );
}
