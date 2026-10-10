import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Activity, ArrowLeft, CircleAlert, ShieldCheck, Square, TrendingUp } from "lucide-react";
import { useState } from "react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../../shared/api/httpClient";
import { useAuthStore } from "../../../shared/auth/authStore";
import type { Currency } from "../../wallet/api/walletApi";
import {
  getTradingAccount,
  getTradingHistory,
  getTradingPositions,
  getTradingRiskLimit,
  getTradingSession,
  setTradingRiskLimit,
  startTrading,
  stopTrading,
  type TradingSession,
} from "../api/tradingApi";

const currencies: Currency[] = ["EUR", "USD", "GBP", "GHS", "KES", "NGN", "XOF", "UGX"];
const money = (value: string | number, currency: Currency) =>
  new Intl.NumberFormat(undefined, { style: "currency", currency, maximumFractionDigits: 2 }).format(Number(value));
const activeStatuses = new Set(["STARTING", "RUNNING", "STOP_REQUESTED", "RECOVERY_REQUIRED"]);

export function TradingPage() {
  const token = useAuthStore((state) => state.accessToken);
  const queryClient = useQueryClient();
  const [maximumAmount, setMaximumAmount] = useState<string>();
  const [currency, setCurrency] = useState<Currency>();
  const [session, setSession] = useState<TradingSession>();
  const [message, setMessage] = useState<{ kind: "error" | "info"; text: string }>();

  const accountQuery = useQuery({
    queryKey: ["trading", "account"],
    queryFn: () => getTradingAccount(token!),
    enabled: Boolean(token),
    retry: false,
  });
  const limitQuery = useQuery({
    queryKey: ["trading", "risk-limit"],
    queryFn: () => getTradingRiskLimit(token!),
    enabled: Boolean(token),
    retry: false,
  });
  const positionsQuery = useQuery({
    queryKey: ["trading", "positions"],
    queryFn: () => getTradingPositions(token!),
    enabled: Boolean(token),
    refetchInterval: 15_000,
    retry: false,
  });
  const historyQuery = useQuery({
    queryKey: ["trading", "history"],
    queryFn: () => getTradingHistory(token!),
    enabled: Boolean(token),
    refetchInterval: 30_000,
    retry: false,
  });
  const sessionQuery = useQuery({
    queryKey: ["trading", "session", session?.sessionId],
    queryFn: () => getTradingSession(token!, session!.sessionId),
    enabled: Boolean(token && session?.sessionId && activeStatuses.has(session.status)),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status && !activeStatuses.has(status) ? false : 3_000;
    },
    retry: false,
  });

  const startMutation = useMutation({
    mutationFn: async () => {
      if (!token) throw new Error("Your session has expired. Sign in again.");
      const amount = Number(amountValue);
      if (!Number.isFinite(amount) || amount <= 0) {
        throw new Error("Enter a maximum trading amount greater than zero.");
      }
      await setTradingRiskLimit(token, amountValue, currencyValue);
      return startTrading(token, amountValue, currencyValue);
    },
    onSuccess: (result) => {
      setSession(result);
      setMessage({ kind: "info", text: "Trading start requested. Session status will update from the server." });
      void queryClient.invalidateQueries({ queryKey: ["trading"] });
    },
    onError: (error) => setMessage({ kind: "error", text: errorText(error) }),
  });

  const stopMutation = useMutation({
    mutationFn: () => {
      if (!token || !session) throw new Error("No active trading session is available.");
      return stopTrading(token, session.sessionId);
    },
    onSuccess: (result) => {
      setSession(result);
      setMessage({ kind: "info", text: "Stop request submitted. Waiting for the server to confirm the final state." });
    },
    onError: (error) => setMessage({ kind: "error", text: errorText(error) }),
  });

  const displayedSession = sessionQuery.data ?? session;
  const amountValue = maximumAmount ?? String(limitQuery.data?.maximumAmount ?? "");
  const currencyValue: Currency = currency ?? limitQuery.data?.currency ?? "EUR";
  const sessionActive = Boolean(displayedSession && activeStatuses.has(displayedSession.status));
  const currencyForAccount: Currency = accountQuery.data?.currency ?? currencyValue;

  return (
    <section className="space-y-5">
      <header className="page-heading">
        <div>
          <p className="eyebrow">RISK-CONTROLLED STRATEGIES</p>
          <h1>AI Trading</h1>
          <p className="page-description">Set the maximum amount the strategy may use, then monitor the server-reported session and positions.</p>
        </div>
        <Link to="/" className="button button-secondary"><ArrowLeft size={15}/> Overview</Link>
      </header>

      <div className="form-panel">
        <div className="flex items-center gap-2 text-sm text-slate-400"><ShieldCheck size={16} /> Risk controls</div>
        <h2 className="mt-2 text-xl font-semibold">Set your maximum</h2>
        <p className="mt-2 text-sm leading-6 text-slate-400">
          Choose the maximum amount the strategy may use. The server validates this limit and controls allocations; the app cannot guarantee returns or override the server's limit.
        </p>
        <form className="mt-5 space-y-4" onSubmit={(event) => { event.preventDefault(); startMutation.mutate(); }}>
          <label className="block">
            <span className="text-sm text-slate-400">Maximum trading amount</span>
            <input
              required
              min="0.01"
              step="0.01"
              type="number"
              inputMode="decimal"
              value={amountValue}
              onChange={(event) => setMaximumAmount(event.target.value)}
              placeholder="250.00"
              disabled={sessionActive || startMutation.isPending}
              className="mt-2 w-full rounded-2xl border border-slate-700 bg-slate-950 px-4 py-3 outline-none focus:border-slate-500 disabled:opacity-60"
            />
          </label>
          <label className="block">
            <span className="text-sm text-slate-400">Currency</span>
            <select value={currencyValue} onChange={(event) => setCurrency(event.target.value as Currency)} disabled={sessionActive || startMutation.isPending} className="mt-2 w-full rounded-2xl border border-slate-700 bg-slate-950 px-4 py-3 disabled:opacity-60">
              {currencies.map((item) => <option key={item} value={item}>{item}</option>)}
            </select>
          </label>
          {limitQuery.isError ? <InlineNotice kind="warning" text="Your saved risk limit is unavailable. You can still submit a limit; the server will validate it." /> : null}
          <button type="submit" disabled={startMutation.isPending || sessionActive} className="flex w-full items-center justify-center gap-2 rounded-2xl bg-white px-4 py-3 font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-50">
            <TrendingUp size={17} /> {startMutation.isPending ? "Requesting start…" : sessionActive ? "Trading session active" : "Start AI trading"}
          </button>
        </form>
        {sessionActive ? (
          <button type="button" onClick={() => stopMutation.mutate()} disabled={stopMutation.isPending || displayedSession?.status === "STOP_REQUESTED"} className="mt-3 flex w-full items-center justify-center gap-2 rounded-2xl border border-slate-700 px-4 py-3 font-medium text-slate-200 disabled:opacity-50">
            <Square size={15} /> {stopMutation.isPending ? "Requesting stop…" : displayedSession?.status === "STOP_REQUESTED" ? "Stop requested…" : "Stop trading"}
          </button>
        ) : null}
      </div>

      {message ? <div role="status" className={`rounded-2xl border p-4 text-sm leading-6 ${message.kind === "error" ? "border-red-900/60 bg-red-950/20 text-red-200" : "border-slate-700 bg-slate-900 text-slate-300"}`}>{message.text}</div> : null}

      <div className="rounded-3xl border border-slate-800 bg-slate-900 p-5">
        <div className="flex items-center gap-2 text-sm text-slate-400"><Activity size={16} /> Trading account</div>
        {accountQuery.isPending ? <p className="mt-4 text-sm text-slate-500">Loading account…</p> : accountQuery.isError ? (
          <InlineNotice kind="warning" text="Trading account data is unavailable. The trading service may not be configured for this environment yet." />
        ) : accountQuery.data ? (
          <>
            <div className="mt-4 grid grid-cols-2 gap-3">
              <Metric label="Available" value={money(accountQuery.data.availableAmount, currencyForAccount)} />
              <Metric label="Allocated" value={money(accountQuery.data.allocatedAmount, currencyForAccount)} />
            </div>
            <div className="mt-4 flex items-center justify-between border-t border-slate-800 pt-4 text-sm">
              <span className="text-slate-500">Account status</span><StatusPill value={accountQuery.data.status} />
            </div>
          </>
        ) : null}
        {displayedSession ? (
          <div className="mt-4 flex items-center justify-between border-t border-slate-800 pt-4 text-sm">
            <div><p className="text-slate-500">Current session</p><p className="mt-1">{money(displayedSession.maximumAmount, displayedSession.currency)} maximum</p></div>
            <StatusPill value={displayedSession.status} />
          </div>
        ) : null}
        {sessionQuery.isError && sessionActive ? <InlineNotice kind="warning" text="We could not refresh the session. Its state is unknown until the server can be reached; do not assume trading has stopped." /> : null}
      </div>

      <DataPanel title="Open positions" loading={positionsQuery.isPending} error={positionsQuery.isError ? "Positions could not be loaded. The trading service may be unavailable." : undefined} empty={!positionsQuery.isError && !positionsQuery.isPending && positionsQuery.data?.length === 0}>
        {positionsQuery.data?.map((position, index) => (
          <div key={position.positionId ?? `${position.symbol ?? position.instrument ?? "position"}-${index}`} className="flex items-center justify-between gap-3 border-b border-slate-800 py-3 last:border-0">
            <div className="min-w-0"><p className="truncate font-medium">{position.symbol ?? position.instrument ?? "Position"}</p><p className="mt-1 text-xs text-slate-500">{[position.side, position.quantity !== undefined ? `Qty ${position.quantity}` : undefined, position.status].filter(Boolean).join(" · ")}</p></div>
            <div className="shrink-0 text-right"><p className="text-sm">{position.unrealizedPnl !== undefined ? money(position.unrealizedPnl, position.currency ?? currencyForAccount) : "—"}</p><p className="text-xs text-slate-500">Unrealized P/L</p></div>
          </div>
        ))}
      </DataPanel>

      <DataPanel title="Recent trades" loading={historyQuery.isPending} error={historyQuery.isError ? "Trading history could not be loaded." : undefined} empty={!historyQuery.isError && !historyQuery.isPending && (historyQuery.data?.content.length ?? 0) === 0}>
        {historyQuery.data?.content.map((trade, index) => (
          <div key={trade.tradeId ?? `${trade.symbol ?? trade.instrument ?? "trade"}-${index}`} className="flex items-center justify-between gap-3 border-b border-slate-800 py-3 last:border-0">
            <div className="min-w-0"><p className="truncate font-medium">{trade.symbol ?? trade.instrument ?? "Trade"}</p><p className="mt-1 text-xs text-slate-500">{[trade.side, trade.status, trade.createdAt ? new Date(trade.createdAt).toLocaleString() : undefined].filter(Boolean).join(" · ")}</p></div>
            <p className="shrink-0 text-sm">{trade.pnl !== undefined ? money(trade.pnl, trade.currency ?? currencyForAccount) : "—"}</p>
          </div>
        ))}
      </DataPanel>

      <p className="text-center text-xs leading-5 text-slate-600">Trading involves risk. Displayed values and session states come from RackPay services; this screen does not calculate profits or change wallet balances.</p>
    </section>
  );
}

function errorText(error: unknown) {
  if (error instanceof ApiError) {
    return error.correlationId ? `${error.message} (Reference: ${error.correlationId})` : error.message;
  }
  return error instanceof Error ? error.message : "The request could not be completed.";
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div className="rounded-2xl bg-slate-950 p-3"><p className="text-xs text-slate-500">{label}</p><p className="mt-2 break-words text-lg font-semibold">{value}</p></div>;
}

function StatusPill({ value }: { value: string }) {
  const color = value === "RUNNING" || value === "ACTIVE" || value === "STOPPED" || value === "CLOSED" ? "text-emerald-300 bg-emerald-950/40" : value === "FAILED" || value === "RECOVERY_REQUIRED" ? "text-red-300 bg-red-950/40" : "text-amber-200 bg-amber-950/40";
  return <span className={`rounded-full px-3 py-1 text-xs font-medium ${color}`}>{value.replaceAll("_", " ")}</span>;
}

function InlineNotice({ text }: { kind: "warning"; text: string }) {
  return <div className="mt-3 flex gap-2 rounded-xl border border-amber-900/50 bg-amber-950/20 p-3 text-sm leading-5 text-amber-200"><CircleAlert size={17} className="mt-0.5 shrink-0" /><span>{text}</span></div>;
}

function DataPanel({ title, loading, error, empty, children }: { title: string; loading: boolean; error?: string; empty: boolean; children: ReactNode }) {
  return <div className="rounded-3xl border border-slate-800 bg-slate-900 p-5"><h2 className="font-semibold">{title}</h2>{loading ? <p className="mt-4 text-sm text-slate-500">Loading…</p> : error ? <p className="mt-4 text-sm leading-6 text-slate-400">{error}</p> : empty ? <p className="mt-4 text-sm text-slate-500">Nothing to show yet.</p> : <div className="mt-2">{children}</div>}</div>;
}
