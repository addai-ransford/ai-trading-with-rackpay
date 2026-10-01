import { apiFetch } from "../../../shared/api/httpClient";

export type Currency = "EUR" | "USD" | "GBP" | "GHS" | "KES" | "NGN" | "XOF" | "UGX";

export type WalletBalance = {
  balanceId: string;
  currency: Currency;
  balance: string;
};

export type Wallet = {
  walletId: string;
  ownerId: string;
  balances: WalletBalance[];
};

export type WalletTransaction = {
  transactionId: string;
  operationType: string;
  amount: string;
  currency: Currency;
  status: string;
  ledgerTransactionId?: string;
  createdAt: string;
  updatedAt: string;
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export function getWallet(accessToken: string) {
  return apiFetch<Wallet>("/api/v1/wallet", {}, accessToken);
}

export function openWalletBalance(accessToken: string, currency: Currency) {
  return apiFetch<WalletBalance>(
    "/api/v1/wallet/balances",
    {
      method: "POST",
      body: JSON.stringify({ currency }),
    },
    accessToken,
  );
}

export function listWalletTransactions(
  accessToken: string,
  page = 0,
  size = 25,
) {
  return apiFetch<PageResponse<WalletTransaction>>(
    `/api/v1/wallet/transactions?page=${page}&size=${size}`,
    {},
    accessToken,
  );
}
