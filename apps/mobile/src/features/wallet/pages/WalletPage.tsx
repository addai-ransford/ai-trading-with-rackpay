import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import {
  ArrowDownLeft,
  ArrowUpRight,
  Plus,
  WalletCards,
} from "lucide-react";
import { useEffect, useState } from "react";
import {
  type Currency,
  getWallet,
  listWalletTransactions,
  openWalletBalance,
} from "../api/walletApi";
import { ApiError } from "../../../shared/api/httpClient";
import { useAuthStore } from "../../../shared/auth/authStore";

const currencies: Currency[] = [
  "EUR",
  "USD",
  "GBP",
  "GHS",
  "KES",
  "NGN",
  "XOF",
  "UGX",
];

const currencyFormatter = new Intl.NumberFormat(undefined, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function WalletPage() {
  const accessToken = useAuthStore((state) => state.accessToken);
  const queryClient = useQueryClient();
  const [currency, setCurrency] = useState<Currency>("EUR");
  const [searchParams, setSearchParams] = useSearchParams();
  const returnedFromCheckout = searchParams.get("payment") === "return";

  useEffect(() => {
    if (!returnedFromCheckout) return;
    void queryClient.invalidateQueries({ queryKey: ["wallet"] });
    const timeout = window.setTimeout(() => {
      searchParams.delete("payment");
      setSearchParams(searchParams, { replace: true });
    }, 10_000);
    return () => window.clearTimeout(timeout);
  }, [queryClient, returnedFromCheckout, searchParams, setSearchParams]);

  const walletQuery = useQuery({
    queryKey: ["wallet"],
    queryFn: () => getWallet(accessToken!),
    enabled: Boolean(accessToken),
    staleTime: 30_000,
  });

  const openBalanceMutation = useMutation({
    mutationFn: () => openWalletBalance(accessToken!, currency),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["wallet"] });
    },
  });

  if (walletQuery.isPending) {
    return <WalletLoading />;
  }

  if (walletQuery.isError) {
    return (
      <section className="flex min-h-dvh flex-col justify-center gap-4">
        <p className="text-sm text-red-300">
          {errorMessage(walletQuery.error, "We could not load your wallet.")}
        </p>
        <button
          type="button"
          onClick={() => void walletQuery.refetch()}
          className="rounded-2xl bg-white px-4 py-3 font-semibold text-slate-950"
        >
          Try again
        </button>
      </section>
    );
  }

  const wallet = walletQuery.data;
  const currencyAlreadyOpen = wallet.balances.some((balance) => balance.currency === currency);
  const availableCurrencies = currencies.filter(
    (option) => !wallet.balances.some((balance) => balance.currency === option),
  );

  return (
    <section className="flex min-h-dvh flex-col gap-6 pb-8 pt-4">
      <header className="flex items-center justify-between">
        <div>
          <p className="text-sm text-slate-500">Your money</p>
          <h1 className="mt-1 text-3xl font-semibold tracking-tight">Wallet</h1>
        </div>
        <Link
          to="/"
          className="rounded-xl border border-slate-800 px-3 py-2 text-sm text-slate-300"
        >
          Home
        </Link>
      </header>

      {returnedFromCheckout ? (
        <div role="status" className="rounded-2xl border border-slate-700 bg-slate-900 p-4 text-sm leading-6 text-slate-300">
          You returned from checkout. We are refreshing your wallet; funds appear only after the payment provider confirms the payment and RackPay processes its callback. This page does not assume the payment succeeded.
        </div>
      ) : null}

      <div>
        {wallet.balances.length === 0 ? (
          <div className="rounded-3xl border border-dashed border-slate-700 bg-slate-900/60 p-6 text-center">
            <WalletCards className="mx-auto text-slate-500" />
            <p className="mt-3 font-medium">No currency balances yet</p>
            <p className="mt-1 text-sm leading-6 text-slate-500">
              Open a currency balance to start using your wallet.
            </p>
          </div>
        ) : (
          <>
            <div className="mb-3 flex items-center justify-between gap-3">
              <p className="text-sm text-slate-400">Your balances</p>
              {wallet.balances.length > 1 ? (
                <p className="text-xs text-slate-500">Swipe to see more <span aria-hidden="true">→</span></p>
              ) : null}
            </div>
            <div
              aria-label="Wallet currency balances"
              className="-mx-1 flex snap-x snap-mandatory gap-3 overflow-x-auto px-1 pb-3 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
            >
              {wallet.balances.map((balance, index) => (
                <article
                  key={balance.balanceId}
                  aria-label={`${balance.currency} balance, ${index + 1} of ${wallet.balances.length}`}
                  className="min-w-[84%] snap-center rounded-3xl border border-slate-700 bg-gradient-to-br from-slate-900 via-slate-900 to-slate-800 p-5 sm:min-w-[70%]"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="text-sm text-slate-400">{balance.currency} balance</p>
                      <p className="mt-3 text-3xl font-semibold tracking-tight">
                        {currencyFormatter.format(Number(balance.balance))}
                      </p>
                    </div>
                    <div className="rounded-2xl border border-slate-700 bg-slate-800/80 p-3">
                      <WalletCards size={20} className="text-slate-300" />
                    </div>
                  </div>
                  <div className="mt-8 flex items-center justify-between border-t border-slate-700/80 pt-3">
                    <span className="text-xs text-slate-500">RackPay Wallet</span>
                    <span className="text-xs text-slate-400">{index + 1} / {wallet.balances.length}</span>
                  </div>
                </article>
              ))}
            </div>
          </>
        )}
      </div>

      <div className="rounded-3xl border border-slate-800 bg-slate-900 p-5">
        <div className="flex items-center gap-3">
          <div className="rounded-xl bg-slate-800 p-2">
            <Plus size={18} />
          </div>
          <div>
            <p className="font-medium">Open currency balance</p>
            <p className="text-sm text-slate-500">
              Add another supported wallet currency.
            </p>
          </div>
        </div>

        <div className="mt-4 flex gap-2">
          <select
            value={currency}
            onChange={(event) => setCurrency(event.target.value as Currency)}
            className="min-w-0 flex-1 rounded-2xl border border-slate-700 bg-slate-950 px-3 py-3 text-sm"
          >
            {currencies.map((option) => (
              <option
                key={option}
                value={option}
                disabled={wallet.balances.some((balance) => balance.currency === option)}
              >
                {option}{wallet.balances.some((balance) => balance.currency === option) ? " · already open" : ""}
              </option>
            ))}
          </select>
          <button
            type="button"
            disabled={openBalanceMutation.isPending || currencyAlreadyOpen || availableCurrencies.length === 0}
            onClick={() => openBalanceMutation.mutate()}
            className="rounded-2xl bg-white px-4 py-3 text-sm font-semibold text-slate-950 disabled:opacity-50"
          >
            {openBalanceMutation.isPending ? "Opening…" : "Open"}
          </button>
        </div>

        {availableCurrencies.length === 0 ? (
          <p className="mt-3 text-sm text-slate-500">
            All currently supported currency balances are already open.
          </p>
        ) : null}
        {openBalanceMutation.isError ? (
          <p role="alert" className="mt-3 text-sm text-red-300">
            {errorMessage(openBalanceMutation.error, "The balance could not be opened. Please check the selected currency and try again.")}
          </p>
        ) : null}
      </div>

      <Link
        to="/wallet/add-money"
        className="flex items-center justify-center rounded-2xl bg-white px-4 py-3 font-semibold text-slate-950 transition hover:bg-slate-200"
      >
        Add money
      </Link>

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
    <div className="rounded-3xl border border-slate-800 bg-slate-900 p-5">
      <div className="flex items-center justify-between">
        <div>
          <p className="font-medium">Recent activity</p>
          <p className="text-sm text-slate-500">Latest wallet transactions</p>
        </div>
        <WalletCards size={18} className="text-slate-500" />
      </div>

      {transactionsQuery.isPending ? (
        <p className="mt-5 text-sm text-slate-500">Loading activity…</p>
      ) : transactionsQuery.isError ? (
        <p className="mt-5 text-sm text-red-300">Activity is unavailable.</p>
      ) : transactionsQuery.data.content.length === 0 ? (
        <p className="mt-5 text-sm text-slate-500">No transactions yet.</p>
      ) : (
        <div className="mt-4 divide-y divide-slate-800">
          {transactionsQuery.data.content.map((transaction) => {
            const positive = ["CREDIT", "DEPOSIT", "REFUND"].includes(
              transaction.operationType,
            );

            return (
              <div
                key={transaction.transactionId}
                className="flex items-center justify-between gap-3 py-3 first:pt-0 last:pb-0"
              >
                <div className="flex min-w-0 items-center gap-3">
                  <div className="rounded-xl bg-slate-800 p-2">
                    {positive ? (
                      <ArrowDownLeft size={16} />
                    ) : (
                      <ArrowUpRight size={16} />
                    )}
                  </div>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">
                      {transaction.operationType.replaceAll("_", " ")}
                    </p>
                    <p className="text-xs text-slate-500">
                      {transaction.status}
                    </p>
                  </div>
                </div>
                <p className="shrink-0 text-sm font-medium">
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
    <section className="flex min-h-dvh flex-col justify-center gap-3">
      <div className="h-8 w-32 animate-pulse rounded-lg bg-slate-800" />
      <div className="h-28 animate-pulse rounded-3xl bg-slate-900" />
      <div className="h-28 animate-pulse rounded-3xl bg-slate-900" />
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
