import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, CheckCircle2, CircleAlert, ShieldCheck } from "lucide-react";
import { Link } from "react-router-dom";
import { ApiError } from "../../../shared/api/httpClient";
import { useAuthStore } from "../../../shared/auth/authStore";
import {
  activatePaymentProvider,
  listPaymentProviders,
  setPaymentProviderEnabled,
  type PaymentProvider,
} from "../api/paymentProviderAdminApi";

const providerLabels: Record<string, string> = {
  MOLLIE: "Mollie",
  STRIPE: "Stripe",
  ADYEN: "Adyen",
  PAYPAL: "PayPal",
};

function describeError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return "Your account is not authorised to manage platform payment providers.";
    }
    if (error.status === 401) {
      return "Your session has expired. Sign in again and retry.";
    }
    return error.correlationId
      ? `${error.message} (Reference: ${error.correlationId})`
      : error.message;
  }
  return error instanceof Error ? error.message : "The request could not be completed.";
}

export function PaymentProviderAdminPage() {
  const accessToken = useAuthStore((state) => state.accessToken);
  const queryClient = useQueryClient();

  const providersQuery = useQuery({
    queryKey: ["admin", "payment-providers"],
    queryFn: () => {
      if (!accessToken) throw new Error("Missing access token");
      return listPaymentProviders(accessToken);
    },
    enabled: Boolean(accessToken),
    retry: (count, error) => !(error instanceof ApiError && [401, 403].includes(error.status)) && count < 1,
  });

  const activateMutation = useMutation({
    mutationFn: (provider: PaymentProvider["provider"]) => {
      if (!accessToken) throw new Error("Missing access token");
      return activatePaymentProvider(accessToken, provider);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin", "payment-providers"] }),
  });

  const enabledMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) => {
      if (!accessToken) throw new Error("Missing access token");
      return setPaymentProviderEnabled(accessToken, id, enabled);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin", "payment-providers"] }),
  });

  const error = providersQuery.error ?? activateMutation.error ?? enabledMutation.error;
  const isSaving = activateMutation.isPending || enabledMutation.isPending;
  const providers = providersQuery.data ?? [];

  return (
    <section className="min-h-dvh space-y-6 py-8">
      <Link to="/" className="inline-flex items-center gap-2 text-sm text-slate-400 transition hover:text-white">
        <ArrowLeft size={16} /> Back to RackPay
      </Link>

      <header className="flex items-start gap-3">
        <span className="rounded-2xl border border-slate-800 bg-slate-900 p-3 text-cyan-300">
          <ShieldCheck size={22} />
        </span>
        <div>
          <p className="text-sm font-medium text-slate-400">Platform administration</p>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight">Payment providers</h1>
          <p className="mt-2 text-sm leading-6 text-slate-400">
            Choose which configured provider starts new wallet-funding checkouts. Existing payments keep their own provider and status.
          </p>
        </div>
      </header>

      {error ? (
        <div role="alert" className="flex gap-3 rounded-2xl border border-rose-900/70 bg-rose-950/40 p-4 text-sm text-rose-200">
          <CircleAlert size={18} className="mt-0.5 shrink-0" />
          <div>
            <p className="font-medium">Could not load or update providers</p>
            <p className="mt-1 leading-6">{describeError(error)}</p>
            {providersQuery.isError ? (
              <button type="button" onClick={() => void providersQuery.refetch()} className="mt-3 font-medium underline underline-offset-4">
                Try again
              </button>
            ) : null}
          </div>
        </div>
      ) : null}

      {providersQuery.isPending ? (
        <div className="rounded-2xl border border-slate-800 bg-slate-900 p-5 text-sm text-slate-400">Loading configured providers…</div>
      ) : null}

      {!providersQuery.isPending && !error && providers.length === 0 ? (
        <div className="rounded-2xl border border-slate-800 bg-slate-900 p-5 text-sm text-slate-400">No payment providers are configured.</div>
      ) : null}

      <Link to="/admin/admins/new" className="inline-flex items-center rounded-xl border border-slate-700 px-4 py-2.5 text-sm font-medium text-slate-200 transition hover:bg-slate-800">Create administrator</Link>

      <div className="space-y-3">
        {providers.map((provider) => (
          <article key={provider.id} className="rounded-2xl border border-slate-800 bg-slate-900 p-4">
            <div className="flex items-start justify-between gap-3">
              <div>
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="font-semibold">{providerLabels[provider.provider] ?? provider.provider}</h2>
                  {provider.active ? (
                    <span className="inline-flex items-center gap-1 rounded-full border border-emerald-800 bg-emerald-950/50 px-2 py-1 text-xs font-medium text-emerald-300">
                      <CheckCircle2 size={12} /> Active
                    </span>
                  ) : null}
                </div>
                <p className="mt-1 text-sm text-slate-400">
                  Environment: <span className="capitalize text-slate-300">{provider.environment.toLowerCase()}</span>
                </p>
                <p className="mt-1 text-xs text-slate-500">
                  {provider.enabled ? "Enabled for selection" : "Disabled"}
                  {provider.updatedAt ? ` · Updated ${new Date(provider.updatedAt).toLocaleString()}` : ""}
                </p>
              </div>
              <span className={`rounded-lg px-2.5 py-1 text-xs font-medium ${provider.enabled ? "bg-emerald-950 text-emerald-300" : "bg-slate-800 text-slate-400"}`}>
                {provider.enabled ? "Enabled" : "Disabled"}
              </span>
            </div>

            <div className="mt-4 flex flex-wrap gap-2">
              <button
                type="button"
                disabled={isSaving || provider.active || !provider.enabled}
                onClick={() => activateMutation.mutate(provider.provider)}
                className="rounded-xl bg-cyan-400 px-4 py-2.5 text-sm font-semibold text-slate-950 transition hover:bg-cyan-300 disabled:cursor-not-allowed disabled:opacity-40"
              >
                {activateMutation.isPending && activateMutation.variables === provider.provider ? "Activating…" : "Make active"}
              </button>
              <button
                type="button"
                disabled={isSaving || provider.active}
                onClick={() => enabledMutation.mutate({ id: provider.id, enabled: !provider.enabled })}
                className="rounded-xl border border-slate-700 px-4 py-2.5 text-sm font-medium text-slate-200 transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-40"
              >
                {enabledMutation.isPending && enabledMutation.variables?.id === provider.id
                  ? "Saving…"
                  : provider.enabled ? "Disable provider" : "Enable provider"}
              </button>
            </div>
            {provider.active && !provider.enabled ? (
              <p className="mt-3 text-xs text-amber-300">This provider is active but disabled; resolve this configuration before switching production traffic.</p>
            ) : null}
          </article>
        ))}
      </div>

      <p className="text-xs leading-5 text-slate-500">
        These controls are enforced by the backend’s platform-admin authorization. Provider credentials are never displayed in this screen.
      </p>
    </section>
  );
}
