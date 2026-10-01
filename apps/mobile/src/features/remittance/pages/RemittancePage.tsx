import { useMutation, useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { ArrowLeft, CheckCircle2, Send } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import {
  createQuote,
  listMobileMoneyNetworks,
  listRemittanceCountries,
  verifyRecipient,
  type MobileMoneyNetwork,
  type RemittanceCountry,
} from "../api/remittanceApi";
import { useAuthStore } from "../../../shared/auth/authStore";

type Step = "country" | "recipient" | "amount";

export function RemittancePage() {
  const accessToken = useAuthStore((state) => state.accessToken);
  const [country, setCountry] = useState<RemittanceCountry>();
  const [network, setNetwork] = useState<MobileMoneyNetwork>();
  const [phoneNumber, setPhoneNumber] = useState("");
  const [recipientName, setRecipientName] = useState<string>();
  const [recipientId, setRecipientId] = useState<string>();
  const [amount, setAmount] = useState("");
  const [step, setStep] = useState<Step>("country");
  const [sourceCountryCode, setSourceCountryCode] = useState("BE");
  const [sourceCurrency, setSourceCurrency] = useState("EUR");
  const [confirmedRecipient, setConfirmedRecipient] = useState(false);

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
        currency: country.currencyCode,
        payoutMethod: "MOBILE_MONEY",
        networkCode: network.code,
        phoneNumber,
      });
    },
    onSuccess: (result) => {
      setRecipientName(result.verifiedName);
      setRecipientId(result.recipientId);
      setConfirmedRecipient(false);
    },
  });

  const quoteMutation = useMutation({
    mutationFn: () => {
      if (!accessToken || !recipientId || !country || !amount.trim()) {
        throw new Error("Quote details are incomplete.");
      }

      return createQuote(accessToken, {
        recipientId,
        sourceCountryCode,
        sourceCurrency,
        destinationCurrency: country.currencyCode,
        sourceAmount: amount.trim(),
      });
    },
  });

  useEffect(() => {
    if (verifyMutation.data?.verifiedName) {
      setStep("recipient");
    }
  }, [verifyMutation.data]);

  const quote = quoteMutation.data;
  const quoteExpired = useMemo(
    () => (quote ? Date.parse(quote.expiresAt) <= Date.now() : false),
    [quote],
  );

  if (!accessToken) {
    return null;
  }

  return (
    <section className="flex min-h-dvh flex-col gap-6 pb-10 pt-4">
      <header className="flex items-center gap-3">
        <Link
          to="/"
          className="rounded-xl border border-slate-800 p-2 text-slate-300"
          aria-label="Back home"
        >
          <ArrowLeft size={18} />
        </Link>
        <div>
          <p className="text-sm text-slate-500">Send money</p>
          <h1 className="text-3xl font-semibold tracking-tight">Remittance</h1>
        </div>
      </header>

      <div className="flex gap-2">
        {(["country", "recipient", "amount"] as Step[]).map((item, index) => (
          <div key={item} className="flex flex-1 items-center gap-2">
            <div
              className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold ${
                step === item ? "bg-white text-slate-950" : "bg-slate-800 text-slate-400"
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
        <section className="rounded-3xl border border-slate-800 bg-slate-900 p-5">
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
                    setStep("recipient");
                  }}
                  className={`flex items-center justify-between rounded-2xl border p-4 text-left ${
                    country?.code === item.code
                      ? "border-white bg-slate-800"
                      : "border-slate-800 bg-slate-950"
                  }`}
                >
                  <span>
                    <span className="block font-medium">{item.name}</span>
                    <span className="text-xs text-slate-500">
                      {item.dialCode} · {item.currencyCode}
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
        <section className="rounded-3xl border border-slate-800 bg-slate-900 p-5">
          <h2 className="font-medium">Recipient</h2>
          <p className="mt-1 text-sm text-slate-500">
            Verify the mobile-money account before you fund the transfer.
          </p>

          <label className="mt-5 block text-sm text-slate-400">
            Network
            <select
              value={network?.code ?? ""}
              onChange={(event) =>
                setNetwork(
                  networksQuery.data?.find(
                    (item) => item.code === event.target.value,
                  ),
                )
              }
              className="mt-2 w-full rounded-2xl border border-slate-700 bg-slate-950 px-3 py-3 text-sm text-white"
            >
              <option value="">Select network</option>
              {networksQuery.data?.map((item) => (
                <option key={item.id} value={item.code}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>

          <label className="mt-4 block text-sm text-slate-400">
            Mobile-money phone number
            <div className="mt-2 flex">
              <span className="rounded-l-2xl border border-r-0 border-slate-700 bg-slate-800 px-3 py-3 text-sm text-slate-300">
                {country.dialCode}
              </span>
              <input
                value={phoneNumber}
                onChange={(event) => setPhoneNumber(event.target.value)}
                inputMode="tel"
                autoComplete="tel"
                placeholder="024 123 4567"
                className="min-w-0 flex-1 rounded-r-2xl border border-slate-700 bg-slate-950 px-3 py-3 text-sm text-white outline-none"
              />
            </div>
          </label>

          <button
            type="button"
            disabled={
              !network ||
              phoneNumber.trim().length < 6 ||
              verifyMutation.isPending
            }
            onClick={() => verifyMutation.mutate()}
            className="mt-5 w-full rounded-2xl bg-white px-4 py-3 font-semibold text-slate-950 disabled:opacity-40"
          >
            {verifyMutation.isPending ? "Verifying…" : "Verify recipient"}
          </button>

          {verifyMutation.isError ? (
            <p className="mt-3 text-sm text-red-300">
              The recipient could not be verified. Check the network and number.
            </p>
          ) : null}

          {recipientName ? (
            <div className="mt-5 rounded-2xl border border-slate-700 bg-slate-950 p-4">
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
        <section className="rounded-3xl border border-slate-800 bg-slate-900 p-5">
          <h2 className="font-medium">Amount</h2>
          <p className="mt-1 text-sm text-slate-500">
            Enter the amount in your sending currency. RackPay supplies the quote and FX rate.
          </p>

          <label className="mt-5 block text-sm text-slate-400">
            You send
            <div className="mt-2 flex">
              <span className="rounded-l-2xl border border-r-0 border-slate-700 bg-slate-800 px-3 py-3 text-sm text-slate-300">
                {sourceCurrency}
              </span>
              <input
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
                inputMode="decimal"
                placeholder="100.00"
                className="min-w-0 flex-1 rounded-r-2xl border border-slate-700 bg-slate-950 px-3 py-3 text-lg text-white outline-none"
              />
            </div>
          </label>

          <button
            type="button"
            disabled={
              !amount.trim() ||
              quoteMutation.isPending
            }
            onClick={() => quoteMutation.mutate()}
            className="mt-5 w-full rounded-2xl bg-white px-4 py-3 font-semibold text-slate-950 disabled:opacity-40"
          >
            {quoteMutation.isPending ? "Getting quote…" : "Get quote"}
          </button>

          {quoteMutation.isError ? (
            <p className="mt-3 text-sm text-red-300">
              The quote could not be created. Please review the amount and try again.
            </p>
          ) : null}

          {quote ? (
            <div className="mt-5 rounded-2xl border border-slate-700 bg-slate-950 p-4">
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

              <div className="mt-4 grid gap-3 text-sm">
                <QuoteRow label="You send" value={`${quote.sourceAmount} ${quote.sourceCurrency}`} />
                <QuoteRow label="Fee" value={`${quote.feeAmount} ${quote.sourceCurrency}`} />
                <QuoteRow label="Recipient gets" value={`${quote.destinationAmount} ${quote.destinationCurrency}`} />
                <QuoteRow label="FX rate" value={quote.fxRate} />
              </div>

              {quoteExpired ? (
                <p className="mt-4 text-sm text-amber-300">
                  This quote has expired. Get a new quote before funding.
                </p>
              ) : (
                <p className="mt-4 text-xs leading-5 text-slate-500">
                  Funding and payout will be handled by the backend. The app does not
                  choose a payout provider or determine final remittance state.
                </p>
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
