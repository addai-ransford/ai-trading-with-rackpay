import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import {
  ArrowDownLeft,
  ArrowUpRight,
  Plus,
  WalletCards,
} from "lucide-react";
import { useState } from "react";
import {
  type Currency,
  getWallet,
  listWalletTransactions,
  openWalletBalance,
} from "../api/walletApi";
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
          We could not load your wallet.
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

      <div className="grid gap-3">
        {wallet.balances.length === 0 ? (
          <div className="rounded-3xl border border-dashed border-slate-700 bg-slate-900/60 p-6 text-center">
            <WalletCards className="mx-auto text-slate-500" />
            <p className="mt-3 font-medium">No currency balances yet</p>
            <p className="mt-1 text-sm leading-6 text-slate-500">
              Open a currency balance to start using your wallet.
            </p>
          </div>
        ) : (
          wallet.balances.map((balance) => (
            <div
              key={balance.balanceId}
              className="rounded-3xl border border-slate-800 bg-slate-900 p-5"
            >
              <p className="text-sm text-slate-500">{balance.currency}</p>
              <p className="mt-2 text-3xl font-semibold tracking-tight">
                {currencyFormatter.format(Number(balance.balance))}
              </p>
            </div>
          ))
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
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
          <button
            type="button"
            disabled={openBalanceMutation.isPending}
            onClick={() => openBalanceMutation.mutate()}
            className="rounded-2xl bg-white px-4 py-3 text-sm font-semibold text-slate-950 disabled:opacity-50"
          >
            {openBalanceMutation.isPending ? "Opening…" : "Open"}
          </button>
        </div>

        {openBalanceMutation.isError ? (
          <p className="mt-3 text-sm text-red-300">
            The balance could not be opened. It may already exist.
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
