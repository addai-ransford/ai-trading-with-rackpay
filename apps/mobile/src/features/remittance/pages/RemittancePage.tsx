import { useMutation, useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { CheckCircle2, History, Send } from "lucide-react";
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
import { ApiError } from "../../../shared/api/httpClient";
import { useAuthStore } from "../../../shared/auth/authStore";

type Step = "country" | "recipient" | "amount";

export function RemittancePage() {
  const accessToken = useAuthStore((state) => state.accessToken);
  const [country, setCountry] = useState<RemittanceCountry>();
  const [sourceCountryCode, setSourceCountryCode] = useState("");
  const [network, setNetwork] = useState<MobileMoneyNetwork>();
  const [phoneNumber, setPhoneNumber] = useState("");
  const [verificationFailure, setVerificationFailure] = useState<string>();
  const [recipientName, setRecipientName] = useState<string>();
  const [recipientId, setRecipientId] = useState<string>();
  const [amount, setAmount] = useState("");
  const [step, setStep] = useState<Step>("country");
  const [confirmedRecipient, setConfirmedRecipient] = useState(false);
  const [fundedRemittanceId, setFundedRemittanceId] = useState<string>();
  const [quoteNow, setQuoteNow] = useState(() => Date.now());
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

  const networksQuery = useQuery({
    queryKey: ["remittance", "networks", country?.code],
    queryFn: () => listMobileMoneyNetworks(accessToken!, country!.code),
    enabled: Boolean(accessToken && country),
    staleTime: 5 * 60_000,
  });

  const verifyMutation = useMutation({
    mutationFn: () => {
      if (!accessToken || !country || !network) {
        throw new Error("Recipient details are incomplete.");
      }

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
        setVerificationFailure(
          result.failureReason ?? "The recipient could not be verified.",
        );
        return;
      }
      setVerificationFailure(undefined);
      setRecipientName(result.verifiedName);
      setRecipientId(result.recipientId);
      setConfirmedRecipient(false);
      if (result.verifiedName) setStep("recipient");
    },
  });

  const payoutMutation = useMutation({
    mutationFn: () => {
      if (!accessToken || !fundedRemittanceId)
        throw new Error("Remittance is not funded.");
      return triggerPayout(accessToken, fundedRemittanceId);
    },
  });

  const quoteMutation = useMutation({
    mutationFn: () => {
      const sourceCountry = sendCountriesQuery.data?.find(
        (item) => item.code === sourceCountryCode,
      );
      if (
        !accessToken ||
        !recipientId ||
        !country ||
        !sourceCountry ||
        !amount.trim()
      ) {
        throw new Error("Quote details are incomplete.");
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
      if (!accessToken || !quote) throw new Error("Quote is required.");
      if (!fundIdempotencyKey) throw new Error("Funding key is not ready.");
      return fundRemittance(accessToken, quote.quoteId, fundIdempotencyKey);
    },
    onSuccess: (result) => setFundedRemittanceId(result.remittanceId),
  });

  useEffect(() => {
    if (!quote) return;
    const timer = window.setInterval(() => setQuoteNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [quote]);

  const quoteExpired = useMemo(
    () => (quote ? Date.parse(quote.expiresAt) <= quoteNow : false),
    [quote, quoteNow],
  );
  const numericAmount = Number(amount);
  const amountIsValid = amount.trim() !== "" && Number.isFinite(numericAmount) && numericAmount > 0;

  const phoneDigits = phoneNumber.replace(/\D/g, "");
  const localPhoneDigits = phoneDigits.startsWith("0")
    ? phoneDigits.slice(1)
    : phoneDigits;
  const phoneNumberIsValid =
    country?.dialCode.replace(/\D/g, "") === "233"
      ? localPhoneDigits.length === 9
      : phoneDigits.length >= 6;

  const remittanceQuery = useQuery({
    queryKey: ["remittance", "detail", fundedRemittanceId],
    queryFn: () => getRemittance(accessToken!, fundedRemittanceId!),
    enabled: Boolean(accessToken && fundedRemittanceId),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status &&
        ["COMPLETED", "FAILED", "CANCELLED", "RECOVERY_REQUIRED"].includes(
          status,
        )
        ? false
        : 3000;
    },
  });

  if (!accessToken) {
    return null;
  }

  return (
    <section className="space-y-5">
      <header className="page-heading">
        <div>
          <p className="eyebrow">INTERNATIONAL TRANSFERS</p>
          <h1>Send money</h1>
          <p className="page-description">Verify the recipient, review RackPay’s exchange rate and fees, then confirm your transfer.</p>
        </div>
        <Link to="/remittance/history" className="button button-secondary" aria-label="Remittance history">
          <History size={16} /> History
        </Link>
      </header>

      <div className="remittance-steps flex gap-2">
        {(["country", "recipient", "amount"] as Step[]).map((item, index) => (
          <div key={item} className="flex flex-1 items-center gap-2">
            <div
              className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold ${
                step === item
                  ? "bg-white text-slate-950"
                  : "bg-slate-800 text-slate-400"
              }`}
            >
              {index + 1}
            </div>
            <span className="hidden text-xs capitalize text-slate-500 sm:block">
              {item}
            </span>
          </div>
        ))}
      </div>

      {step === "country" ? (
        <section className="form-panel">
          <h2 className="font-medium">Where are you sending?</h2>
          <p className="mt-1 text-sm text-slate-500">
            Countries are loaded from RackPay configuration.
          </p>

          {countriesQuery.isPending ? (
            <p className="mt-5 text-sm text-slate-500">Loading countries…</p>
          ) : countriesQuery.isError ? (
            <p className="mt-5 text-sm text-red-300">
              We could not load receiving countries.
            </p>
          ) : (
            <div className="mt-4 grid gap-2">
              {countriesQuery.data.map((item) => (
                <button
                  key={item.code}
                  type="button"
                  onClick={() => {
                    setCountry(item);
                    setNetwork(undefined);
                    setPhoneNumber("");
                    setRecipientName(undefined);
                    setRecipientId(undefined);
                    setVerificationFailure(undefined);
                    setConfirmedRecipient(false);
                    setAmount("");
                    quoteMutation.reset();
                    fundMutation.reset();
                    setFundedRemittanceId(undefined);
                    setFundIdempotencyKey(undefined);
                    setStep("recipient");
                  }}
                  className={`flex items-center justify-between rounded-2xl border p-4 text-left ${
                    country?.code === item.code
                      ? "border-white bg-slate-800"
                      : "border-slate-800 bg-slate-950"
                  }`}
                >
                  <span className="flex items-center gap-3">
                    <span aria-hidden="true" className="text-2xl">
                      {countryFlag(item.code)}
                    </span>
                    <span>
                      <span className="block font-medium">{item.name}</span>
                      <span className="text-xs text-slate-500">
                        {item.dialCode} · {item.currency}
                      </span>
                    </span>
                  </span>
                  <span className="text-sm text-slate-400">{item.code}</span>
                </button>
              ))}
            </div>
          )}
        </section>
      ) : null}

      {step === "recipient" && country ? (
        <section className="form-panel">
          <h2 className="font-medium">Recipient</h2>
          <p className="mt-1 text-sm text-slate-500">
            Verify the mobile-money account before you fund the transfer.
          </p>

          <label className="mt-5 block text-sm text-slate-400">
            Network
            <select
              value={network?.code ?? ""}
              onChange={(event) => {
                setNetwork(
                  networksQuery.data?.find(
                    (item) => item.code === event.target.value,
                  ),
                );
                setRecipientName(undefined);
                setRecipientId(undefined);
                setVerificationFailure(undefined);
                setConfirmedRecipient(false);
                setAmount("");
                quoteMutation.reset();
                fundMutation.reset();
                setFundedRemittanceId(undefined);
                setFundIdempotencyKey(undefined);
              }}
              className="form-control"
            >
              <option value="">Select network</option>
              {networksQuery.data?.map((item) => (
                <option key={item.id} value={item.code}>
                  {item.name}
                </option>
              ))}
            </select>
            {networksQuery.isPending ? (
              <span className="mt-2 block text-xs text-slate-500">Loading available networks…</span>
            ) : networksQuery.isError ? (
              <span className="mt-2 block text-xs text-red-300">Networks could not be loaded. Try again in a moment.</span>
            ) : networksQuery.data?.length === 0 ? (
              <span className="mt-2 block text-xs text-amber-300">No enabled mobile-money networks are configured for this country.</span>
            ) : null}
          </label>

          <label className="mt-4 block text-sm text-slate-400">
            Mobile-money phone number
            <div className="mt-2 flex">
              <span className="rounded-l-2xl border border-r-0 border-slate-700 bg-slate-800 px-3 py-3 text-sm text-slate-300">
                {country.dialCode}
              </span>
              <input
                value={phoneNumber}
                onChange={(event) => {
                  setPhoneNumber(event.target.value);
                  setRecipientName(undefined);
                  setRecipientId(undefined);
                  setVerificationFailure(undefined);
                  setConfirmedRecipient(false);
                  setAmount("");
                  quoteMutation.reset();
                  fundMutation.reset();
                  setFundedRemittanceId(undefined);
                  setFundIdempotencyKey(undefined);
                }}
                inputMode="tel"
                autoComplete="tel"
                placeholder="024 123 4567"
                className="min-w-0 flex-1 rounded-r-2xl border border-slate-700 bg-slate-950 px-3 py-3 text-sm text-white outline-none"
              />
            </div>
            {country.dialCode.replace(/\D/g, "") === "233" ? (
              <span className="mt-1 block text-xs text-slate-500">
                Enter 9 digits after +233. A leading 0 is optional.
              </span>
            ) : null}
          </label>

          <button
            type="button"
            disabled={
              !network || !phoneNumberIsValid || verifyMutation.isPending
            }
            onClick={() => verifyMutation.mutate()}
            className="mt-5 w-full rounded-2xl bg-white px-4 py-3 font-semibold text-slate-950 disabled:opacity-40"
          >
            {verifyMutation.isPending ? "Verifying…" : "Verify recipient"}
          </button>

          {verifyMutation.isError || verificationFailure ? (
            <p className="mt-3 text-sm text-red-300">
              {verificationFailure ??
                (verifyMutation.error instanceof Error
                  ? verifyMutation.error.message
                  : "The recipient could not be verified.")}
            </p>
          ) : null}

          {recipientName ? (
            <div className="verified-recipient">
              <div className="flex items-start gap-3">
                <CheckCircle2 className="mt-0.5 shrink-0" size={20} />
                <div>
                  <p className="text-xs uppercase tracking-wide text-slate-500">
                    Verified recipient
                  </p>
                  <p className="mt-1 font-medium">{recipientName}</p>
                  <p className="text-sm text-slate-500">
                    {country.dialCode} {phoneNumber}
                  </p>
                </div>
              </div>

              <label className="mt-4 flex items-start gap-3 text-sm text-slate-300">
                <input
                  type="checkbox"
                  checked={confirmedRecipient}
                  onChange={(event) =>
                    setConfirmedRecipient(event.target.checked)
                  }
                  className="mt-1"
                />
                I confirm this is the recipient I want to send money to.
              </label>

              <button
                type="button"
                disabled={!confirmedRecipient}
                onClick={() => setStep("amount")}
                className="mt-4 w-full rounded-2xl bg-white px-4 py-3 font-semibold text-slate-950 disabled:opacity-40"
              >
                Continue
              </button>
            </div>
          ) : null}
        </section>
      ) : null}

      {step === "amount" && country && recipientId ? (
        <section className="form-panel">
          <h2 className="font-medium">Amount</h2>
          <p className="mt-1 text-sm text-slate-500">
            Enter the amount in your sending currency. RackPay supplies the
            quote and FX rate.
          </p>

          <label className="mt-5 block text-sm text-slate-400">
            Sending country
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
              className="form-control"
            >
              <option value="">
                {sendCountriesQuery.isPending
                  ? "Loading sending countries…"
                  : "Select sending country"}
              </option>
              {sendCountriesQuery.data?.map((item) => (
                <option key={item.code} value={item.code}>
                  {item.name} · {item.currency}
                </option>
              ))}
            </select>
            {sendCountriesQuery.isError ? (
              <span className="mt-2 block text-xs text-red-300">
                Sending countries could not be loaded. Retry by reopening this page.
              </span>
            ) : null}
          </label>

          <label className="mt-4 block text-sm text-slate-400">
            You send
            <div className="mt-2 flex">
              <span className="rounded-l-2xl border border-r-0 border-slate-700 bg-slate-800 px-3 py-3 text-sm text-slate-300">
                {sendCountriesQuery.data?.find((item) => item.code === sourceCountryCode)?.currency ?? "—"}
              </span>
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
                placeholder="100.00"
                className="amount-input min-w-0 flex-1"
              />
            </div>
          </label>

          <button
            type="button"
            disabled={
              !amountIsValid ||
              !sourceCountryCode ||
              !sendCountriesQuery.data?.some((item) => item.code === sourceCountryCode) ||
              quoteMutation.isPending
            }
            onClick={() => quoteMutation.mutate()}
            className="mt-5 w-full rounded-2xl bg-white px-4 py-3 font-semibold text-slate-950 disabled:opacity-40"
          >
            {quoteMutation.isPending ? "Getting quote…" : "Get quote"}
          </button>

          {quoteMutation.isError ? (
            <p role="alert" className="mt-3 text-sm text-red-300">
              {errorMessage(quoteMutation.error, "The quote could not be created. Review the details and try again.")}
            </p>
          ) : null}

          {quote ? (
            <div className="quote-card">
              <div className="flex items-center gap-3">
                <div className="rounded-xl bg-slate-800 p-2">
                  <Send size={18} />
                </div>
                <div>
                  <p className="font-medium">Quote ready</p>
                  <p className="text-xs text-slate-500">
                    Expires {new Date(quote.expiresAt).toLocaleTimeString()}
                  </p>
                </div>
              </div>

              <div className="quote-rate" aria-label="Quoted exchange rate">
                <div>
                  <p className="quote-rate-label">
                    <span aria-hidden="true">↔</span>
                    Exchange rate
                  </p>
                  <p className="quote-rate-note">
                    Rate returned by RackPay for this quote
                  </p>
                </div>
                <p className="quote-rate-value">
                  1 {quote.sourceCurrency} = {quote.fxRate} {quote.destinationCurrency}
                </p>
              </div>

              <div className="mt-4 grid gap-3 text-sm">
                <QuoteRow
                  label="You send"
                  value={`${quote.sourceAmount} ${quote.sourceCurrency}`}
                />
                <QuoteRow
                  label="Fee"
                  value={`${quote.feeAmount} ${quote.sourceCurrency}`}
                />
                <QuoteRow
                  label="Recipient gets"
                  value={`${quote.destinationAmount} ${quote.destinationCurrency}`}
                />
              </div>

              {quoteExpired ? (
                <p className="mt-4 text-sm text-amber-300">
                  This quote has expired. Get a new quote before funding.
                </p>
              ) : fundedRemittanceId ? (
                <div className="mt-4 rounded-2xl border border-slate-700 bg-slate-900 p-4">
                  <p className="text-xs uppercase tracking-wide text-slate-500">
                    Remittance
                  </p>
                  <p className="mt-1 font-medium">
                    {remittanceQuery.data?.status ?? "FUNDS_RESERVED"}
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    The backend is the source of truth for the transfer state.
                  </p>
                  {![
                    "PAYOUT_PENDING",
                    "PAYOUT_PROCESSING",
                    "COMPLETED",
                  ].includes(remittanceQuery.data?.status ?? "") ? (
                    <button
                      type="button"
                      disabled={
                        payoutMutation.isPending || !remittanceQuery.data
                      }
                      onClick={() => payoutMutation.mutate()}
                      className="mt-4 w-full rounded-2xl bg-white px-4 py-3 font-semibold text-slate-950 disabled:opacity-40"
                    >
                      {payoutMutation.isPending
                        ? "Starting payout…"
                        : "Start payout"}
                    </button>
                  ) : null}
                  {payoutMutation.isError ? (
                    <p role="alert" className="mt-3 text-sm text-red-300">
                      {errorMessage(payoutMutation.error, "The payout request could not be started.")}
                    </p>
                  ) : null}
                </div>
              ) : (
                <>
                  <p className="mt-4 text-xs leading-5 text-slate-500">
                    Funding and payout are handled by the backend. The app does
                    not choose a payout provider or determine final remittance
                    state.
                  </p>
                  <button
                    type="button"
                    disabled={fundMutation.isPending || !fundIdempotencyKey || quoteExpired}
                    onClick={() => fundMutation.mutate()}
                    className="mt-4 w-full rounded-2xl bg-white px-4 py-3 font-semibold text-slate-950 disabled:opacity-40"
                  >
                    {fundMutation.isPending ? "Funding…" : "Fund remittance"}
                  </button>
                  {fundMutation.isError ? (
                    <p role="alert" className="mt-3 text-sm text-red-300">
                      {errorMessage(fundMutation.error, "The remittance could not be funded. Your wallet was not assumed to be debited by the app.")}
                    </p>
                  ) : null}
                </>
              )}
            </div>
          ) : null}
        </section>
      ) : null}
    </section>
  );
}

function QuoteRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <span className="text-slate-500">{label}</span>
      <span className="text-right font-medium">{value}</span>
    </div>
  );
}

function countryFlag(countryCode: string) {
  const normalized = countryCode.trim().toUpperCase();
  if (!/^[A-Z]{2}$/.test(normalized)) return "🌐";
  return String.fromCodePoint(
    ...[...normalized].map((character) => 127397 + character.charCodeAt(0)),
  );
}

function errorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    return error.correlationId
      ? `${error.message} (Reference: ${error.correlationId})`
      : error.message;
  }
  return error instanceof Error ? error.message : fallback;
}
