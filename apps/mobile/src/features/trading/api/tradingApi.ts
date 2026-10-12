import { apiFetch } from "../../../shared/api/httpClient";
import type { Currency, PageResponse } from "../../wallet/api/walletApi";

export type TradingAccount = {
  accountId: string;
  currency: Currency;
  availableAmount: string | number;
  allocatedAmount: string | number;
  status: string;
};

export type TradingRiskLimit = {
  maximumAmount: string | number;
  currency: Currency;
};

export type TradingSessionStatus =
  | "STARTING"
  | "RUNNING"
  | "STOP_REQUESTED"
  | "STOPPED"
  | "FAILED"
  | "RECOVERY_REQUIRED";

export type TradingSession = {
  sessionId: string;
  status: TradingSessionStatus;
  maximumAmount: string | number;
  currency: Currency;
};

export type TradingPosition = {
  positionId?: string;
  symbol?: string;
  instrument?: string;
  side?: string;
  quantity?: string | number;
  entryPrice?: string | number;
  currentPrice?: string | number;
  unrealizedPnl?: string | number;
  currency?: Currency;
  status?: string;
};

export type TradingTrade = {
  tradeId?: string;
  symbol?: string;
  instrument?: string;
  side?: string;
  amount?: string | number;
  pnl?: string | number;
  currency?: Currency;
  status?: string;
  createdAt?: string;
};

export function getTradingAccount(token: string) {
  return apiFetch<TradingAccount>("/api/v1/trading/account", {}, token);
}

export function getTradingRiskLimit(token: string) {
  return apiFetch<TradingRiskLimit>("/api/v1/trading/risk-limit", {}, token);
}

export function setTradingRiskLimit(
  token: string,
  maximumAmount: string,
  currency: Currency,
) {
  return apiFetch<TradingRiskLimit>(
    "/api/v1/trading/risk-limit",
    { method: "PUT", body: JSON.stringify({ maximumAmount: Number(maximumAmount), currency }) },
    token,
  );
}

export function startTrading(token: string, maximumAmount: string, currency: Currency) {
  return apiFetch<TradingSession>(
    "/api/v1/trading/sessions",
    { method: "POST", body: JSON.stringify({ maximumAmount: Number(maximumAmount), currency }) },
    token,
  );
}

export function getTradingSession(token: string, sessionId: string) {
  return apiFetch<TradingSession>(`/api/v1/trading/sessions/${encodeURIComponent(sessionId)}`, {}, token);
}

export function stopTrading(token: string, sessionId: string) {
  return apiFetch<TradingSession>(
    `/api/v1/trading/sessions/${encodeURIComponent(sessionId)}/stop`,
    { method: "POST" },
    token,
  );
}

export function getTradingPositions(token: string) {
  return apiFetch<TradingPosition[]>("/api/v1/trading/positions", {}, token);
}

export function getTradingHistory(token: string, page = 0, size = 25) {
  return apiFetch<PageResponse<TradingTrade>>(
    `/api/v1/trading/trades?page=${page}&size=${size}`,
    {},
    token,
  );
}
