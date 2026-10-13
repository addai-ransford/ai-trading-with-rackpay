import { useMutation, useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import {
  ArrowDownUp,
  ArrowRight,
  CheckCircle2,
  Clock3,
  History,
  Send,
  ShieldCheck,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import {
  createQuote,
  fundRemittance,
  getRemittance,
  listMobileMoneyNetworks,
  listRemittanceCountries,
  triggerPayout,
  verifyRecipient,
  type MobileMoneyNetwork,
  type RemittanceCountry,
} from "../api/remittanceApi";
import { ApiError, apiFetch } from "../../../shared/api/httpClient";
import { countryFlag, isQuoteExpired, isValidAmount, isValidRecipientPhone } from "../utils/remittanceUtils";
import { useAuthStore } from "../../../shared/auth/authStore";

type ExchangeRate = {
  sourceCurrency: string;
  destinationCurrency: string;
  rate: string | number;
  rateType: string;
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

export function RemittancePage() {
  const accessToken = useAuthStore((state) => state.accessToken);
  const [country, setCountry] = useState<RemittanceCountry>();
  const [sourceCountryCode, setSourceCountryCode] = useState("BE");
  const [network, setNetwork] = useState<MobileMoneyNetwork>();
  const [phoneNumber, setPhoneNumber] = useState("");
  const [verificationFailure, setVerificationFailure] = useState<string>();
  const [recipientName, setRecipientName] = useState<string>();
  const [recipientId, setRecipientId] = useState<string>();
  const [confirmedRecipient, setConfirmedRecipient] = useState(false);
  const [amount, setAmount] = useState("100");
  const [fundedRemittanceId, setFundedRemittanceId] = useState<string>();
  const [quoteNow, setQuoteNow] = useState(Date.now());
  const [fundIdempotencyKey, setFundIdempotencyKey] = useState<string>();

  const countriesQuery = useQuery({
    queryKey: ["remittance", "countries", "RECEIVE"],
    queryFn: () => listRemittanceCountries(accessToken!),
    enabled: Boolean(accessToken),
    staleTime: 5 * 60_000,
  });
  const sendCountriesQuery = useQuery({
    queryKey: ["remittance", "countries", "SEND"],
    queryFn: () => listRemittanceCountries(accessToken!, "SEND"),
    enabled: Boolean(accessToken),
    staleTime: 5 * 60_000,
  });
  const ratesQuery = useQuery({
    queryKey: ["public", "exchange-rates"],
    queryFn: () => apiFetch<ExchangeRate[]>("/api/v1/public/exchange-rates"),
    staleTime: 60_000,
    refetchInterval: 60_000,
  });

  useEffect(() => {
    if (!country && countriesQuery.data?.length) {
      setCountry(
        countriesQuery.data.find((item) => item.code === "GH") ??
        countriesQuery.data[0],
      );
    }
  }, [country, countriesQuery.data]);

  useEffect(() => {
    if (sendCountriesQuery.data?.length && !sendCountriesQuery.data.some((item) => item.code === sourceCountryCode)) {
      setSourceCountryCode(
        sendCountriesQuery.data.find((item) => item.code === "BE")?.code ??
        sendCountriesQuery.data[0].code,
      );
    }
  }, [sendCountriesQuery.data, sourceCountryCode]);

  const sourceCountry = sendCountriesQuery.data?.find((item) => item.code === sourceCountryCode);
  const networksQuery = useQuery({
    queryKey: ["remittance", "networks", country?.code],
    queryFn: () => listMobileMoneyNetworks(accessToken!, country!.code),
    enabled: Boolean(accessToken && country),
    staleTime: 5 * 60_000,
  });

  const selectedRate = useMemo(() => {
    if (!sourceCountry || !country) return undefined;
    return ratesQuery.data?.find(
      (item) => item.sourceCurrency === sourceCountry.currency &&
        item.destinationCurrency === country.currency,
    );
  }, [country, ratesQuery.data, sourceCountry]);

  const indicativeDestination = useMemo(() => {
    const value = Number(amount);
    if (!selectedRate || !Number.isFinite(value) || value <= 0) return undefined;
    return value * Number(selectedRate.rate);
  }, [amount, selectedRate]);

  const clearTransfer = () => {
    setRecipientName(undefined);
    setRecipientId(undefined);
    setVerificationFailure(undefined);
    setConfirmedRecipient(false);
    setNetwork(undefined);
    setPhoneNumber("");
    quoteMutation.reset();
    fundMutation.reset();
    setFundedRemittanceId(undefined);
    setFundIdempotencyKey(undefined);
  };

  const verifyMutation = useMutation({
    mutationFn: () => {
      if (!accessToken || !country || !network) throw new Error("Choose a destination network and enter the recipient's number.");
      return verifyRecipient(accessToken, {
        countryCode: country.code,
        countryDialCode: country.dialCode,
        currency: country.currency,
        payoutMethod: "MOBILE_MONEY",
        networkCode: network.code,
        phoneNumber,
      });
    },
    onSuccess: (result) => {
      if (!result.verified) {
        setRecipientName(undefined);
        setRecipientId(undefined);
        setConfirmedRecipient(false);
        setVerificationFailure(result.failureReason ?? "The recipient could not be verified.");
        return;
      }
      setVerificationFailure(undefined);
      setRecipientName(result.verifiedName);
      setRecipientId(result.recipientId);
      setConfirmedRecipient(false);
    },
  });

  const quoteMutation = useMutation({
    mutationFn: () => {
      if (!accessToken || !recipientId || !country || !sourceCountry || !amount.trim()) {
        throw new Error("Verify the recipient and complete the transfer details first.");
      }
      return createQuote(accessToken, {
        recipientId,
        sourceCountryCode: sourceCountry.code,
        sourceCurrency: sourceCountry.currency,
        destinationCurrency: country.currency,
        sourceAmount: amount.trim(),
      });
    },
    onSuccess: () => {
      setFundIdempotencyKey(crypto.randomUUID());
      setFundedRemittanceId(undefined);
    },
  });
  const quote = quoteMutation.data;

  const fundMutation = useMutation({
    mutationFn: () => {
      if (!accessToken || !quote) throw new Error("A valid quote is required.");
      if (!fundIdempotencyKey) throw new Error("The funding request key is not ready.");
      return fundRemittance(accessToken, quote.quoteId, fundIdempotencyKey);
    },
    onSuccess: (result) => setFundedRemittanceId(result.remittanceId),
  });

  const payoutMutation = useMutation({
    mutationFn: () => {
      if (!accessToken || !fundedRemittanceId) throw new Error("Fund the remittance before starting payout.");
      return triggerPayout(accessToken, fundedRemittanceId);
    },
  });

  useEffect(() => {
    if (!quote) return;
    const timer = window.setInterval(() => setQuoteNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [quote]);

  const quoteExpired = useMemo(
    () => (quote ? isQuoteExpired(quote.expiresAt, quoteNow) : false),
    [quote, quoteNow],
  );
  const amountIsValid = isValidAmount(amount);
  const phoneNumberIsValid = isValidRecipientPhone(phoneNumber, country?.dialCode ?? "");

  const remittanceQuery = useQuery({
    queryKey: ["remittance", "detail", fundedRemittanceId],
    queryFn: () => getRemittance(accessToken!, fundedRemittanceId!),
    enabled: Boolean(accessToken && fundedRemittanceId),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status && ["COMPLETED", "FAILED", "CANCELLED", "RECOVERY_REQUIRED"].includes(status)
        ? false
        : 3000;
    },
  });

  if (!accessToken) return null;

  return (
    <section className="space-y-6 pb-8">
      <header className="page-heading">
        <div>
          <p className="eyebrow">RACKPAY TRANSFERS</p>
          <h1>Send money internationally</h1>
          <p className="page-description">Choose a destination, see the indicative rate, verify your recipient and review the final quote — all in one place.</p>
        </div>
        <Link to="/remittance/history" className="button button-secondary"><History size={16} /> History</Link>
      </header>

      <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1.25fr)_minmax(320px,0.75fr)]">
        <div className="space-y-5">
          <section className="overflow-hidden rounded-3xl border border-slate-800 bg-slate-900/80 shadow-xl shadow-black/10">
            <div className="border-b border-slate-800 bg-gradient-to-r from-emerald-950/60 via-slate-900 to-slate-900 p-5 sm:p-6">
              <div className="flex items-center gap-3">
                <span className="grid h-11 w-11 place-items-center rounded-2xl border border-emerald-800/60 bg-emerald-950/70 text-emerald-300"><Send size={20} /></span>
                <div>
                  <h2 className="font-semibold text-white">Transfer details</h2>
                  <p className="mt-1 text-sm text-slate-400">Your route determines the currencies and indicative rate.</p>
                </div>
              </div>
              <div className="mt-5 grid gap-3 sm:grid-cols-2">
                <label className="block text-xs font-semibold uppercase tracking-wide text-slate-400">
                  You send from
                  <select
                    value={sourceCountryCode}
                    onChange={(event) => {
                      setSourceCountryCode(event.target.value);
                      quoteMutation.reset();
                      fundMutation.reset();
                      setFundedRemittanceId(undefined);
                      setFundIdempotencyKey(undefined);
                    }}
                    disabled={sendCountriesQuery.isPending || sendCountriesQuery.isError}
                    className="form-control mt-2 normal-case tracking-normal"
                  >
                    <option value="">{sendCountriesQuery.isPending ? "Loading countries…" : "Choose sending country"}</option>
                    {sendCountriesQuery.data?.map((item) => (
                      <option key={item.code} value={item.code}>{countryFlag(item.code)} {item.name} · {item.currency}</option>
                    ))}
                  </select>
                </label>
                <label className="block text-xs font-semibold uppercase tracking-wide text-slate-400">
                  Recipient country
                  <select
                    value={country?.code ?? ""}
                    onChange={(event) => {
                      const nextCountry = countriesQuery.data?.find((item) => item.code === event.target.value);
                      if (!nextCountry) return;
                      setCountry(nextCountry);
                      clearTransfer();
                    }}
                    disabled={countriesQuery.isPending || countriesQuery.isError}
                    className="form-control mt-2 normal-case tracking-normal"
                  >
                    <option value="">{countriesQuery.isPending ? "Loading destinations…" : "Choose recipient country"}</option>
                    {countriesQuery.data?.map((item) => (
                      <option key={item.code} value={item.code}>{countryFlag(item.code)} {item.name} · {item.currency}</option>
                    ))}
                  </select>
                </label>
              </div>
              {countriesQuery.isError || sendCountriesQuery.isError ? (
                <p role="alert" className="mt-3 text-sm text-red-300">Country options could not be loaded. Please refresh and try again.</p>
              ) : null}
            </div>

            <div className="space-y-5 p-5 sm:p-6">
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="block text-sm text-slate-300">
                  Mobile-money network
                  <select
                    value={network?.code ?? ""}
                    onChange={(event) => {
                      setNetwork(networksQuery.data?.find((item) => item.code === event.target.value));
                      setRecipientName(undefined);
                      setRecipientId(undefined);
                      setVerificationFailure(undefined);
                      setConfirmedRecipient(false);
                      quoteMutation.reset();
                      fundMutation.reset();
                      setFundedRemittanceId(undefined);
                      setFundIdempotencyKey(undefined);
                    }}
                    className="form-control mt-2"
                    disabled={!country || networksQuery.isPending || networksQuery.isError}
                  >
                    <option value="">{networksQuery.isPending ? "Loading networks…" : "Select network"}</option>
                    {networksQuery.data?.map((item) => <option key={item.id} value={item.code}>{item.name}</option>)}
                  </select>
                  {networksQuery.isError ? <span className="mt-2 block text-xs text-red-300">Could not load networks. Try again shortly.</span> : null}
                  {!networksQuery.isPending && !networksQuery.isError && networksQuery.data?.length === 0 ? <span className="mt-2 block text-xs text-amber-300">No mobile-money networks are enabled for this destination yet.</span> : null}
                </label>
                <label className="block text-sm text-slate-300">
                  Recipient mobile number
                  <div className="mt-2 flex">
                    <span className="flex items-center rounded-l-xl border border-r-0 border-slate-700 bg-slate-800 px-3 text-sm text-slate-300">{country?.dialCode ?? "—"}</span>
                    <input
                      value={phoneNumber}
                      onChange={(event) => {
                        setPhoneNumber(event.target.value);
                        setRecipientName(undefined);
                        setRecipientId(undefined);
                        setVerificationFailure(undefined);
                        setConfirmedRecipient(false);
                        quoteMutation.reset();
                        fundMutation.reset();
                        setFundedRemittanceId(undefined);
                        setFundIdempotencyKey(undefined);
                      }}
                      inputMode="tel"
                      autoComplete="tel"
                      placeholder={country?.code === "GH" ? "024 123 4567" : "Recipient phone number"}
                      className="form-control min-w-0 rounded-l-none"
                    />
                  </div>
                  {country?.dialCode.replace(/\D/g, "") === "233" ? <span className="mt-1 block text-xs text-slate-500">Enter 9 digits after +233. A leading 0 is optional.</span> : null}
                </label>
              </div>

              {recipientName && recipientId ? (
                <div className="rounded-2xl border border-emerald-800/70 bg-emerald-950/30 p-4">
                  <div className="flex items-start gap-3">
                    <CheckCircle2 className="mt-0.5 shrink-0 text-emerald-400" size={20} />
                    <div className="min-w-0 flex-1">
                      <p className="text-xs font-semibold uppercase tracking-wide text-emerald-400">Recipient verified</p>
                      <p className="mt-1 font-semibold text-white">{recipientName}</p>
                      <p className="mt-1 text-sm text-slate-400">{country?.dialCode} {phoneNumber}</p>
                    </div>
                  </div>
                  <label className="mt-4 flex items-start gap-3 text-sm text-slate-300">
                    <input type="checkbox" checked={confirmedRecipient} onChange={(event) => setConfirmedRecipient(event.target.checked)} className="mt-1 accent-emerald-400" />
                    I confirm this is the person I want to send money to.
                  </label>
                </div>
              ) : null}

              {verifyMutation.isError || verificationFailure ? (
                <p role="alert" className="rounded-xl border border-red-900/50 bg-red-950/30 p-3 text-sm text-red-300">
                  {verificationFailure ?? errorMessage(verifyMutation.error, "The recipient could not be verified.")}
                </p>
              ) : null}

              <div className="flex flex-col gap-3 sm:flex-row">
                <button
                  type="button"
                  disabled={!country || !network || !phoneNumberIsValid || verifyMutation.isPending}
                  onClick={() => verifyMutation.mutate()}
                  className="button button-secondary flex-1 justify-center disabled:cursor-not-allowed disabled:opacity-40"
                >
                  {verifyMutation.isPending ? "Verifying recipient…" : recipientId ? "Verify again" : "Verify recipient"}
                </button>
                <div className="flex flex-1 items-center gap-2 rounded-xl border border-slate-800 bg-slate-950/70 px-3 py-2 text-xs leading-5 text-slate-500">
                  <ShieldCheck className="shrink-0 text-emerald-400" size={17} />
                  Recipient details are checked before a transfer quote can be requested.
                </div>
              </div>
            </div>
          </section>

          <section className="rounded-3xl border border-slate-800 bg-slate-900/80 p-5 sm:p-6">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="eyebrow">TRANSFER AMOUNT</p>
                <h2 className="mt-1 text-lg font-semibold text-white">How much would you like to send?</h2>
              </div>
              <span className="rounded-xl border border-slate-700 bg-slate-800 p-2 text-slate-300"><ArrowDownUp size={18} /></span>
            </div>
            <label className="mt-5 block text-sm text-slate-300">
              Amount to send
              <div className="mt-2 flex">
                <span className="flex items-center rounded-l-xl border border-r-0 border-slate-700 bg-slate-800 px-4 font-semibold text-white">{sourceCountry?.currency ?? "—"}</span>
                <input
                  value={amount}
                  onChange={(event) => {
                    setAmount(event.target.value);
                    quoteMutation.reset();
                    fundMutation.reset();
                    setFundedRemittanceId(undefined);
                    setFundIdempotencyKey(undefined);
                  }}
                  inputMode="decimal"
                  type="number"
                  min="0.01"
                  step="0.01"
                  placeholder="100.00"
                  className="form-control min-w-0 rounded-l-none text-lg"
                />
              </div>
            </label>
            <div className="mt-4 flex flex-col gap-3 sm:flex-row">
              <button
                type="button"
                disabled={!recipientId || !confirmedRecipient || !sourceCountry || !amountIsValid || quoteMutation.isPending}
                onClick={() => quoteMutation.mutate()}
                className="button button-primary flex-1 justify-center disabled:cursor-not-allowed disabled:opacity-40"
              >
                {quoteMutation.isPending ? "Preparing quote…" : "Get transfer quote"} <ArrowRight size={16} />
              </button>
              {!confirmedRecipient ? <p className="self-center text-xs text-slate-500">Verify and confirm the recipient to continue.</p> : null}
            </div>
            {quoteMutation.isError ? <p role="alert" className="mt-3 text-sm text-red-300">{errorMessage(quoteMutation.error, "The quote could not be created. Check the details and try again.")}</p> : null}
          </section>

          {quote ? (
            <section className="rounded-3xl border border-emerald-800/60 bg-slate-900 p-5 sm:p-6">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="eyebrow">YOUR TRANSFER QUOTE</p>
                  <h2 className="mt-1 text-lg font-semibold text-white">Review before funding</h2>
                </div>
                <span className="flex items-center gap-1.5 rounded-full border border-slate-700 px-2.5 py-1 text-xs text-slate-300"><Clock3 size={13} /> Expires {new Date(quote.expiresAt).toLocaleTimeString()}</span>
              </div>
              <div className="mt-5 rounded-2xl bg-emerald-950/40 p-4">
                <p className="text-sm text-emerald-200">Recipient gets</p>
                <p className="mt-1 text-3xl font-semibold tracking-tight text-white">{money(Number(quote.destinationAmount), quote.destinationCurrency)}</p>
                <p className="mt-2 text-sm text-emerald-100/70">1 {quote.sourceCurrency} = {Number(quote.fxRate).toLocaleString(undefined, { maximumFractionDigits: 6 })} {quote.destinationCurrency}</p>
              </div>
              <div className="mt-4 divide-y divide-slate-800 text-sm">
                <QuoteRow label="You send" value={money(Number(quote.sourceAmount), quote.sourceCurrency)} />
                <QuoteRow label="Transfer fee" value={money(Number(quote.feeAmount), quote.sourceCurrency)} />
                <QuoteRow label="Recipient receives" value={money(Number(quote.destinationAmount), quote.destinationCurrency)} />
              </div>
              {quoteExpired ? <p className="mt-4 text-sm text-amber-300">This quote has expired. Request a new quote before funding.</p> : null}
              {fundedRemittanceId ? (
                <div className="mt-5 rounded-2xl border border-slate-700 bg-slate-950/60 p-4">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Transfer status</p>
                  <p className="mt-1 font-semibold text-white">{remittanceQuery.data?.status ?? "FUNDS_RESERVED"}</p>
                  <p className="mt-1 break-all text-xs text-slate-500">Reference: {fundedRemittanceId}</p>
                  {!["PAYOUT_PENDING", "PAYOUT_PROCESSING", "COMPLETED"].includes(remittanceQuery.data?.status ?? "") ? (
                    <button type="button" disabled={payoutMutation.isPending || !remittanceQuery.data} onClick={() => payoutMutation.mutate()} className="button button-primary mt-4 w-full justify-center disabled:opacity-40">
                      {payoutMutation.isPending ? "Starting payout…" : "Start payout"}
                    </button>
                  ) : null}
                  {payoutMutation.isError ? <p role="alert" className="mt-3 text-sm text-red-300">{errorMessage(payoutMutation.error, "Payout could not be started.")}</p> : null}
                </div>
              ) : (
                <>
                  <p className="mt-4 text-xs leading-5 text-slate-500">The final quote is issued by RackPay's backend and expires at the time shown. Funding and payout status are confirmed by the backend.</p>
                  <button type="button" disabled={fundMutation.isPending || !fundIdempotencyKey || quoteExpired} onClick={() => fundMutation.mutate()} className="button button-primary mt-4 w-full justify-center disabled:opacity-40">
                    {fundMutation.isPending ? "Funding transfer…" : "Confirm and fund transfer"}
                  </button>
                  {fundMutation.isError ? <p role="alert" className="mt-3 text-sm text-red-300">{errorMessage(fundMutation.error, "The transfer could not be funded.")}</p> : null}
                </>
              )}
            </section>
          ) : null}
        </div>

        <aside className="space-y-5 xl:sticky xl:top-6">
          <section className="overflow-hidden rounded-3xl border border-emerald-900/60 bg-[radial-gradient(ellipse_at_100%_0%,#1c4933_0,transparent_48%),linear-gradient(145deg,#12251b,#0f1720_75%)] p-5 sm:p-6">
            <div className="flex items-center gap-3">
              <span className="grid h-10 w-10 place-items-center rounded-xl border border-emerald-800 bg-emerald-950/70 text-emerald-300"><ArrowDownUp size={19} /></span>
              <div>
                <p className="text-xs font-semibold uppercase tracking-wider text-emerald-300">Live indicative rate</p>
                <p className="mt-1 text-sm text-slate-300">{sourceCountry?.name ?? "Belgium"} → {country?.name ?? "Ghana"}</p>
              </div>
            </div>
            {ratesQuery.isPending ? <p className="mt-6 text-sm text-slate-400">Loading latest rate…</p> : ratesQuery.isError ? (
              <div className="mt-5 text-sm text-amber-200">The indicative rate is temporarily unavailable. You can still request a backend quote.</div>
            ) : selectedRate ? (
              <>
                <p className="mt-6 text-sm text-slate-400">1 {sourceCountry?.currency} equals</p>
                <p className="mt-1 break-words text-3xl font-semibold tracking-tight text-white">{Number(selectedRate.rate).toLocaleString(undefined, { maximumFractionDigits: 6 })} <span className="text-xl text-emerald-300">{country?.currency}</span></p>
                <div className="my-5 h-px bg-emerald-900/70" />
                <p className="text-sm text-slate-400">Estimated recipient amount</p>
                <p className="mt-1 text-2xl font-semibold text-emerald-300">{indicativeDestination === undefined ? "—" : money(indicativeDestination, country?.currency ?? "")}</p>
                <p className="mt-2 text-xs leading-5 text-slate-500">Based on {amount || "0"} {sourceCountry?.currency}. Indicative estimate before fees.</p>
                <span className="mt-4 inline-flex items-center gap-2 rounded-full border border-emerald-900 bg-emerald-950/50 px-3 py-1.5 text-xs text-emerald-200"><span className="h-1.5 w-1.5 rounded-full bg-emerald-400" /> Published rate · indicative</span>
              </>
            ) : (
              <div className="mt-5 rounded-xl border border-slate-700/70 bg-black/10 p-4">
                <p className="font-medium text-white">Rate not available yet</p>
                <p className="mt-1 text-sm leading-5 text-slate-400">No published rate is available for this currency pair. RackPay will not guess a rate; request a transfer quote when ready.</p>
              </div>
            )}
            {ratesQuery.isError ? <button type="button" className="mt-3 text-sm text-emerald-300 underline" onClick={() => void ratesQuery.refetch()}>Try loading again</button> : null}
          </section>

          <section className="rounded-3xl border border-slate-800 bg-slate-900/70 p-5">
            <h3 className="font-semibold text-white">Your route</h3>
            <div className="mt-4 flex items-center gap-3">
              <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-slate-800 text-2xl">{sourceCountry ? countryFlag(sourceCountry.code) : "🇧🇪"}</span>
              <div className="min-w-0 flex-1">
                <p className="text-xs text-slate-500">Sending from</p>
                <p className="truncate font-medium text-white">{sourceCountry?.name ?? "Belgium"}</p>
                <p className="text-xs text-slate-400">{sourceCountry?.currency ?? "EUR"}</p>
              </div>
            </div>
            <div className="ml-5 my-1 h-6 border-l border-dashed border-slate-700" />
            <div className="flex items-center gap-3">
              <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-slate-800 text-2xl">{country ? countryFlag(country.code) : "🇬🇭"}</span>
              <div className="min-w-0 flex-1">
                <p className="text-xs text-slate-500">Receiving in</p>
                <p className="truncate font-medium text-white">{country?.name ?? "Ghana"}</p>
                <p className="text-xs text-slate-400">{country?.currency ?? "GHS"}</p>
              </div>
            </div>
          </section>

          <div className="flex gap-3 rounded-2xl border border-slate-800 bg-slate-900/50 p-4 text-sm leading-5 text-slate-400">
            <ShieldCheck size={18} className="mt-0.5 shrink-0 text-emerald-400" />
            The displayed rate is an estimate. Your final rate, fee and recipient amount are confirmed in the transfer quote before you fund.
          </div>
          {ratesQuery.isFetching ? <p className="text-xs text-slate-500">Refreshing published rates…</p> : null}
        </aside>
      </div>
    </section>
  );
}

function QuoteRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 py-3">
      <span className="text-slate-500">{label}</span>
      <span className="text-right font-medium text-slate-100">{value}</span>
    </div>
  );
}

function errorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    return error.correlationId ? `${error.message} (Reference: ${error.correlationId})` : error.message;
  }
  return error instanceof Error ? error.message : fallback;
}
