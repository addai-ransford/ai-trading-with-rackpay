export function HomePage() {
  return (
    <section className="flex min-h-dvh flex-col justify-center gap-6">
      <div>
        <p className="text-sm font-medium text-slate-400">Financial platform</p>
        <h1 className="mt-2 text-4xl font-semibold tracking-tight">RackPay</h1>
        <p className="mt-3 max-w-sm text-base leading-7 text-slate-400">
          Wallet, remittance and AI-assisted trading in one mobile experience.
        </p>
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
