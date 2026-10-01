import { LogOut } from "lucide-react";
import { useAuthStore } from "../../../shared/auth/authStore";
import { logout } from "../../../shared/auth/keycloak";

export function HomePage() {
  const user = useAuthStore((state) => state.user);

  return (
    <section className="flex min-h-dvh flex-col justify-center gap-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-sm font-medium text-slate-400">Financial platform</p>
          <h1 className="mt-2 text-4xl font-semibold tracking-tight">RackPay</h1>
          <p className="mt-3 max-w-sm text-base leading-7 text-slate-400">
            Wallet, remittance and AI-assisted trading in one mobile experience.
          </p>
        </div>

        <button
          type="button"
          onClick={() => void logout()}
          aria-label="Sign out"
          className="rounded-xl border border-slate-800 p-2 text-slate-400 transition hover:bg-slate-900 hover:text-slate-100"
        >
          <LogOut size={18} />
        </button>
      </div>

      <div className="rounded-2xl border border-slate-800 bg-slate-900 p-4">
        <p className="text-xs uppercase tracking-wide text-slate-500">Signed in as</p>
        <p className="mt-1 font-medium">{user?.name ?? user?.username ?? user?.email ?? "RackPay user"}</p>
        {user?.email ? <p className="mt-1 text-sm text-slate-500">{user.email}</p> : null}
      </div>

      <div className="grid gap-3">
        {["Wallet", "Remittance", "AI Trading"].map((feature) => (
          <div
            key={feature}
            className="rounded-2xl border border-slate-800 bg-slate-900 p-4"
          >
            <p className="font-medium">{feature}</p>
            <p className="mt-1 text-sm text-slate-500">Coming next.</p>
          </div>
        ))}
      </div>
    </section>
  );
}
