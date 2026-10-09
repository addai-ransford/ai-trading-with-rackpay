import { Bot, LogOut, Send, WalletCards } from "lucide-react";
import { Link } from "react-router-dom";
import { useAuthStore } from "../../../shared/auth/authStore";
import { logout } from "../../../shared/auth/keycloak";

export function HomePage() {
  const user = useAuthStore((state) => state.user);

  return (
    <section className="flex min-h-dvh flex-col justify-center gap-6 py-8">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-sm font-medium text-slate-400">Financial platform</p>
          <h1 className="mt-2 text-4xl font-semibold tracking-tight">RackPay</h1>
          <p className="mt-3 max-w-sm text-base leading-7 text-slate-400">
            Wallet, international remittance and risk-controlled AI trading in one experience.
          </p>
        </div>
        <button type="button" onClick={() => void logout()} aria-label="Sign out" className="rounded-xl border border-slate-800 p-2 text-slate-400 transition hover:bg-slate-900 hover:text-slate-100">
          <LogOut size={18} />
        </button>
      </div>

      <div className="rounded-2xl border border-slate-800 bg-slate-900 p-4">
        <p className="text-xs uppercase tracking-wide text-slate-500">Signed in as</p>
        <p className="mt-1 font-medium">{user?.name ?? user?.username ?? user?.email ?? "RackPay user"}</p>
        {user?.email ? <p className="mt-1 text-sm text-slate-500">{user.email}</p> : null}
      </div>

      <Link to="/wallet" className="flex items-center gap-3 rounded-2xl border border-slate-800 bg-slate-900 p-4 transition hover:bg-slate-800">
        <span className="rounded-xl bg-slate-800 p-2"><WalletCards size={18} /></span>
        <span><span className="block font-medium">Wallet</span><span className="mt-1 block text-sm text-slate-500">View balances, activity and add money.</span></span>
      </Link>

      <Link to="/remittance" className="flex items-center gap-3 rounded-2xl border border-slate-800 bg-slate-900 p-4 transition hover:bg-slate-800">
        <span className="rounded-xl bg-slate-800 p-2"><Send size={18} /></span>
        <span><span className="block font-medium">Remittance</span><span className="mt-1 block text-sm text-slate-500">Verify a recipient, get a quote and track transfers.</span></span>
      </Link>

      <Link to="/trading" className="flex items-center gap-3 rounded-2xl border border-slate-800 bg-slate-900 p-4 transition hover:bg-slate-800">
        <span className="rounded-xl bg-slate-800 p-2"><Bot size={18} /></span>
        <span><span className="block font-medium">AI Trading</span><span className="mt-1 block text-sm text-slate-500">Set a maximum amount, manage a session and review positions.</span></span>
      </Link>
    </section>
  );
}
