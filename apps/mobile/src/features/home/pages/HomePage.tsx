import { useQuery } from "@tanstack/react-query";
import {
  ArrowDownLeft,
  ArrowLeftRight,
  ArrowRight,
  ArrowUpRight,
  Bot,
  CreditCard,
  Settings2,
  ShieldCheck,
  WalletCards,
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

  return (
    <section>
      <div className="page-heading">
        <div>
          <p className="eyebrow">YOUR FINANCIAL SPACE</p>
          <h1>Good to have you here.</h1>
          <p className="page-description">One place for your money, international transfers and risk-controlled AI trading.</p>
        </div>
        <button type="button" onClick={() => void logout()} aria-label="Sign out" className="button button-secondary">
          Sign out
        </button>
      </div>

      <div className="notice-panel">
        <ShieldCheck size={22} />
        <div>
          <strong>Welcome{user?.name ? `, ${user.name.split(" ")[0]}` : user?.username ? `, ${user.username}` : ""}</strong>
          <p>Your wallet balances and transaction states are loaded from RackPay's backend.</p>
        </div>
      </div>

      <div className="section-heading">
        <div><h2>Wallet overview</h2><p>Available currency balances</p></div>
        <Link className="text-link" to="/wallet">View wallet <ArrowRight size={14}/></Link>
      </div>
      {walletQuery.isPending ? <div className="skeleton-card" /> : walletQuery.isError ? (
        <div className="inline-error">Unable to load wallet.<button onClick={() => void walletQuery.refetch()}>Retry</button></div>
      ) : balances.length ? (
        <div className="balance-grid">
          {balances.slice(0, 3).map((balance) => (
            <div className="mini-balance" key={balance.balanceId}>
              <span><WalletCards size={15}/> {balance.currency}</span>
              <strong>{money(balance.balance, balance.currency)}</strong>
              <small>Available balance</small>
            </div>
          ))}
        </div>
      ) : (
        <div className="empty-inline">No currency balances yet. <Link to="/wallet">Open a balance <ArrowRight size={14}/></Link></div>
      )}

      <div className="section-heading"><div><h2>Quick actions</h2><p>Choose what you want to do next</p></div></div>
      <div className="action-grid">
        <Link to="/wallet" className="action-card"><span className="action-icon"><WalletCards size={19}/></span><strong>Manage wallet</strong><p>Swipe through balances and view transaction history</p><ArrowRight className="action-arrow" size={16}/></Link>
        <Link to="/wallet/add-money" className="action-card"><span className="action-icon"><CreditCard size={19}/></span><strong>Add money</strong><p>Start a secure provider-hosted checkout</p><ArrowRight className="action-arrow" size={16}/></Link>
        <Link to="/remittance" className="action-card"><span className="action-icon"><ArrowLeftRight size={19}/></span><strong>Send money</strong><p>Review exchange rates and send internationally</p><ArrowRight className="action-arrow" size={16}/></Link>
        <Link to="/trading" className="action-card"><span className="action-icon"><Bot size={19}/></span><strong>AI Trading</strong><p>Trading sessions and risk controls</p><ArrowRight className="action-arrow" size={16}/></Link>
        <Link to="/admin/payment-providers" className="action-card"><span className="action-icon"><Settings2 size={19}/></span><strong>Platform settings</strong><p>Manage configured payment providers</p><ArrowRight className="action-arrow" size={16}/></Link>
      </div>

      <div className="section-heading">
        <div><h2>Recent activity</h2><p>Latest wallet transactions</p></div>
        <Link className="text-link" to="/wallet">Full history <ArrowRight size={14}/></Link>
      </div>
      {activityQuery.isPending ? <div className="skeleton-list" /> : activityQuery.isError ? (
        <div className="inline-error">Unable to load activity.<button onClick={() => void activityQuery.refetch()}>Retry</button></div>
      ) : activity.length ? (
        <div className="transaction-list">
          {activity.map((transaction) => (
            <div className="transaction-row" key={transaction.transactionId}>
              <div className="transaction-icon">{transaction.operationType.toLowerCase().includes("credit") ? <ArrowDownLeft size={18}/> : <ArrowUpRight size={18}/>}</div>
              <div className="transaction-info"><strong>{transaction.operationType.replaceAll("_", " ")}</strong><span>{new Date(transaction.createdAt).toLocaleDateString()}</span></div>
              <div className="transaction-value"><strong>{money(transaction.amount, transaction.currency)}</strong><span className={`state-pill state-${transaction.status.toLowerCase()}`}>{transaction.status.replaceAll("_", " ")}</span></div>
            </div>
          ))}
        </div>
      ) : (
        <div className="empty-state"><strong>No transactions yet</strong><p>Your latest wallet activity will appear here.</p><Link to="/remittance" className="text-link">Start a transfer <ArrowRight size={14}/></Link></div>
      )}
      <p className="compliance-note"><ShieldCheck size={14}/> RackPay's backend is authoritative for balances, payments and transfer status.</p>
    </section>
  );
}
