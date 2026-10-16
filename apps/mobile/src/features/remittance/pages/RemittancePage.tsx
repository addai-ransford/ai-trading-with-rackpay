import { useMutation, useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import {
  ArrowRight,
  CheckCircle2,
  Clock3,
  History,
  Send,
  ShieldCheck,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import {
  addManualRecipient,
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
import { apiFetch } from "../../../shared/api/httpClient";
import { countryFlag, isQuoteExpired, isValidAmount, isValidRecipientPhone } from "../utils/remittanceUtils";
import { useAuthStore } from "../../../shared/auth/authStore";

type ExchangeRate = {
  sourceCurrency: string;
  destinationCurrency: string;
  rate: string | number;
  rateType: string;
};

type Step = "amount" | "recipient" | "review";

function money(value: number, currency: string): string {
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
  const [activeStep, setActiveStep] = useState<Step>("amount");
  const [selectedCountry, setSelectedCountry] = useState<RemittanceCountry>();
  const [selectedSourceCountryCode, setSelectedSourceCountryCode] = useState("BE");
  const [network, setNetwork] = useState<MobileMoneyNetwork>();
  const [phoneNumber, setPhoneNumber] = useState("");
  const [amount, setAmount] = useState("100");
  const [verificationFailure, setVerificationFailure] = useState<string>();
  const [recipientName, setRecipientName] = useState<string>();
  const [recipientId, setRecipientId] = useState<string>();
  const [verificationProvider, setVerificationProvider] = useState<string>();
  const [recipientIsVerified, setRecipientIsVerified] = useState(false);
  const [manualRecipientName, setManualRecipientName] = useState("");
  const [confirmedRecipient, setConfirmedRecipient] = useState(false);
  const [fundedRemittanceId, setFundedRemittanceId] = useState<string>();
  const [quoteNow, setQuoteNow] = useState(0);
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

  const country = selectedCountry ?? countriesQuery.data?.find((item) => item.code === "GH") ?? countriesQuery.data?.[0];
  const sourceCountryCode = sendCountriesQuery.data?.some((item) => item.code === selectedSourceCountryCode)
    ? selectedSourceCountryCode
    : sendCountriesQuery.data?.find((item) => item.code === "BE")?.code ?? sendCountriesQuery.data?.[0]?.code ?? selectedSourceCountryCode;
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

  const resetQuoteAndFunding = () => {
    quoteMutation.reset();
    fundMutation.reset();
    setFundedRemittanceId(undefined);
    setFundIdempotencyKey(undefined);
  };

  const clearVerification = () => {
    setRecipientName(undefined);
    setRecipientId(undefined);
    setVerificationProvider(undefined);
    setRecipientIsVerified(false);
    setManualRecipientName("");
    setVerificationFailure(undefined);
    setConfirmedRecipient(false);
    verifyMutation.reset();
    resetQuoteAndFunding();
  };

  const clearTransfer = () => {
    clearVerification();
    setNetwork(undefined);
    setPhoneNumber("");
    setActiveStep("amount");
  };

  const verifyMutation = useMutation({
    mutationFn: (request: {
      countryCode: string;
      countryDialCode: string;
      currency: string;
      networkCode: string;
      phoneNumber: string;
    }) => {
      if (!accessToken) throw new Error("Sign in before verifying a recipient.");
      return verifyRecipient(accessToken, {
        ...request,
        payoutMethod: "MOBILE_MONEY",
      });
    },
    onSuccess: (result, request) => {
      // Ignore a response for details the user has edited while verification was in flight.
      if (country?.code !== request.countryCode || network?.code !== request.networkCode || phoneNumber !== request.phoneNumber) {
        return;
      }
      if (!result.verified || !result.recipientId || !result.verifiedName?.trim()) {
        setRecipientName(undefined);
        setRecipientId(undefined);
        setVerificationProvider(undefined);
        setRecipientIsVerified(false);
        setConfirmedRecipient(false);
        setVerificationFailure(result.failureReason ?? "The provider did not return a verified recipient name.");
        return;
      }
      setVerificationFailure(undefined);
      setRecipientName(result.verifiedName.trim());
      setRecipientId(result.recipientId);
      setVerificationProvider(result.verificationProvider ?? undefined);
      setRecipientIsVerified(true);
      setConfirmedRecipient(false);
    },
  });

  const manualRecipientMutation = useMutation({
    mutationFn: () => {
      if (!accessToken || !country || !network) throw new Error("Complete the recipient details first.");
      return addManualRecipient(accessToken, {
        countryCode: country.code,
        currency: country.currency,
        payoutMethod: "MOBILE_MONEY",
        networkCode: network.code,
        phoneNumber,
        recipientName: manualRecipientName.trim(),
      });
    },
    onSuccess: (result) => {
      if (!result.recipientId || !result.recipientName) return;
      setRecipientId(result.recipientId);
      setRecipientName(result.recipientName);
      setVerificationProvider(result.verificationProvider ?? undefined);
      setRecipientIsVerified(result.verified);
      setConfirmedRecipient(false);
      setVerificationFailure(undefined);
      verifyMutation.reset();
    },
  });

  const quoteMutation = useMutation({
    mutationFn: () => {
      if (!accessToken || !recipientId || !confirmedRecipient || !country || !sourceCountry || !amount.trim()) {
        throw new Error("Confirm the recipient and complete the transfer details first.");
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
      setQuoteNow(Date.now());
      setFundIdempotencyKey(crypto.randomUUID());
      setFundedRemittanceId(undefined);
      setActiveStep("review");
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
    <section className="remittance-page mx-auto max-w-6xl space-y-8 pb-12">
      {/* Header */}
      <header className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="eyebrow flex items-center gap-1.5">
            <Send size={14} /> RackPay Transfers
          </p>
          <h1 className="mt-1 text-2xl font-bold tracking-tight text-white sm:text-3xl">
            Send money internationally
          </h1>
          <p className="mt-1 text-sm text-slate-400">
            Send directly to recipient mobile-money accounts with real-time conversion quotes from you rackpay wallet.
          </p>
        </div>
        <Link to="/remittance/history" className="button button-secondary self-start sm:self-auto">
          <History size={16} /> History
        </Link>
      </header>

      {/* Step Progress Navigation */}
      <StepIndicator
        activeStep={activeStep}
        setActiveStep={setActiveStep}
        hasRecipient={Boolean(recipientId && confirmedRecipient)}
        hasQuote={Boolean(quote)}
      />

      {/* Main Grid Layout */}
      <div className="grid items-start gap-8 lg:grid-cols-12">
        {/* Step Panels */}
        <div className="space-y-6 lg:col-span-7">
          {/* STEP 1: Route & Amount */}
          {activeStep === "amount" && (
            <div className="rounded-3xl border border-slate-800 bg-slate-900/90 p-6 shadow-2xl shadow-emerald-950/20">
              <h2 className="text-xl font-semibold text-white">1. Select route & amount</h2>
              <p className="mt-1 text-sm text-slate-400">Choose where you are sending from and how much to transfer.</p>

              <div className="mt-6 space-y-4">
                {/* Sending Country */}
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400">
                    You send from
                  </label>
                  <select
                    value={sourceCountryCode}
                    onChange={(e) => {
                      setSelectedSourceCountryCode(e.target.value);
                      resetQuoteAndFunding();
                    }}
                    disabled={sendCountriesQuery.isPending}
                    className="form-control mt-2 w-full text-sm"
                  >
                    {sendCountriesQuery.data?.map((item) => (
                      <option key={item.code} value={item.code}>
                        {countryFlag(item.code)} {item.name} ({item.currency})
                      </option>
                    ))}
                  </select>
                </div>

                {/* Receiving Country */}
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400">
                    Destination Country
                  </label>
                  <select
                    value={country?.code ?? ""}
                    onChange={(e) => {
                      const nextCountry = countriesQuery.data?.find((item) => item.code === e.target.value);
                      if (nextCountry) {
                        setSelectedCountry(nextCountry);
                        clearTransfer();
                      }
                    }}
                    disabled={countriesQuery.isPending}
                    className="form-control mt-2 w-full text-sm"
                  >
                    {countriesQuery.data?.map((item) => (
                      <option key={item.code} value={item.code}>
                        {countryFlag(item.code)} {item.name} ({item.currency})
                      </option>
                    ))}
                  </select>
                </div>

                {/* Send Amount Input */}
                <div className="pt-2">
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400">
                    Amount to send
                  </label>
                  <div className="relative mt-2 flex items-center rounded-2xl border border-slate-700 bg-slate-950/80 focus-within:border-emerald-500">
                    <input
                      value={amount}
                      onChange={(e) => {
                        setAmount(e.target.value);
                        resetQuoteAndFunding();
                      }}
                      type="number"
                      step="0.01"
                      min="0.01"
                      placeholder="100.00"
                      className="w-full bg-transparent px-4 py-3 text-2xl font-bold text-white focus:outline-none"
                    />
                    <span className="mr-4 rounded-xl bg-slate-800 px-3 py-1.5 text-sm font-semibold text-slate-200">
                      {sourceCountry?.currency ?? "EUR"}
                    </span>
                  </div>
                </div>

                {/* Live Indicative Estimate Card */}
                {selectedRate && indicativeDestination !== undefined && (
                  <div className="rounded-2xl border border-emerald-900/50 bg-emerald-950/20 p-4">
                    <p className="text-xs text-emerald-400">Estimated Recipient Amount</p>
                    <p className="mt-1 text-2xl font-bold text-white">
                      {money(indicativeDestination, country?.currency ?? "")}
                    </p>
                    <p className="mt-1 text-xs text-slate-400">
                      1 {sourceCountry?.currency} = {Number(selectedRate.rate).toFixed(4)} {country?.currency}
                    </p>
                  </div>
                )}

                <button
                  type="button"
                  disabled={!amountIsValid}
                  onClick={() => setActiveStep("recipient")}
                  className="button button-primary mt-4 w-full justify-center py-3 text-base"
                >
                  Continue to Recipient <ArrowRight size={18} />
                </button>
              </div>
            </div>
          )}

          {/* STEP 2: Recipient Details */}
          {activeStep === "recipient" && (
            <div className="rounded-3xl border border-slate-800 bg-slate-900/90 p-6 shadow-2xl shadow-emerald-950/20">
              <div className="flex items-center justify-between">
                <h2 className="text-xl font-semibold text-white">2. Recipient details</h2>
                <button
                  type="button"
                  onClick={() => setActiveStep("amount")}
                  className="text-xs text-slate-400 underline hover:text-slate-200"
                >
                  Edit Amount
                </button>
              </div>
              <p className="mt-1 text-sm text-slate-400">Choose the payout network and recipient mobile number.</p>

              <div className="mt-6 space-y-4">
                {/* Mobile Money Network */}
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400">
                    Mobile Money Network
                  </label>
                  <select
                    value={network?.code ?? ""}
                    onChange={(e) => {
                      setNetwork(networksQuery.data?.find((item) => item.code === e.target.value));
                      clearVerification();
                    }}
                    disabled={networksQuery.isPending}
                    className="form-control mt-2 w-full text-sm"
                  >
                    <option value="">Select Network</option>
                    {networksQuery.data?.map((item) => (
                      <option key={item.id} value={item.code}>{item.name}</option>
                    ))}
                  </select>
                </div>

                {/* Recipient Phone Number */}
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400">
                    Phone Number
                  </label>
                  <div className="mt-2 flex">
                    <span className="flex items-center rounded-l-xl border border-r-0 border-slate-700 bg-slate-800 px-3 text-sm text-slate-300">
                      {country?.dialCode ?? "—"}
                    </span>
                    <input
                      value={phoneNumber}
                      onChange={(e) => {
                        setPhoneNumber(e.target.value);
                        clearVerification();
                      }}
                      inputMode="tel"
                      placeholder="024 123 4567"
                      className="form-control min-w-0 rounded-l-none text-sm"
                    />
                  </div>
                </div>

                {/* Verification Box */}
                {recipientName && recipientId ? (
                  <div className={`rounded-2xl border p-4 ${recipientIsVerified ? "border-emerald-500/30 bg-emerald-950/40" : "border-amber-500/40 bg-amber-950/20"}`}>
                    <div className="flex items-center gap-3">
                      <CheckCircle2 className={`${recipientIsVerified ? "text-emerald-400" : "text-amber-400"} shrink-0`} size={20} />
                      <div>
                        <p className={`text-xs font-semibold uppercase ${recipientIsVerified ? "text-emerald-400" : "text-amber-400"}`}>
                          {recipientIsVerified ? `Verified via ${verificationProvider ?? "payment provider"}` : "Name entered by sender · unverified"}
                        </p>
                        <p className="text-lg font-bold text-white">{recipientName}</p>
                      </div>
                    </div>
                    {!recipientIsVerified && <p className="mt-2 text-xs text-amber-200">The provider could not confirm this name. Check the recipient’s name and number carefully; a wrong detail may cause a failed or misdirected transfer.</p>}
                    <label className="mt-3 flex items-start gap-2.5 text-xs text-slate-300 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={confirmedRecipient}
                        onChange={(e) => setConfirmedRecipient(e.target.checked)}
                        className="mt-0.5 accent-emerald-400"
                      />
                      I confirm this is the recipient I intend to send money to.
                    </label>
                  </div>
                ) : (
                  <div className="space-y-2">
                    <button
                      type="button"
                      disabled={!network || !phoneNumberIsValid || verifyMutation.isPending}
                      onClick={() => {
                        if (!country || !network) return;
                        verifyMutation.mutate({
                          countryCode: country.code,
                          countryDialCode: country.dialCode,
                          currency: country.currency,
                          networkCode: network.code,
                          phoneNumber,
                        });
                      }}
                      className="button button-secondary w-full justify-center py-2.5"
                    >
                      {verifyMutation.isPending ? "Verifying..." : "Verify Recipient Name"}
                    </button>
                  </div>
                )}

                {(verificationFailure || verifyMutation.error) && (
                  <p role="alert" className="rounded-xl border border-red-900/40 bg-red-950/20 p-3 text-xs text-red-300">
                    {verificationFailure ?? (verifyMutation.error instanceof Error
                      ? verifyMutation.error.message
                      : "Recipient verification failed. Try again.")}
                  </p>
                )}

                {!recipientId && (verificationFailure || verifyMutation.error) && (
                  <div className="space-y-2 rounded-xl border border-amber-500/30 bg-amber-950/10 p-3">
                    <label className="block text-xs font-semibold text-amber-200" htmlFor="manual-recipient-name">Enter recipient name manually</label>
                    <input
                      id="manual-recipient-name"
                      value={manualRecipientName}
                      onChange={(event) => setManualRecipientName(event.target.value)}
                      maxLength={255}
                      autoComplete="name"
                      placeholder="Recipient’s full name"
                      className="form-control w-full text-sm"
                    />
                    <p className="text-xs text-amber-200">This name is not verified by Flutterwave or Paystack.</p>
                    <button
                      type="button"
                      disabled={!network || !phoneNumberIsValid || !manualRecipientName.trim() || manualRecipientMutation.isPending}
                      onClick={() => manualRecipientMutation.mutate()}
                      className="button button-secondary w-full justify-center disabled:cursor-not-allowed disabled:opacity-40"
                    >
                      {manualRecipientMutation.isPending ? "Saving recipient..." : "Continue with unverified name"}
                    </button>
                    {manualRecipientMutation.error && <p role="alert" className="text-xs text-red-300">{manualRecipientMutation.error instanceof Error ? manualRecipientMutation.error.message : "Could not save recipient."}</p>}
                  </div>
                )}

                <div className="pt-2 flex gap-3">
                  <button
                    type="button"
                    onClick={() => setActiveStep("amount")}
                    className="button button-secondary flex-1 justify-center"
                  >
                    Back
                  </button>
                  <button
                    type="button"
                    disabled={!confirmedRecipient || quoteMutation.isPending}
                    onClick={() => quoteMutation.mutate()}
                    className="button button-primary flex-1 justify-center disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    {quoteMutation.isPending ? "Fetching Quote..." : "Get Transfer Quote"}
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* STEP 3: Review & Payment Confirmation */}
          {activeStep === "review" && quote && (
            <div className="rounded-3xl border border-emerald-800/60 bg-slate-900/90 p-6 shadow-2xl shadow-emerald-950/30">
              <div className="flex items-center justify-between">
                <h2 className="text-xl font-semibold text-white">3. Final Review & Fund</h2>
                <span className="inline-flex items-center gap-1 rounded-full bg-slate-800 px-3 py-1 text-xs text-slate-300">
                  <Clock3 size={13} /> Expires at {new Date(quote.expiresAt).toLocaleTimeString()}
                </span>
              </div>

              <div className="mt-6 rounded-2xl bg-slate-950/70 p-5 space-y-3">
                <div className="flex justify-between text-sm">
                  <span className="text-slate-400">Source Amount:</span>
                  <span className="font-semibold text-white">{money(Number(quote.sourceAmount), quote.sourceCurrency)}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-slate-400">Transfer Fee:</span>
                  <span className="font-semibold text-white">{money(Number(quote.feeAmount), quote.sourceCurrency)}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-slate-400">Guaranteed FX Rate:</span>
                  <span className="font-semibold text-emerald-400">
                    1 {quote.sourceCurrency} = {Number(quote.fxRate).toFixed(4)} {quote.destinationCurrency}
                  </span>
                </div>
                <div className="my-2 border-t border-slate-800" />
                <div className="flex justify-between text-base">
                  <span className="font-medium text-slate-200">Recipient Receives:</span>
                  <span className="text-xl font-bold text-emerald-400">
                    {money(Number(quote.destinationAmount), quote.destinationCurrency)}
                  </span>
                </div>
              </div>

              {quoteExpired && (
                <p className="mt-3 text-xs text-amber-300">This quote expired. Please go back to refresh.</p>
              )}

              {/* Action Buttons */}
              <div className="mt-6 space-y-3">
                {!fundedRemittanceId ? (
                  <button
                    type="button"
                    disabled={fundMutation.isPending || quoteExpired}
                    onClick={() => fundMutation.mutate()}
                    className="button button-primary w-full justify-center py-3 text-base"
                  >
                    {fundMutation.isPending ? "Funding Transfer..." : "Confirm & Fund Transfer"}
                  </button>
                ) : (
                  <div className="rounded-2xl border border-emerald-500/30 bg-emerald-950/30 p-4 space-y-3">
                    <p className="text-xs text-emerald-400 font-semibold uppercase">Transfer Funded!</p>
                    <p className="text-xs text-slate-300">
                      Status: <strong className="text-white">{remittanceQuery.data?.status ?? "FUNDS_RESERVED"}</strong>
                    </p>
                    <button
                      type="button"
                      disabled={payoutMutation.isPending}
                      onClick={() => payoutMutation.mutate()}
                      className="button button-primary w-full justify-center"
                    >
                      {payoutMutation.isPending ? "Starting Payout…" : "Start Payout"}
                    </button>
                  </div>
                )}

                <button
                  type="button"
                  onClick={() => setActiveStep("recipient")}
                  className="button button-secondary w-full justify-center text-xs"
                >
                  Change Recipient / Details
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Transfer Summary Sidebar */}
        <aside className="space-y-6 lg:col-span-5 lg:sticky lg:top-6">
          <div className="rounded-3xl border border-slate-800 bg-slate-900/60 p-6 backdrop-blur">
            <h3 className="text-base font-semibold text-white">Transfer Summary</h3>

            {/* Route Section */}
            <div className="mt-5 space-y-4">
              <div className="flex items-center gap-3">
                <span className="text-2xl">{sourceCountry ? countryFlag(sourceCountry.code) : "🇪🇺"}</span>
                <div>
                  <p className="text-xs text-slate-400">Sending from</p>
                  <p className="text-sm font-semibold text-white">{sourceCountry?.name ?? "Europe"}</p>
                </div>
              </div>

              <div className="ml-4 h-6 border-l border-dashed border-slate-700" />

              <div className="flex items-center gap-3">
                <span className="text-2xl">{country ? countryFlag(country.code) : "🌍"}</span>
                <div>
                  <p className="text-xs text-slate-400">Receiving in</p>
                  <p className="text-sm font-semibold text-white">{country?.name ?? "Destination"}</p>
                </div>
              </div>
            </div>

            {/* Live Financial Breakdown */}
            <div className="mt-6 border-t border-slate-800 pt-4 space-y-2.5 text-xs">
              <div className="flex justify-between items-center">
                <span className="text-slate-400">You send</span>
                <span className="font-semibold text-white text-sm">
                  {amountIsValid ? money(Number(amount), sourceCountry?.currency ?? "EUR") : "—"}
                </span>
              </div>

              <div className="flex justify-between items-center">
                <span className="text-slate-400">Recipient receives</span>
                <span className="font-semibold text-emerald-400 text-sm">
                  {quote
                    ? money(Number(quote.destinationAmount), quote.destinationCurrency)
                    : indicativeDestination !== undefined && country
                    ? money(indicativeDestination, country.currency)
                    : "—"}
                </span>
              </div>

              {quote ? (
                <div className="flex justify-between items-center pt-2 border-t border-slate-800/60">
                  <span className="text-slate-400">Transfer Fee</span>
                  <span className="font-medium text-slate-200">
                    {money(Number(quote.feeAmount), quote.sourceCurrency)}
                  </span>
                </div>
              ) : selectedRate ? (
                <div className="flex justify-between items-center pt-2 border-t border-slate-800/60">
                  <span className="text-slate-400">Exchange Rate (Indicative)</span>
                  <span className="font-medium text-slate-200">
                    1 {sourceCountry?.currency} = {Number(selectedRate.rate).toFixed(4)} {country?.currency}
                  </span>
                </div>
              ) : null}
            </div>

            {/* Recipient & Network Info */}
            <div className="mt-4 border-t border-slate-800 pt-4 space-y-2 text-xs text-slate-400">
              <div className="flex justify-between">
                <span>Network:</span>
                <span className="font-medium text-slate-200">{network?.name ?? "Not selected"}</span>
              </div>
              <div className="flex justify-between">
                <span>Recipient:</span>
                <span className="font-medium text-slate-200">{recipientName ?? "Unverified"}</span>
              </div>
            </div>

            {/* Security Note */}
            <div className="mt-6 rounded-2xl bg-slate-950/50 p-3.5 flex items-start gap-2.5 text-xs text-slate-400">
              <ShieldCheck size={18} className="text-emerald-400 shrink-0 mt-0.5" />
              <span>RackPay guarantees zero hidden fees and instant payout dispatch upon funding.</span>
            </div>
          </div>
        </aside>
      </div>
    </section>
  );
}

// --- Step Indicator Helper Component ---
function StepIndicator({
  activeStep,
  setActiveStep,
  hasRecipient,
  hasQuote,
}: {
  activeStep: Step;
  setActiveStep: (step: Step) => void;
  hasRecipient: boolean;
  hasQuote: boolean;
}) {
  return (
    <div className="flex items-center justify-between rounded-2xl border border-slate-800 bg-slate-900/60 p-2 text-xs sm:text-sm">
      <button
        type="button"
        onClick={() => setActiveStep("amount")}
        className={`flex flex-1 items-center justify-center gap-2 rounded-xl py-2 font-medium transition ${
          activeStep === "amount" ? "bg-emerald-500/10 text-emerald-400" : "text-slate-400"
        }`}
      >
        <span className="flex h-5 w-5 items-center justify-center rounded-full bg-slate-800 text-xs font-bold">1</span>
        Amount
      </button>

      <span className="text-slate-700">•</span>

      <button
        type="button"
        disabled={!hasRecipient && activeStep === "amount"}
        onClick={() => setActiveStep("recipient")}
        className={`flex flex-1 items-center justify-center gap-2 rounded-xl py-2 font-medium transition ${
          activeStep === "recipient" ? "bg-emerald-500/10 text-emerald-400" : "text-slate-400"
        }`}
      >
        <span className="flex h-5 w-5 items-center justify-center rounded-full bg-slate-800 text-xs font-bold">2</span>
        Recipient
      </button>

      <span className="text-slate-700">•</span>

      <button
        type="button"
        disabled={!hasQuote}
        onClick={() => setActiveStep("review")}
        className={`flex flex-1 items-center justify-center gap-2 rounded-xl py-2 font-medium transition ${
          activeStep === "review" ? "bg-emerald-500/10 text-emerald-400" : "text-slate-400"
        }`}
      >
        <span className="flex h-5 w-5 items-center justify-center rounded-full bg-slate-800 text-xs font-bold">3</span>
        Review & Pay
      </button>
    </div>
  );
}
