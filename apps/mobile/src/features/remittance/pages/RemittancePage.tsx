import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { ArrowLeft, ChevronDown, Clock3, RefreshCw, Search, ShieldCheck } from "lucide-react";
import { useNavigate } from "react-router-dom";
import {
  createRemittanceQuote,
  listMobileMoneyNetworks,
  listRemittanceCountries,
  verifyRemittanceRecipient,
  type MobileMoneyNetwork,
  type RemittanceCountry,
} from "../../../shared/api/remittanceApi";
import { useAuthStore } from "../../../shared/auth/authStore";

const SOURCE_COUNTRY = "BE";
const SOURCE_CURRENCY = "EUR";
const EMPTY_COUNTRIES: RemittanceCountry[] = [];
const EMPTY_NETWORKS: MobileMoneyNetwork[] = [];

function countryFlag(code: string) {
  return code
    .toUpperCase()
    .replace(/./g, (letter) => String.fromCodePoint(127397 + letter.charCodeAt(0)));
}

function money(value: number | string, currency: string) {
  return new Intl.NumberFormat(undefined, {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(Number(value));
}

export function RemittancePage() {
  const navigate = useNavigate();
  const accessToken = useAuthStore((state) => state.accessToken);
  const [countrySearch, setCountrySearch] = useState("");
  const [countryPickerOpen, setCountryPickerOpen] = useState(false);
  const [countryCode, setCountryCode] = useState("");
  const [networkCode, setNetworkCode] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [amount, setAmount] = useState("100");
  const [now, setNow] = useState(0);
  useEffect(() => {
    const initialTick = window.setTimeout(() => setNow(Date.now()), 0);
    const timer = window.setInterval(() => setNow(Date.now()), 5000);
    return () => {
      window.clearTimeout(initialTick);
      window.clearInterval(timer);
    };
  }, []);

  const [recipient, setRecipient] = useState<{
    recipientId: string;
    verifiedName: string;
    normalizedPhoneNumber: string;
  } | null>(null);

  const countriesQuery = useQuery({
    queryKey: ["remittance-countries", "RECEIVE"],
    queryFn: () => listRemittanceCountries(accessToken!, "RECEIVE"),
    enabled: Boolean(accessToken),
  });
  const countries = countriesQuery.data ?? EMPTY_COUNTRIES;
  const effectiveCountryCode = countryCode || countries[0]?.code || "";
  const selectedCountry = countries.find((country) => country.code === effectiveCountryCode);

  const filteredCountries = useMemo(() => {
    const term = countrySearch.trim().toLocaleLowerCase();
    return countries.filter((country) =>
      !term ||
      country.name.toLocaleLowerCase().includes(term) ||
      country.code.toLocaleLowerCase().includes(term) ||
      country.dialCode.includes(term),
    );
  }, [countries, countrySearch]);

  const networksQuery = useQuery({
    queryKey: ["remittance-networks", effectiveCountryCode],
    queryFn: () => listMobileMoneyNetworks(accessToken!, effectiveCountryCode),
    enabled: Boolean(accessToken && effectiveCountryCode),
  });
  const networks = networksQuery.data ?? EMPTY_NETWORKS;
  const effectiveNetworkCode = networks.some((network) => network.code === networkCode)
    ? networkCode
    : networks[0]?.code ?? "";

  const verifyMutation = useMutation({
    mutationFn: () => {
      if (!accessToken || !selectedCountry) {
        throw new Error("Sign in and select a destination country first.");
      }
      return verifyRemittanceRecipient(accessToken, {
        countryCode: selectedCountry.code,
        countryDialCode: selectedCountry.dialCode,
        currency: selectedCountry.currencyCode,
        payoutMethod: "MOBILE_MONEY",
        networkCode: effectiveNetworkCode,
        phoneNumber: phoneNumber.trim(),
      });
    },
    onSuccess: (result) => {
      setRecipient({
        recipientId: result.recipientId,
        verifiedName: result.verifiedName,
        normalizedPhoneNumber: result.normalizedPhoneNumber,
      });
    },
  });

  const quoteMutation = useMutation({
    mutationFn: () => {
      if (!accessToken || !recipient || !selectedCountry) {
        throw new Error("Verify the recipient before requesting a transfer quote.");
      }
      return createRemittanceQuote(accessToken, {
        recipientId: recipient.recipientId,
        sourceCountryCode: SOURCE_COUNTRY,
        sourceCurrency: SOURCE_CURRENCY,
        destinationCurrency: selectedCountry.currencyCode,
        sourceAmount: Number(amount),
      });
    },
  });

  function chooseCountry(country: RemittanceCountry) {
    setCountryCode(country.code);
    setCountryPickerOpen(false);
    setCountrySearch("");
    setRecipient(null);
    verifyMutation.reset();
    quoteMutation.reset();
  }

  function updatePhone(value: string) {
    setPhoneNumber(value);
    setRecipient(null);
    verifyMutation.reset();
    quoteMutation.reset();
  }

  function updateAmount(value: string) {
    setAmount(value);
    quoteMutation.reset();
  }

  const quote = quoteMutation.data;
  const quoteExpired = Boolean(quote && new Date(quote.expiresAt).getTime() <= now);

  return (
    <section className="flex min-h-dvh flex-col gap-5 pb-10">
      <header className="sticky top-0 z-20 -mx-5 border-b border-slate-800 bg-slate-950/95 px-5 pb-3 pt-4 backdrop-blur">
        <div className="mb-3 flex items-center justify-between">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="inline-flex min-h-10 items-center gap-2 rounded-xl px-2 text-sm font-medium text-slate-300 transition hover:bg-slate-800"
            aria-label="Go back"
          >
            <ArrowLeft size={18} /> Back
          </button>
          <span className="text-xs font-medium text-slate-500">RACKPAY TRANSFERS</span>
        </div>
        <div className="rounded-2xl border border-emerald-400/30 bg-emerald-400/10 p-4">
          <div className="flex items-center gap-2 text-sm text-emerald-200">
            <RefreshCw size={15} /> Your exchange rate
          </div>
          {quote && !quoteExpired ? (
            <>
              <p className="mt-1 text-2xl font-semibold tracking-tight text-white">
                1 {quote.sourceCurrency} = {Number(quote.fxRate).toLocaleString(undefined, { maximumFractionDigits: 6 })} {quote.destinationCurrency}
              </p>
              <div className="mt-2 flex items-center gap-1 text-xs text-slate-300">
                <Clock3 size={13} /> Quote valid until {new Date(quote.expiresAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
              </div>
            </>
          ) : (
            <>
              <p className="mt-1 text-2xl font-semibold tracking-tight text-white">
                {selectedCountry ? `1 EUR → ${selectedCountry.currencyCode}` : "Select a destination"}
              </p>
              <p className="mt-2 text-xs leading-5 text-slate-300">
                {quoteExpired
                  ? "This quote has expired. Request a fresh quote before continuing."
                  : "Verify your recipient to get the live backend quote, including fees and the amount they receive."}
              </p>
            </>
          )}
        </div>
      </header>

      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Send money</h1>
        <p className="mt-1 text-sm text-slate-400">Know the rate and recipient amount before you confirm.</p>
      </div>

      <div className="space-y-2">
        <label htmlFor="country-search" className="text-sm font-medium text-slate-300">Destination country</label>
        <button
          type="button"
          onClick={() => setCountryPickerOpen((open) => !open)}
          className="flex w-full items-center gap-3 rounded-2xl border border-slate-700 bg-slate-900 px-4 py-3 text-left"
          aria-expanded={countryPickerOpen}
        >
          <span className="text-2xl" aria-hidden="true">{selectedCountry ? countryFlag(selectedCountry.code) : "🌍"}</span>
          <span className="min-w-0 flex-1">
            <span className="block font-medium">{selectedCountry?.name ?? (countriesQuery.isLoading ? "Loading countries…" : "Choose a country")}</span>
            <span className="block text-sm text-slate-400">{selectedCountry ? `${selectedCountry.dialCode} · ${selectedCountry.currencyCode}` : "Available destinations"}</span>
          </span>
          <ChevronDown size={18} className={countryPickerOpen ? "rotate-180 text-slate-400" : "text-slate-400"} />
        </button>

        {countryPickerOpen && (
          <div className="overflow-hidden rounded-2xl border border-slate-700 bg-slate-900">
            <div className="flex items-center gap-2 border-b border-slate-800 px-3">
              <Search size={17} className="shrink-0 text-slate-500" />
              <input
                id="country-search"
                value={countrySearch}
                onChange={(event) => setCountrySearch(event.target.value)}
                placeholder="Search country or calling code"
                className="min-w-0 flex-1 bg-transparent py-3 text-sm text-white outline-none placeholder:text-slate-500"
                autoComplete="off"
              />
            </div>
            <div className="max-h-56 overflow-y-auto overscroll-contain" role="listbox" aria-label="Destination countries">
              {filteredCountries.map((country) => (
                <button
                  type="button"
                  key={country.code}
                  role="option"
                  aria-selected={country.code === effectiveCountryCode}
                  onClick={() => chooseCountry(country)}
                  className={`flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-slate-800 ${country.code === countryCode ? "bg-slate-800/70" : ""}`}
                >
                  <span className="text-xl" aria-hidden="true">{countryFlag(country.code)}</span>
                  <span className="min-w-0 flex-1">
                    <span className="block text-sm font-medium">{country.name}</span>
                    <span className="block text-xs text-slate-500">{country.dialCode} · {country.currencyCode}</span>
                  </span>
                  {country.code === effectiveCountryCode && <span className="text-xs text-emerald-300">Selected</span>}
                </button>
              ))}
              {!filteredCountries.length && <p className="px-4 py-5 text-sm text-slate-400">No matching countries found.</p>}
            </div>
          </div>
        )}
        {countriesQuery.isError && <p className="text-sm text-rose-300">Could not load enabled destinations. Please try again.</p>}
      </div>

      <div className="space-y-2">
        <label htmlFor="network" className="text-sm font-medium text-slate-300">Mobile money network</label>
        <select
          id="network"
          value={effectiveNetworkCode}
          onChange={(event) => {
            setNetworkCode(event.target.value);
            setRecipient(null);
            verifyMutation.reset();
            quoteMutation.reset();
          }}
          disabled={!networks.length || networksQuery.isLoading}
          className="w-full rounded-2xl border border-slate-700 bg-slate-900 px-4 py-3 text-white outline-none focus:border-emerald-400 disabled:opacity-50"
        >
          {networksQuery.isLoading && <option value="">Loading networks…</option>}
          {!networksQuery.isLoading && !networks.length && <option value="">No networks available</option>}
          {networks.map((network) => <option key={network.id} value={network.code}>{network.name}</option>)}
        </select>
        {networksQuery.isError && <p className="text-sm text-rose-300">Could not load networks for this destination.</p>}
      </div>

      <div className="space-y-2">
        <label htmlFor="recipient-phone" className="text-sm font-medium text-slate-300">Recipient phone number</label>
        <div className="flex overflow-hidden rounded-2xl border border-slate-700 bg-slate-900 focus-within:border-emerald-400">
          <span className="flex items-center border-r border-slate-700 px-3 text-sm text-slate-400">{selectedCountry?.dialCode ?? "+—"}</span>
          <input
            id="recipient-phone"
            type="tel"
            inputMode="tel"
            autoComplete="tel-national"
            value={phoneNumber}
            onChange={(event) => updatePhone(event.target.value)}
            placeholder="Phone number"
            className="min-w-0 flex-1 bg-transparent px-3 py-3 text-white outline-none placeholder:text-slate-500"
          />
        </div>
        <button
          type="button"
          disabled={!selectedCountry || !effectiveNetworkCode || !phoneNumber.trim() || verifyMutation.isPending}
          onClick={() => verifyMutation.mutate()}
          className="w-full rounded-2xl border border-slate-700 px-4 py-3 text-sm font-semibold text-slate-100 transition hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {verifyMutation.isPending ? "Verifying recipient…" : "Verify recipient"}
        </button>
        {verifyMutation.isError && <p role="alert" className="text-sm text-rose-300">{verifyMutation.error.message}</p>}
        {recipient && (
          <div className="flex items-start gap-3 rounded-2xl border border-emerald-400/30 bg-emerald-400/10 p-3">
            <ShieldCheck size={20} className="mt-0.5 shrink-0 text-emerald-300" />
            <div>
              <p className="font-medium text-emerald-100">{recipient.verifiedName}</p>
              <p className="mt-1 text-xs text-slate-300">{recipient.normalizedPhoneNumber} · Name verified by payout provider</p>
            </div>
          </div>
        )}
      </div>

      <div className="space-y-2">
        <label htmlFor="send-amount" className="text-sm font-medium text-slate-300">You send</label>
        <div className="flex items-center rounded-2xl border border-slate-700 bg-slate-900 px-4">
          <span className="text-slate-400">€</span>
          <input
            id="send-amount"
            type="number"
            min="0.01"
            step="0.01"
            value={amount}
            onChange={(event) => updateAmount(event.target.value)}
            className="w-full bg-transparent px-3 py-4 text-xl font-semibold text-white outline-none"
          />
          <span className="text-sm font-medium text-slate-400">EUR</span>
        </div>
        <p className="text-xs text-slate-500">Sending from Belgium · EUR</p>
      </div>

      <button
        type="button"
        disabled={!recipient || !selectedCountry || !Number.isFinite(Number(amount)) || Number(amount) <= 0 || quoteMutation.isPending}
        onClick={() => quoteMutation.mutate()}
        className="w-full rounded-2xl bg-emerald-400 px-4 py-4 font-semibold text-slate-950 transition hover:bg-emerald-300 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {quoteMutation.isPending ? "Getting exchange rate…" : quoteExpired ? "Request a new quote" : "Show exchange rate"}
      </button>
      {quoteMutation.isError && <p role="alert" className="text-sm text-rose-300">{quoteMutation.error.message}</p>}

      {quote && !quoteExpired && (
        <div className="space-y-3 rounded-2xl border border-slate-700 bg-slate-900 p-4">
          <div className="flex items-center justify-between text-sm text-slate-400"><span>Transfer amount</span><span className="text-white">{money(quote.sourceAmount, quote.sourceCurrency)}</span></div>
          <div className="flex items-center justify-between text-sm text-slate-400"><span>Transfer fee</span><span className="text-white">{money(quote.feeAmount, quote.sourceCurrency)}</span></div>
          <div className="border-t border-slate-700 pt-3">
            <p className="text-sm text-slate-400">Recipient gets</p>
            <p className="mt-1 text-2xl font-semibold text-emerald-300">{money(quote.destinationAmount, quote.destinationCurrency)}</p>
          </div>
          <p className="text-xs leading-5 text-slate-500">Quote expires {new Date(quote.expiresAt).toLocaleString()}. Request a fresh quote if it expires or if you change the recipient, destination or amount.</p>
        </div>
      )}
      {quoteExpired && <p role="alert" className="text-sm text-amber-300">Your quote expired. Request a new quote to refresh the rate.</p>}
    </section>
  );
}
