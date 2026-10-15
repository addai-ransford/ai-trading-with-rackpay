import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import {
  ArrowDownLeft,
  ArrowUpRight,
  Plus,
  WalletCards,
  Sparkles,
  RefreshCw,
  Wallet,
  Clock,
  AlertCircle,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import {
  type Currency,
  getWallet,
  listWalletTransactions,
  openWalletBalance,
} from "../api/walletApi";
import { ApiError } from "../../../shared/api/httpClient";
import { useAuthStore } from "../../../shared/auth/authStore";


const currencyFormatter = new Intl.NumberFormat(undefined, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

function formatDate(dateString: string): string {
  try {
    return new Intl.DateTimeFormat(undefined, {
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    }).format(new Date(dateString));
  } catch {
    return dateString;
  }
}

function getCurrencySymbol(currency: string): string {
  try {
    const parts = new Intl.NumberFormat(undefined, {
      style: "currency",
      currency,
      currencyDisplay: "narrowSymbol",
    }).formatToParts(0);
    return parts.find((p) => p.type === "currency")?.value ?? currency;
  } catch {
    return currency;
  }
}

export function WalletPage() {
  const accessToken = useAuthStore((state) => state.accessToken);
  const queryClient = useQueryClient();
  const [currency, setCurrency] = useState<Currency>("EUR");
  const [searchParams, setSearchParams] = useSearchParams();
  const returnedFromCheckout = searchParams.get("payment") === "return";
  const [activeCardIndex, setActiveCardIndex] = useState(0);

  useEffect(() => {
    if (!returnedFromCheckout) return;
    void queryClient.invalidateQueries({ queryKey: ["wallet"] });
    const timeout = window.setTimeout(() => {
      searchParams.delete("payment");
      setSearchParams(searchParams, { replace: true });
    }, 10_000);
    return () => window.clearTimeout(timeout);
  }, [returnedFromCheckout, queryClient]);

  const walletQuery = useQuery({
    queryKey: ["wallet"],
    queryFn: () => getWallet(accessToken!),
    enabled: Boolean(accessToken),
    staleTime: 30_000,
  });

  const wallet = walletQuery.data;

  // Dynamically derive available currencies from user balances and common standards
  const availableCurrencies = useMemo(() => {
    const activeCurrencies = wallet?.balances.map((b) => b.currency) as Currency[] ?? [];
    const standardCurrencies: Currency[] = ["EUR", "USD", "GBP", "GHS", "KES", "NGN", "XOF", "UGX"];
    return Array.from(new Set([...activeCurrencies, ...standardCurrencies]));
  }, [wallet]);

  const openBalanceMutation = useMutation({
    mutationFn: () => openWalletBalance(accessToken!, currency),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["wallet"] });
    },
  });

  useEffect(() => {
    const unopened = availableCurrencies.filter(
      (c) => !wallet?.balances.some((b) => b.currency === c),
    );
    if (unopened.length > 0 && (!currency || wallet?.balances.some((b) => b.currency === currency))) {
      setCurrency(unopened[0]);
    }
  }, [availableCurrencies, currency, wallet]);

  if (walletQuery.isPending) {
    return <WalletLoading />;
  }

  if (walletQuery.isError) {
    return (
      <section className="mx-auto flex min-h-[60vh] max-w-md flex-col items-center justify-center text-center px-4">
        <div className="rounded-2xl border border-red-500/20 bg-red-500/10 p-4 text-red-400 shadow-lg">
          <AlertCircle size={32} />
        </div>
        <h2 className="mt-4 text-lg font-bold text-white">Failed to load wallet</h2>
        <p className="mt-1 text-sm text-slate-400">
          {errorMessage(walletQuery.error, "We could not load your wallet.")}
        </p>
        <button
          type="button"
          onClick={() => void walletQuery.refetch()}
          className="button button-primary mt-6 inline-flex items-center gap-2 px-5 py-2.5"
        >
          <RefreshCw size={16} /> Try again
        </button>
      </section>
    );
  }

  const currencyAlreadyOpen = wallet?.balances.some((balance) => balance.currency === currency) ?? false;
  const unopenedCurrencies = availableCurrencies.filter(
    (c) => !wallet?.balances.some((b) => b.currency === c),
  );

  return (
    <section className="wallet-page mx-auto max-w-5xl space-y-8 pb-12 pt-4">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-500/20 bg-emerald-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-wider text-emerald-400">
            <Sparkles size={13} /> Financial Space
          </span>
          <h1 className="mt-2 text-2xl font-extrabold tracking-tight text-white sm:text-3xl">Your wallet</h1>
          <p className="mt-1 text-sm text-slate-400">Swipe between your currency balances and review recent activity.</p>
        </div>
        <Link to="/wallet/add-money" className="button button-primary self-start sm:self-auto flex items-center gap-2">
          <Plus size={16}/> Add money
        </Link>
      </header>

      {returnedFromCheckout ? (
        <div role="status" className="flex items-start gap-3 rounded-2xl border border-emerald-500/30 bg-emerald-950/30 p-4 text-sm text-emerald-200 backdrop-blur-md">
          <Clock className="mt-0.5 shrink-0 text-emerald-400" size={18} />
          <div>
            <p className="font-semibold text-white">Refreshing wallet balances...</p>
            <p className="mt-0.5 text-xs text-slate-300">
              Funds appear only after the payment provider confirms the payment and RackPay processes its callback.
            </p>
          </div>
        </div>
      ) : null}

      <div>
        {wallet?.balances.length === 0 ? (
          <div className="rounded-3xl border border-dashed border-slate-800 bg-slate-900/40 p-10 text-center backdrop-blur-sm">
            <div className="mx-auto grid h-12 w-12 place-items-center rounded-2xl border border-slate-800 bg-slate-800/50 text-slate-400">
              <WalletCards size={22} />
            </div>
            <p className="mt-4 font-semibold text-white">No currency balances yet</p>
            <p className="mt-1 text-xs text-slate-400 max-w-sm mx-auto">
              Open a currency balance below to start using your wallet.
            </p>
          </div>
        ) : (
          <>
            <div className="mb-4 flex items-center justify-between gap-3">
              <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">Your balances</p>
              {wallet && wallet.balances.length > 1 ? (
                <div className="flex items-center gap-3">
                  <div className="flex gap-1.5">
                    {wallet.balances.map((_, idx) => (
                      <span
                        key={idx}
                        className={`h-1.5 rounded-full transition-all ${
                          idx === activeCardIndex ? "w-6 bg-emerald-400" : "w-1.5 bg-slate-800"
                        }`}
                      />
                    ))}
                  </div>
                  <span className="text-xs text-slate-500 hidden sm:inline">Swipe <span aria-hidden="true">→</span></span>
                </div>
              ) : null}
            </div>
            <div
              aria-label="Wallet currency balances"
              onScroll={(e) => {
                const el = e.currentTarget;
                const index = Math.round(el.scrollLeft / (el.clientWidth * 0.75));
                setActiveCardIndex(Math.min(index, (wallet?.balances.length ?? 1) - 1));
              }}
              className="-mx-2 flex snap-x snap-mandatory gap-4 overflow-x-auto px-2 pb-2 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
            >
              {wallet?.balances.map((balance, index) => {
                const symbol = getCurrencySymbol(balance.currency);
                return (
                  <article
                    key={balance.balanceId}
                    aria-label={`${balance.currency} balance, ${index + 1} of ${wallet.balances.length}`}
                    className="min-w-[85%] snap-center rounded-3xl border border-slate-800 bg-[radial-gradient(ellipse_at_top_right,#1b3b2b_0,transparent_50%),linear-gradient(135deg,#0f172a,#090d16)] p-6 shadow-xl shadow-black/30 sm:min-w-[45%]"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex items-center gap-3">
                        <span className="grid h-10 w-10 place-items-center rounded-xl bg-slate-800/80 text-sm font-bold text-emerald-400">
                          {symbol}
                        </span>
                        <div>
                          <p className="text-xs font-bold uppercase tracking-wider text-slate-400">
                            {balance.currency} balance
                          </p>
                          <p className="text-xs text-slate-500">Active account</p>
                        </div>
                      </div>
                      <div className="rounded-2xl border border-emerald-500/20 bg-emerald-500/10 p-2.5 text-emerald-400">
                        <Wallet size={18} />
                      </div>
                    </div>
                    <div className="mt-6">
                      <p className="text-3xl font-black tracking-tight text-white">
                        {currencyFormatter.format(Number(balance.balance))}
                      </p>
                    </div>
                    <div className="mt-6 flex items-center justify-between border-t border-slate-800/80 pt-4 text-xs text-slate-500">
                      <span className="font-medium text-slate-400">RackPay Wallet</span>
                      <span className="rounded-full bg-slate-800/80 px-2.5 py-0.5 text-[10px] font-semibold text-slate-300">
                        {index + 1} / {wallet?.balances.length}
                      </span>
                    </div>
                  </article>
                );
              })}
            </div>
          </>
        )}
      </div>

      <div className="rounded-3xl border border-slate-800 bg-slate-900/80 p-6 shadow-xl shadow-black/20 backdrop-blur-md">
        <div className="flex items-center gap-3">
          <div className="rounded-2xl border border-slate-700 bg-slate-800 p-2.5 text-emerald-400">
            <Plus size={20} />
          </div>
          <div>
            <h3 className="font-semibold text-white">Open currency balance</h3>
            <p className="text-xs text-slate-400">
              Add another supported wallet currency instantly.
            </p>
          </div>
        </div>

        <div className="mt-6 space-y-4">
          <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
            {availableCurrencies.map((option) => {
              const isOpen = wallet?.balances.some((b) => b.currency === option) ?? false;
              const isSelected = currency === option;
              const symbol = getCurrencySymbol(option);

              return (
                <button
                  type="button"
                  key={option}
                  disabled={isOpen}
                  onClick={() => setCurrency(option)}
                  className={`flex items-center gap-3 rounded-2xl border p-3.5 text-left transition-all ${
                    isOpen
                      ? "border-slate-800/30 bg-slate-950/20 opacity-30 cursor-not-allowed"
                      : isSelected
                      ? "border-emerald-500 bg-emerald-500/10 text-white shadow-lg shadow-emerald-950/20"
                      : "border-slate-800 bg-slate-950/60 text-slate-400 hover:border-slate-700"
                  }`}
                >
                  <span className="grid h-8 w-8 place-items-center rounded-lg bg-slate-800 text-xs font-bold text-emerald-300">
                    {symbol}
                  </span>
                  <div className="min-w-0">
                    <p className="text-xs font-bold text-white">{option}</p>
                    <p className="text-[10px] text-slate-400 truncate">{isOpen ? "Active" : "Available"}</p>
                  </div>
                </button>
              );
            })}
          </div>

          <div className="flex justify-end pt-2">
            <button
              type="button"
              disabled={openBalanceMutation.isPending || currencyAlreadyOpen || unopenedCurrencies.length === 0}
              onClick={() => openBalanceMutation.mutate()}
              className="button button-primary w-full sm:w-auto px-6 py-3"
            >
              {openBalanceMutation.isPending ? "Opening…" : `Open ${currency} Balance`}
            </button>
          </div>
        </div>

        {unopenedCurrencies.length === 0 ? (
          <p className="mt-3 text-xs text-slate-500">
            All currently available currency balances are already open.
          </p>
        ) : null}
        {openBalanceMutation.isError ? (
          <p role="alert" className="mt-3 text-xs text-red-300">
            {errorMessage(openBalanceMutation.error, "The balance could not be opened. Please check the selected currency and try again.")}
          </p>
        ) : null}
      </div>


      <WalletTransactions accessToken={accessToken} />
    </section>
  );
}

function WalletTransactions({ accessToken }: { accessToken?: string }) {
  const transactionsQuery = useQuery({
    queryKey: ["wallet", "transactions", 0],
    queryFn: () => {
      if (!accessToken) throw new Error("Missing access token");
      return listWalletTransactions(accessToken);
    },
    enabled: Boolean(accessToken),
    staleTime: 15_000,
  });

  return (
    <div className="rounded-3xl border border-slate-800 bg-slate-900/80 p-6 shadow-xl backdrop-blur-md">
      <div className="flex items-center justify-between border-b border-slate-800/80 pb-4">
        <div>
          <h2 className="text-base font-semibold text-white">Recent activity</h2>
          <p className="text-xs text-slate-400">Latest wallet transactions</p>
        </div>
        <div className="rounded-xl border border-slate-800 bg-slate-800/50 p-2 text-emerald-400">
          <WalletCards size={18} />
        </div>
      </div>

      {transactionsQuery.isPending ? (
        <div className="mt-6 space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-14 animate-pulse rounded-2xl bg-slate-800/40" />
          ))}
        </div>
      ) : transactionsQuery.isError ? (
        <p className="mt-6 text-xs text-red-300">Activity is unavailable.</p>
      ) : transactionsQuery.data.content.length === 0 ? (
        <div className="py-10 text-center text-xs text-slate-500">No transactions yet.</div>
      ) : (
        <div className="mt-4 divide-y divide-slate-800/60">
          {transactionsQuery.data.content.map((transaction) => {
            const positive = ["CREDIT", "DEPOSIT", "REFUND"].includes(
              transaction.operationType,
            );

            return (
              <div
                key={transaction.transactionId}
                className="flex items-center justify-between gap-3 py-3.5 first:pt-2 last:pb-0"
              >
                <div className="flex min-w-0 items-center gap-3">
                  <div
                    className={`grid h-10 w-10 shrink-0 place-items-center rounded-2xl ${
                      positive ? "border border-emerald-500/20 bg-emerald-500/10 text-emerald-400" : "border border-slate-700 bg-slate-800 text-slate-300"
                    }`}
                  >
                    {positive ? (
                      <ArrowDownLeft size={18} />
                    ) : (
                      <ArrowUpRight size={18} />
                    )}
                  </div>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold text-white">
                      {transaction.operationType.replaceAll("_", " ")}
                    </p>
                    <p className="text-xs text-slate-500">
                      {formatDate(transaction.createdAt ?? "")} ·{" "}
                      <span
                        className={
                          transaction.status === "COMPLETED"
                            ? "text-emerald-400 font-medium"
                            : transaction.status === "FAILED"
                            ? "text-red-400 font-medium"
                            : "text-amber-400 font-medium"
                        }
                      >
                        {transaction.status}
                      </span>
                    </p>
                  </div>
                </div>
                <p className={`shrink-0 text-sm font-bold ${positive ? "text-emerald-400" : "text-slate-200"}`}>
                  {positive ? "+" : "-"}
                  {currencyFormatter.format(Number(transaction.amount))}{" "}
                  {transaction.currency}
                </p>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function WalletLoading() {
  return (
    <section className="mx-auto max-w-5xl space-y-6 pt-4 px-4 sm:px-0">
      <div className="h-8 w-40 animate-pulse rounded-xl bg-slate-800" />
      <div className="h-44 animate-pulse rounded-3xl bg-slate-900" />
      <div className="h-32 animate-pulse rounded-3xl bg-slate-900" />
    </section>
  );
}

function errorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    return error.correlationId
      ? `${error.message} (Reference: ${error.correlationId})`
      : error.message;
  }
  return error instanceof Error ? error.message : fallback;
}
