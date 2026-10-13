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
    <section className="space-y-7">
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

      <div className="rounded-2xl border border-[#34463a] bg-[radial-gradient(ellipse_at_95%_0%,#233827_0,transparent_43%),linear-gradient(120deg,#18261c,#101a18_75%)] p-6 sm:p-8">
        <div className="mb-6 flex items-center gap-3 text-[#b7f36b]">
          <span className="grid h-11 w-11 place-items-center rounded-xl bg-[#1b2a20]"><ArrowLeftRight size={22} /></span>
          <div>
            <h2 className="text-lg font-semibold text-[#eef6eb]">Exchange rate calculator</h2>
            <p className="text-xs text-[#a8b8ab]">Use the published rate to estimate your transfer.</p>
          </div>
        </div>

        {ratesQuery.isPending ? (
          <div className="rounded-xl border border-[#344638] p-5 text-sm text-[#a8b8ab]" role="status">Loading published rates…</div>
        ) : ratesQuery.isError ? (
          <div className="rounded-xl border border-red-900/60 bg-red-950/20 p-5 text-sm text-red-200" role="alert">
            Exchange rates are temporarily unavailable. Please try again shortly.
            <button type="button" className="ml-3 underline" onClick={() => void ratesQuery.refetch()}>Try again</button>
          </div>
        ) : rates.length === 0 ? (
          <div className="rounded-xl border border-[#344638] bg-[#101a14]/70 p-5">
            <strong className="text-[#eef6eb]">Rates are being prepared</strong>
            <p className="mt-2 text-sm leading-6 text-[#a8b8ab]">
              RackPay has not published an exchange rate yet. We will display a rate here once a corridor is configured. We will never substitute an invented rate.
            </p>
          </div>
        ) : selectedRate ? (
          <div className="grid gap-5 lg:grid-cols-[1fr_1fr]">
            <div className="space-y-4">
              <label className="block text-xs font-semibold text-[#b8c4ca]" htmlFor="rate-pair">Currency pair</label>
              <select id="rate-pair" className="form-control" value={pairKey} onChange={(event) => setSelectedPair(event.target.value)}>
                {rates.map((item) => {
                  const key = `${item.sourceCurrency}_${item.destinationCurrency}`;
                  return <option key={key} value={key}>{item.sourceCurrency} → {item.destinationCurrency}</option>;
                })}
              </select>
              <label className="block text-xs font-semibold text-[#b8c4ca]" htmlFor="send-amount">You send ({selectedRate.sourceCurrency})</label>
              <input id="send-amount" className="form-control" type="number" min="0.01" step="0.01" inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} />
              <p className="text-xs text-[#8b9aa5]">{currencyNames[selectedRate.sourceCurrency] ?? selectedRate.sourceCurrency} to {currencyNames[selectedRate.destinationCurrency] ?? selectedRate.destinationCurrency}</p>
            </div>
            <div className="flex flex-col justify-center rounded-xl border border-[#354638] bg-[#101a14]/75 p-5">
              <p className="text-xs text-[#a8b8ab]">Published indicative rate</p>
              <p className="mt-2 text-2xl font-semibold tracking-tight text-[#b7f36b]">
                1 {selectedRate.sourceCurrency} = {Number(selectedRate.rate).toLocaleString(undefined, { maximumFractionDigits: 6 })} {selectedRate.destinationCurrency}
              </p>
              <div className="my-5 h-px bg-[#2a3a2d]" />
              <p className="text-xs text-[#a8b8ab]">Estimated recipient amount, before fees</p>
              <p className="mt-2 break-words text-3xl font-semibold text-[#eef6eb]">
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
        <article className="rounded-xl border border-[#24313b] bg-[#101820] p-5">
          <h3 className="font-semibold text-[#eaf0f2]">Clear before you confirm</h3>
          <p className="mt-2 text-sm leading-6 text-[#8b9aa5]">Your final quote shows the applicable transfer fee, the confirmed exchange rate and the amount your recipient will receive before you fund the transfer.</p>
        </article>
        <article className="rounded-xl border border-[#24313b] bg-[#101820] p-5">
          <h3 className="font-semibold text-[#eaf0f2]">Rates are not a promise until quoted</h3>
          <p className="mt-2 text-sm leading-6 text-[#8b9aa5]">Published rates are indicative. The backend-issued transfer quote is authoritative and expires at the time shown during checkout.</p>
        </article>
      </div>
      <p className="compliance-note"><ShieldCheck size={14} /> Exchange rates and transfer fees are supplied by RackPay's backend. The calculator is an estimate and does not include fees.</p>
      <div className="flex items-center gap-2 text-xs text-[#81909a]"><RefreshCw size={13} /> {ratesQuery.isFetching ? "Checking published rates…" : "Published rates are checked periodically."}</div>
    </section>
  );
}
