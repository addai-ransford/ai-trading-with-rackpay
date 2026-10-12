import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, CreditCard } from "lucide-react";
import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../../shared/api/httpClient";
import { env } from "../../../shared/config/env";
import { useAuthStore } from "../../../shared/auth/authStore";
import { type Currency } from "../../wallet/api/walletApi";
import { createPayment } from "../api/paymentApi";

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

export function PaymentPage() {
  const accessToken = useAuthStore((state) => state.accessToken);
  const user = useAuthStore((state) => state.user);
  const queryClient = useQueryClient();
  const [amount, setAmount] = useState("");
  const [currency, setCurrency] = useState<Currency>("EUR");
  const [error, setError] = useState<string>();

  const webhookConfigured = Boolean(env.paymentWebhookUrl);
  const returnUrl = useMemo(
    () => `${window.location.origin}/wallet?payment=return`,
    [],
  );

  const paymentMutation = useMutation({
    mutationFn: () => {
      if (!accessToken) throw new Error("Missing access token");
      if (!env.paymentWebhookUrl) {
        throw new Error("Payment webhook URL is not configured.");
      }

      const reference = `rackpay-${crypto.randomUUID()}`;

      return createPayment(accessToken, {
        reference,
        amount,
        currency,
        description: "RackPay wallet funding",
        returnUrl,
        webhookUrl: env.paymentWebhookUrl,
        customerEmail: user?.email,
      });
    },
    onSuccess: (payment) => {
      setError(undefined);
      void queryClient.invalidateQueries({ queryKey: ["wallet"] });

      if (payment.checkoutUrl) {
        window.location.assign(payment.checkoutUrl);
      }
    },
    onError: (cause) => {
      if (cause instanceof ApiError) {
        setError(
          cause.correlationId
            ? `${cause.message} (Reference: ${cause.correlationId})`
            : cause.message,
        );
        return;
      }
      setError(cause instanceof Error ? cause.message : "Payment could not be started.");
    },
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(undefined);

    const numericAmount = Number(amount);
    if (!Number.isFinite(numericAmount) || numericAmount <= 0) {
      setError("Enter an amount greater than zero.");
      return;
    }

    paymentMutation.mutate();
  }

  return (
    <section className="space-y-5">
      <header className="page-heading">
        <div>
          <p className="eyebrow">WALLET FUNDING</p>
          <h1>Add money</h1>
          <p className="page-description">Start a secure checkout with RackPay’s currently configured payment provider.</p>
        </div>
        <Link to="/wallet" className="button button-secondary"><ArrowLeft size={15}/> Back to wallet</Link>
      </header>

      <div className="form-panel">
        <div className="flex items-center gap-3">
          <div className="rounded-xl bg-slate-800 p-2">
            <CreditCard size={18} />
          </div>
          <div>
            <p className="font-medium">Secure checkout</p>
            <p className="text-sm text-slate-500">
              RackPay will send you to the configured payment provider.
            </p>
          </div>
        </div>

        <form onSubmit={submit} className="mt-5 space-y-4">
          <label className="block">
            <span className="text-sm text-slate-400">Amount</span>
            <input
              inputMode="decimal"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              placeholder="100.00"
              className="mt-2 w-full rounded-2xl border border-slate-700 bg-slate-950 px-4 py-3 outline-none focus:border-slate-500"
            />
          </label>

          <label className="block">
            <span className="text-sm text-slate-400">Currency</span>
            <select
              value={currency}
              onChange={(event) => setCurrency(event.target.value as Currency)}
              className="mt-2 w-full rounded-2xl border border-slate-700 bg-slate-950 px-4 py-3"
            >
              {currencies.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>

          {!webhookConfigured ? (
            <div className="rounded-2xl border border-amber-900/60 bg-amber-950/20 p-4 text-sm leading-6 text-amber-200">
              Payment checkout is not configured for this environment. Set
              <code className="mx-1 rounded bg-slate-950 px-1">
                VITE_PAYMENT_WEBHOOK_URL
              </code>
              to a publicly reachable backend callback URL for the active
              provider, using the route
              <code className="mx-1 rounded bg-slate-950 px-1">
                /api/v1/payments/webhooks/{"{provider}"}
              </code>
              . A provider cannot call localhost from the public internet.
            </div>
          ) : null}

          {error ? (
            <div className="rounded-2xl border border-red-900/60 bg-red-950/20 p-4 text-sm leading-6 text-red-200">
              {error}
            </div>
          ) : null}

          <button
            type="submit"
            disabled={paymentMutation.isPending || !webhookConfigured}
            className="w-full rounded-2xl bg-white px-4 py-3 font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {paymentMutation.isPending ? "Starting checkout…" : "Continue to payment"}
          </button>
        </form>
      </div>

      <p className="text-center text-xs leading-5 text-slate-600">
        The backend selects the active payment provider. RackPay does not store
        your card details.
      </p>
    </section>
  );
}
