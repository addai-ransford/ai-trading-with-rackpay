import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeftRight, ArrowRight, RefreshCw, ShieldCheck } from "lucide-react";
import { Link } from "react-router-dom";
import { apiFetch } from "../../../shared/api/httpClient";
import { useAuthStore } from "../../../shared/auth/authStore";

type ExchangeRate = {
  sourceCurrency: string;
  destinationCurrency: string;
  rate: string | number;
  rateType: "INDICATIVE" | string;
};

const currencyNames: Record<string, string> = {
  EUR: "Euro",
  GBP: "British pound",
  USD: "US dollar",
  GHS: "Ghanaian cedi",
  KES: "Kenyan shilling",
  NGN: "Nigerian naira",
  XOF: "West African CFA franc",
  UGX: "Ugandan shilling",
};

function money(value: number, currency: string) {
  try {
    return new Intl.NumberFormat(undefined, {
      style: "currency",
      currency,
      maximumFractionDigits: 2,
    }).format(value);
  } catch {
    return `${value.toFixed(2)} ${currency}`;
  }
}

export function ExchangeRatesPage() {
  const authenticated = useAuthStore((state) => state.authenticated);
  const [amount, setAmount] = useState("100");
  const [selectedPair, setSelectedPair] = useState("");

  const ratesQuery = useQuery({
    queryKey: ["public", "exchange-rates"],
    queryFn: () => apiFetch<ExchangeRate[]>("/api/v1/public/exchange-rates"),
    staleTime: 60_000,
    refetchInterval: 60_000,
  });

  const rates = ratesQuery.data ?? [];
  const selectedRate = rates.find(
    (item) => `${item.sourceCurrency}_${item.destinationCurrency}` === selectedPair,
  ) ?? rates.find((item) => item.sourceCurrency === "EUR" && item.destinationCurrency === "GHS") ?? rates[0];

  const pairKey = selectedRate
    ? `${selectedRate.sourceCurrency}_${selectedRate.destinationCurrency}`
    : "";
  const sourceAmount = Number(amount);
  const destinationAmount = useMemo(() => {
    if (!selectedRate || !Number.isFinite(sourceAmount) || sourceAmount <= 0) return undefined;
    return sourceAmount * Number(selectedRate.rate);
  }, [selectedRate, sourceAmount]);

  return (
    <section className="rates-page space-y-7">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="eyebrow">RACKPAY EXCHANGE RATES</p>
          <h1>Know your rate before you send.</h1>
          <p className="page-description">
            Check RackPay's published exchange rates and estimate what your recipient could receive.
          </p>
        </div>
        <Link to={authenticated ? "/remittance" : "/login?mode=register"} className="button button-primary">
          Send money <ArrowRight size={16} />
        </Link>
      </div>

      <div className="rates-panel rounded-2xl p-6 sm:p-8">
        <div className="rates-panel-heading mb-6 flex items-center gap-3">
          <span className="rates-icon grid h-11 w-11 place-items-center rounded-xl"><ArrowLeftRight size={22} /></span>
          <div>
            <h2 className="rates-panel-title text-lg font-semibold">Exchange rate calculator</h2>
            <p className="rates-muted text-xs">Use the published rate to estimate your transfer.</p>
          </div>
        </div>

        {ratesQuery.isPending ? (
          <div className="rates-state rounded-xl p-5 text-sm" role="status">Loading published rates…</div>
        ) : ratesQuery.isError ? (
          <div className="rates-error rounded-xl p-5 text-sm" role="alert">
            Exchange rates are temporarily unavailable. Please try again shortly.
            <button type="button" className="ml-3 underline" onClick={() => void ratesQuery.refetch()}>Try again</button>
          </div>
        ) : rates.length === 0 ? (
          <div className="rates-empty rounded-xl p-5">
            <strong>Rates are being prepared</strong>
            <p className="rates-muted mt-2 text-sm leading-6">
              RackPay has not published an exchange rate yet. We will display a rate here once a corridor is configured. We will never substitute an invented rate.
            </p>
          </div>
        ) : selectedRate ? (
          <div className="grid gap-5 lg:grid-cols-[1fr_1fr]">
            <div className="space-y-4">
              <label className="rates-field-label block text-xs font-semibold" htmlFor="rate-pair">Currency pair</label>
              <select id="rate-pair" className="form-control" value={pairKey} onChange={(event) => setSelectedPair(event.target.value)}>
                {rates.map((item) => {
                  const key = `${item.sourceCurrency}_${item.destinationCurrency}`;
                  return <option key={key} value={key}>{item.sourceCurrency} → {item.destinationCurrency}</option>;
                })}
              </select>
              <label className="rates-field-label block text-xs font-semibold" htmlFor="send-amount">You send ({selectedRate.sourceCurrency})</label>
              <input id="send-amount" className="form-control" type="number" min="0.01" step="0.01" inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} />
              <p className="rates-muted text-xs">{currencyNames[selectedRate.sourceCurrency] ?? selectedRate.sourceCurrency} to {currencyNames[selectedRate.destinationCurrency] ?? selectedRate.destinationCurrency}</p>
            </div>
            <div className="rates-result flex flex-col justify-center rounded-xl p-5">
              <p className="rates-muted text-xs">Published indicative rate</p>
              <p className="rates-rate mt-2 text-2xl font-semibold tracking-tight">
                1 {selectedRate.sourceCurrency} = {Number(selectedRate.rate).toLocaleString(undefined, { maximumFractionDigits: 6 })} {selectedRate.destinationCurrency}
              </p>
              <div className="rates-divider my-5 h-px" />
              <p className="rates-muted text-xs">Estimated recipient amount, before fees</p>
              <p className="rates-amount mt-2 break-words text-3xl font-semibold">
                {destinationAmount === undefined ? "—" : money(destinationAmount, selectedRate.destinationCurrency)}
              </p>
              <Link to={authenticated ? "/remittance" : "/login?mode=register"} className="button button-primary mt-5 w-full">
                Get a transfer quote <ArrowRight size={16} />
              </Link>
            </div>
          </div>
        ) : null}
      </div>

      <div className="grid gap-3 md:grid-cols-2">
        <article className="rates-info-card rounded-xl p-5">
          <h3 className="rates-info-title font-semibold">Clear before you confirm</h3>
          <p className="rates-info-copy mt-2 text-sm leading-6">Your final quote shows the applicable transfer fee, the confirmed exchange rate and the amount your recipient will receive before you fund the transfer.</p>
        </article>
        <article className="rates-info-card rounded-xl p-5">
          <h3 className="rates-info-title font-semibold">Rates are not a promise until quoted</h3>
          <p className="rates-info-copy mt-2 text-sm leading-6">Published rates are indicative. The backend-issued transfer quote is authoritative and expires at the time shown during checkout.</p>
        </article>
      </div>
      <p className="compliance-note"><ShieldCheck size={14} /> Exchange rates and transfer fees are supplied by RackPay's backend. The calculator is an estimate and does not include fees.</p>
      <div className="rates-muted flex items-center gap-2 text-xs"><RefreshCw size={13} /> {ratesQuery.isFetching ? "Checking published rates…" : "Published rates are checked periodically."}</div>
    </section>
  );
}
