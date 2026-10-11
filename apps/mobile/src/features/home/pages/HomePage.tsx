import { ArrowRight, Send, Wallet, Activity } from "lucide-react";
import { Link } from "react-router-dom";

const features = [
  {
    title: "Wallet",
    description: "View balances and recent activity.",
    icon: Wallet,
    href: "/wallet",
    available: false,
  },
  {
    title: "Remittance",
    description: "Compare exchange rates and send money internationally.",
    icon: Send,
    href: "/remittance",
    available: true,
  },
  {
    title: "AI Trading",
    description: "Manage your trading preferences and activity.",
    icon: Activity,
    href: "/trading",
    available: false,
  },
];

export function HomePage() {
  return (
    <section className="flex min-h-dvh flex-col justify-center gap-6 py-8">
      <div>
        <p className="text-sm font-medium text-emerald-300">Financial platform</p>
        <h1 className="mt-2 text-4xl font-semibold tracking-tight">RackPay</h1>
        <p className="mt-3 max-w-sm text-base leading-7 text-slate-400">
          Wallet, remittance and AI-assisted trading in one mobile experience.
        </p>
      </div>

      <div className="grid gap-3">
        {features.map(({ title, description, icon: Icon, href, available }) => {
          const content = (
            <>
              <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-slate-800 text-emerald-300">
                <Icon size={21} />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block font-medium">{title}</span>
                <span className="mt-1 block text-sm leading-5 text-slate-400">{description}</span>
                {!available && <span className="mt-2 block text-xs text-slate-500">Coming next</span>}
              </span>
              <ArrowRight size={18} className="shrink-0 text-slate-500" />
            </>
          );

          return available ? (
            <Link
              key={title}
              to={href}
              className="flex items-center gap-3 rounded-2xl border border-emerald-400/30 bg-slate-900 p-4 transition hover:border-emerald-300/60 hover:bg-slate-900/80"
            >
              {content}
            </Link>
          ) : (
            <div key={title} className="flex items-center gap-3 rounded-2xl border border-slate-800 bg-slate-900/60 p-4 opacity-80">
              {content}
            </div>
          );
        })}
      </div>
    </section>
  );
}
