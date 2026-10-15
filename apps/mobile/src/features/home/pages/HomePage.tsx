import { useQuery } from "@tanstack/react-query";
import {
  ArrowDownLeft,
  ArrowLeftRight,
  ArrowRight,
  ArrowUpRight,
  Bot,
  CreditCard,
  LockKeyhole,
  ShieldCheck,
  WalletCards,
  Sparkles,
  TrendingUp,
  Send,
  ChevronRight,
  Wallet,
} from "lucide-react";
import { Link } from "react-router-dom";
import { apiFetch } from "../../../shared/api/httpClient";
import { useAuthStore } from "../../../shared/auth/authStore";
import { logout } from "../../../shared/auth/keycloak";

type Balance = { balanceId: string; currency: string; balance: string | number };
type Transaction = {
  transactionId: string;
  operationType: string;
  amount: string | number;
  currency: string;
  status: string;
  createdAt: string;
};
type Page<T> = { content: T[]; totalElements: number };

function money(amount: string | number, currency: string) {
  try {
    return new Intl.NumberFormat(undefined, {
      style: "currency",
      currency,
      maximumFractionDigits: 2,
    }).format(Number(amount));
  } catch {
    return `${amount} ${currency}`;
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

export function HomePage() {
  const user = useAuthStore((state) => state.user);
  const accessToken = useAuthStore((state) => state.accessToken);

  const walletQuery = useQuery({
    queryKey: ["wallet"],
    queryFn: () => apiFetch<{ balances: Balance[] }>("/api/v1/wallet", {}, accessToken),
    enabled: Boolean(accessToken),
  });
  const activityQuery = useQuery({
    queryKey: ["wallet-transactions"],
    queryFn: () => apiFetch<Page<Transaction>>("/api/v1/wallet/transactions?page=0&size=5", {}, accessToken),
    enabled: Boolean(accessToken),
  });

  const balances = walletQuery.data?.balances ?? [];
  const activity = activityQuery.data?.content ?? [];

  if (!accessToken) {
    return (
      <div className="dashboard-page mx-auto max-w-6xl space-y-20 pb-20 pt-8 px-4 sm:px-6 lg:px-8">
        {/* Public Hero */}
        <section className="relative overflow-hidden rounded-[2.5rem] border border-slate-800 bg-[radial-gradient(ellipse_at_top,#132e21_0,transparent_60%),linear-gradient(135deg,#090d16,#05070a)] p-8 sm:p-14 shadow-2xl">
          <div className="grid items-center gap-12 lg:grid-cols-12 relative z-10">
            <div className="space-y-6 lg:col-span-7">
              <span className="inline-flex items-center gap-2 rounded-full border border-emerald-500/30 bg-emerald-500/10 px-4 py-1.5 text-xs font-semibold uppercase tracking-wider text-emerald-400">
                <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse" /> Money moves with you
              </span>
              <h1 className="text-4xl font-extrabold tracking-tight text-white sm:text-6xl leading-[1.1]">
                One secure place for everything your money can do.
              </h1>
              <p className="text-base text-slate-300 sm:text-lg max-w-xl leading-relaxed">
                Manage multi-currency balances, send money internationally and explore AI-assisted trading with risk limits you control.
              </p>
              <div className="flex flex-col sm:flex-row gap-4 pt-4">
                <Link to="/login?mode=register" className="button button-primary py-4 px-8 text-base font-semibold justify-center shadow-xl shadow-emerald-950/50">
                  Create your account <ArrowRight size={18} />
                </Link>
                <Link to="/rates" className="button button-secondary py-4 px-8 text-base font-semibold justify-center">
                  View exchange rates <ArrowLeftRight size={18} />
                </Link>
              </div>
              <div className="flex items-center gap-2 text-xs text-slate-400 pt-2">
                <LockKeyhole size={14} className="text-emerald-400" /> Secure sign-in powered by RackPay and Keycloak
              </div>
            </div>

            {/* Hero Interactive Preview Card */}
            <div className="lg:col-span-5 rounded-3xl border border-slate-700/80 bg-slate-900/90 p-6 shadow-2xl space-y-4 backdrop-blur-xl">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <div className="flex items-center gap-2.5">
                  <span className="grid h-8 w-8 place-items-center rounded-xl bg-emerald-500 text-slate-950 font-black">R</span>
                  <span className="text-xs font-bold uppercase tracking-wider text-slate-200">Financial Space</span>
                </div>
                <span className="rounded-full border border-emerald-500/30 bg-emerald-500/10 px-2.5 py-0.5 text-[10px] font-bold text-emerald-400">LIVE</span>
              </div>
              <div className="rounded-2xl border border-slate-800 bg-slate-950/80 p-4 space-y-1">
                <span className="text-xs text-emerald-400 font-medium uppercase tracking-wider">Multi-currency wallet</span>
                <p className="text-base font-extrabold text-white">One home for your balances</p>
                <p className="text-xs text-slate-400">Keep currencies organised in one place.</p>
              </div>
              <div className="space-y-2">
                <div className="flex items-center justify-between rounded-2xl border border-slate-800 bg-slate-950/50 p-3.5">
                  <div className="flex items-center gap-3">
                    <span className="rounded-xl border border-slate-800 bg-slate-800 p-2 text-emerald-400"><ArrowLeftRight size={16}/></span>
                    <span className="text-xs font-medium text-white">International transfers</span>
                  </div>
                  <ChevronRight size={16} className="text-slate-500" />
                </div>
                <div className="flex items-center justify-between rounded-2xl border border-slate-800 bg-slate-950/50 p-3.5">
                  <div className="flex items-center gap-3">
                    <span className="rounded-xl border border-slate-800 bg-slate-800 p-2 text-emerald-400"><Bot size={16}/></span>
                    <span className="text-xs font-medium text-white">AI trading controls</span>
                  </div>
                  <ChevronRight size={16} className="text-slate-500" />
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Feature Section */}
        <section className="space-y-8">
          <div className="text-center sm:text-left max-w-2xl">
            <span className="text-xs font-semibold uppercase tracking-wider text-emerald-400">Built around you</span>
            <h2 className="text-3xl font-bold tracking-tight text-white mt-1">Your money. Your decisions.</h2>
            <p className="text-sm text-slate-400 mt-2">Start with an account when you're ready. You can explore RackPay first.</p>
          </div>

          <div className="grid gap-6 sm:grid-cols-3">
            <div className="rounded-3xl border border-slate-800 bg-slate-900/60 p-8 space-y-4 shadow-xl backdrop-blur-md">
              <span className="grid h-12 w-12 place-items-center rounded-2xl border border-slate-700 bg-slate-800 text-emerald-400"><WalletCards size={22}/></span>
              <h3 className="font-bold text-white text-lg">Multi-currency wallet</h3>
              <p className="text-xs text-slate-400 leading-relaxed">Keep your currency balances and transaction history together seamlessly.</p>
            </div>
            <Link to="/rates" className="rounded-3xl border border-slate-800 bg-slate-900/60 p-8 space-y-4 shadow-xl backdrop-blur-md hover:border-emerald-500/50 transition group flex flex-col justify-between">
              <div className="space-y-4">
                <span className="grid h-12 w-12 place-items-center rounded-2xl border border-slate-700 bg-slate-800 text-emerald-400"><ArrowLeftRight size={22}/></span>
                <h3 className="font-bold text-white text-lg">Public exchange rates</h3>
                <p className="text-xs text-slate-400 leading-relaxed">Check published currency rates and estimate what your recipient could receive before registering.</p>
              </div>
              <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-400 group-hover:translate-x-1 transition-transform pt-2">Explore rates <ArrowRight size={14}/></span>
            </Link>
            <div className="rounded-3xl border border-slate-800 bg-slate-900/60 p-8 space-y-4 shadow-xl backdrop-blur-md">
              <span className="grid h-12 w-12 place-items-center rounded-2xl border border-slate-700 bg-slate-800 text-emerald-400"><Bot size={22}/></span>
              <h3 className="font-bold text-white text-lg">Risk-controlled trading</h3>
              <p className="text-xs text-slate-400 leading-relaxed">Set a maximum amount or trading range that automated systems must respect.</p>
            </div>
          </div>
        </section>

        <p className="flex items-center justify-center gap-2 text-xs text-slate-500 pt-4">
          <ShieldCheck size={14} className="text-emerald-400" /> Financial balances, rates, fees and transfer status are determined by RackPay's backend.
        </p>
      </div>
    );
  }

  return (
    <div className="dashboard-page mx-auto max-w-6xl space-y-10 pb-16 pt-4 px-4 sm:px-6 lg:px-8">
      {/* Dashboard Top Header */}
      <header className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between border-b border-slate-800/80 pb-6">
        <div>
          <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-500/20 bg-emerald-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-wider text-emerald-400">
            <Sparkles size={13} /> Financial Space
          </span>
          <h1 className="mt-2 text-3xl font-extrabold tracking-tight text-white">Good to have you here.</h1>
          <p className="mt-1 text-sm text-slate-400">One place for your money, international transfers and risk-controlled AI trading.</p>
        </div>
        <button type="button" onClick={() => void logout()} aria-label="Sign out" className="button button-secondary self-start sm:self-auto">
          Sign out
        </button>
      </header>

      {/* Welcome Banner */}
      <div className="flex items-start gap-4 rounded-3xl border border-emerald-500/30 bg-gradient-to-r from-emerald-950/40 via-slate-900 to-slate-900 p-6 text-sm text-emerald-200 shadow-xl backdrop-blur-md">
        <div className="rounded-2xl border border-emerald-500/30 bg-emerald-500/10 p-3 text-emerald-400 shrink-0">
          <ShieldCheck size={24} />
        </div>
        <div>
          <strong className="font-bold text-white text-base">
            Welcome{user?.name ? `, ${user.name.split(" ")[0]}` : user?.username ? `, ${user.username}` : ""}
          </strong>
          <p className="mt-1 text-xs text-slate-300 leading-relaxed">Your wallet balances and transaction states are loaded securely from RackPay's backend.</p>
        </div>
      </div>

      {/* Wallet Overview Section (Stacked Layout) */}
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-white">Wallet overview</h2>
            <p className="text-xs text-slate-400">Available currency balances</p>
          </div>
          <Link className="text-xs font-semibold text-emerald-400 hover:underline flex items-center gap-1" to="/wallet">
            View full wallet <ArrowRight size={14}/>
          </Link>
        </div>

        {walletQuery.isPending ? (
          <div className="space-y-3">
            {[1, 2].map((i) => (
              <div key={i} className="h-20 animate-pulse rounded-2xl bg-slate-900 border border-slate-800" />
            ))}
          </div>
        ) : walletQuery.isError ? (
          <div className="rounded-2xl border border-red-500/30 bg-red-950/20 p-4 text-xs text-red-300 flex items-center justify-between">
            <span>Unable to load wallet.</span>
            <button onClick={() => void walletQuery.refetch()} className="underline font-semibold">Retry</button>
          </div>
        ) : balances.length ? (
          <div className="space-y-3">
            {balances.slice(0, 3).map((balance) => {
              const symbol = getCurrencySymbol(balance.currency);
              return (
                <div
                  className="flex items-center justify-between rounded-2xl border border-slate-800 bg-[radial-gradient(ellipse_at_top_right,#1b3b2b_0,transparent_50%),linear-gradient(135deg,#0f172a,#090d16)] p-4 sm:p-5 shadow-lg hover:border-slate-700 transition"
                  key={balance.balanceId}
                >
                  <div className="flex items-center gap-3.5">
                    <span className="grid h-11 w-11 place-items-center rounded-xl bg-slate-800/80 text-sm font-bold text-emerald-400">
                      {symbol}
                    </span>
                    <div>
                      <p className="text-xs font-bold uppercase tracking-wider text-slate-400">{balance.currency} balance</p>
                      <p className="text-xs text-slate-500">Available funds</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-xl sm:text-2xl font-black tracking-tight text-white">
                      {money(balance.balance, balance.currency)}
                    </p>
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <div className="rounded-3xl border border-dashed border-slate-800 bg-slate-900/40 p-10 text-center space-y-3">
            <p className="text-sm font-medium text-slate-300">No currency balances yet.</p>
            <Link to="/wallet" className="button button-primary inline-flex items-center gap-2 text-xs py-2.5 px-5">
              Open a balance <ArrowRight size={14}/>
            </Link>
          </div>
        )}
      </section>

      {/* Quick Actions Horizontal Slide */}
      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-white">Quick actions</h2>
          <span className="text-xs text-slate-500 hidden sm:inline">Swipe <span aria-hidden="true">→</span></span>
        </div>

        <div
          aria-label="Quick actions drawer"
          className="-mx-4 flex snap-x snap-mandatory gap-3 overflow-x-auto px-4 pb-2 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        >
          <Link
            to="/wallet"
            className="flex min-w-[180px] shrink-0 snap-center items-center gap-3 rounded-2xl border border-slate-800 bg-slate-900/80 p-4 transition-all hover:border-emerald-500/50 hover:bg-slate-900"
          >
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl border border-slate-700 bg-slate-800 text-emerald-400">
              <WalletCards size={18} />
            </span>
            <div className="min-w-0">
              <strong className="block truncate text-xs font-bold text-white">Manage wallet</strong>
              <span className="text-[10px] text-slate-400">View balances</span>
            </div>
          </Link>

          <Link
            to="/wallet/add-money"
            className="flex min-w-[180px] shrink-0 snap-center items-center gap-3 rounded-2xl border border-slate-800 bg-slate-900/80 p-4 transition-all hover:border-emerald-500/50 hover:bg-slate-900"
          >
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl border border-slate-700 bg-slate-800 text-emerald-400">
              <CreditCard size={18} />
            </span>
            <div className="min-w-0">
              <strong className="block truncate text-xs font-bold text-white">Add money</strong>
              <span className="text-[10px] text-slate-400">Fund account</span>
            </div>
          </Link>

          <Link
            to="/remittance"
            className="flex min-w-[180px] shrink-0 snap-center items-center gap-3 rounded-2xl border border-slate-800 bg-slate-900/80 p-4 transition-all hover:border-emerald-500/50 hover:bg-slate-900"
          >
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl border border-slate-700 bg-slate-800 text-emerald-400">
              <Send size={18} />
            </span>
            <div className="min-w-0">
              <strong className="block truncate text-xs font-bold text-white">Send money</strong>
              <span className="text-[10px] text-slate-400">International</span>
            </div>
          </Link>

          <Link
            to="/rates"
            className="flex min-w-[180px] shrink-0 snap-center items-center gap-3 rounded-2xl border border-slate-800 bg-slate-900/80 p-4 transition-all hover:border-emerald-500/50 hover:bg-slate-900"
          >
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl border border-slate-700 bg-slate-800 text-emerald-400">
              <TrendingUp size={18} />
            </span>
            <div className="min-w-0">
              <strong className="block truncate text-xs font-bold text-white">Exchange rates</strong>
              <span className="text-[10px] text-slate-400">Live indicators</span>
            </div>
          </Link>

          <Link
            to="/trading"
            className="flex min-w-[180px] shrink-0 snap-center items-center gap-3 rounded-2xl border border-slate-800 bg-slate-900/80 p-4 transition-all hover:border-emerald-500/50 hover:bg-slate-900"
          >
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl border border-slate-700 bg-slate-800 text-emerald-400">
              <Bot size={18} />
            </span>
            <div className="min-w-0">
              <strong className="block truncate text-xs font-bold text-white">AI Trading</strong>
              <span className="text-[10px] text-slate-400">Risk controls</span>
            </div>
          </Link>
        </div>
      </section>

      {/* Recent Activity Section */}
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-white">Recent activity</h2>
            <p className="text-xs text-slate-400">Latest wallet transactions</p>
          </div>
          <Link className="text-xs font-semibold text-emerald-400 hover:underline flex items-center gap-1" to="/wallet">
            Full history <ArrowRight size={14}/>
          </Link>
        </div>

        {activityQuery.isPending ? (
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-16 animate-pulse rounded-2xl bg-slate-900 border border-slate-800" />
            ))}
          </div>
        ) : activityQuery.isError ? (
          <div className="rounded-2xl border border-red-500/30 bg-red-950/20 p-4 text-xs text-red-300 flex items-center justify-between">
            <span>Unable to load activity.</span>
            <button onClick={() => void activityQuery.refetch()} className="underline font-semibold">Retry</button>
          </div>
        ) : activity.length ? (
          <div className="rounded-3xl border border-slate-800 bg-slate-900/80 p-6 shadow-xl divide-y divide-slate-800/60 backdrop-blur-md">
            {activity.map((transaction) => {
              const positive = transaction.operationType.toLowerCase().includes("credit") || transaction.operationType.toLowerCase().includes("deposit") || transaction.operationType.toLowerCase().includes("refund");
              return (
                <div className="flex items-center justify-between gap-4 py-4 first:pt-0 last:pb-0" key={transaction.transactionId}>
                  <div className="flex min-w-0 items-center gap-3.5">
                    <div className={`grid h-11 w-11 shrink-0 place-items-center rounded-2xl ${
                      positive ? "border border-emerald-500/20 bg-emerald-500/10 text-emerald-400" : "border border-slate-700 bg-slate-800 text-slate-300"
                    }`}>
                      {positive ? <ArrowDownLeft size={18}/> : <ArrowUpRight size={18}/>}
                    </div>
                    <div className="min-w-0">
                      <strong className="text-sm font-semibold text-white block truncate">
                        {transaction.operationType.replaceAll("_", " ")}
                      </strong>
                      <span className="text-xs text-slate-500">{new Date(transaction.createdAt).toLocaleDateString()}</span>
                    </div>
                  </div>
                  <div className="text-right shrink-0">
                    <strong className={`text-sm font-bold block ${positive ? "text-emerald-400" : "text-slate-200"}`}>
                      {positive ? "+" : "-"}{money(transaction.amount, transaction.currency)}
                    </strong>
                    <span className={`inline-block mt-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold uppercase ${
                      transaction.status.toLowerCase() === "completed" ? "bg-emerald-500/10 text-emerald-400 border border-emerald-500/20" : "bg-amber-500/10 text-amber-400 border border-amber-500/20"
                    }`}>
                      {transaction.status.replaceAll("_", " ")}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <div className="rounded-3xl border border-slate-800 bg-slate-900/60 p-10 text-center space-y-3">
            <strong className="text-white text-sm font-semibold block">No transactions yet</strong>
            <p className="text-xs text-slate-400 max-w-sm mx-auto">Your latest wallet activity will appear here once you make a deposit or transfer.</p>
            <div className="pt-2">
              <Link to="/remittance" className="button button-primary inline-flex items-center gap-2 text-xs py-2.5 px-5">
                Start a transfer <ArrowRight size={14}/>
              </Link>
            </div>
          </div>
        )}
      </section>

      <p className="flex items-center gap-2 text-xs text-slate-500 pt-4">
        <ShieldCheck size={14} className="text-emerald-400" /> RackPay's backend is authoritative for balances, payments and transfer status.
      </p>
    </div>
  );
}
