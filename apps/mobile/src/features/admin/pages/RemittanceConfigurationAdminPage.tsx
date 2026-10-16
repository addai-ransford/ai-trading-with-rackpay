import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, CircleAlert, Globe2, Plus, Route } from "lucide-react";
import { Link } from "react-router-dom";
import { useState } from "react";
import { ApiError } from "../../../shared/api/httpClient";
import { useAuthStore } from "../../../shared/auth/authStore";
import {
  addRemittanceNetwork,
  listAdminRemittanceCountries,
  listRemittanceCorridors,
  REMITTANCE_CURRENCIES,
  saveRemittanceCorridor,
  saveRemittanceCountry,
  updateRemittanceNetwork,
  type RemittanceCurrency,
  type RemittanceNetwork,
} from "../api/remittanceAdminApi";

const inputClass = "form-control w-full text-sm";
const labelClass = "mb-1.5 block text-xs font-semibold uppercase tracking-wide text-slate-400";
const currencies = [...REMITTANCE_CURRENCIES];

function messageFor(error: unknown) {
  if (error instanceof ApiError) return error.message;
  return error instanceof Error ? error.message : "The request could not be completed.";
}

export function RemittanceConfigurationAdminPage() {
  const token = useAuthStore((state) => state.accessToken);
  const cache = useQueryClient();
  const [countryForm, setCountryForm] = useState({ code: "", name: "", dialCode: "+", currency: "EUR" as RemittanceCurrency, enabled: true, sendEnabled: false, receiveEnabled: true });
  const [networkCountry, setNetworkCountry] = useState("");
  const [editingNetwork, setEditingNetwork] = useState<RemittanceNetwork | null>(null);
  const [networkForm, setNetworkForm] = useState({ code: "", name: "", flutterwaveCode: "", paystackCode: "", enabled: true });
  const [corridorForm, setCorridorForm] = useState({ sourceCountryCode: "", destinationCountryCode: "", sourceCurrency: "EUR" as RemittanceCurrency, destinationCurrency: "GHS" as RemittanceCurrency, minAmount: "1", maxAmount: "", feeFixedAmount: "0", feeCurrency: "EUR" as RemittanceCurrency, feeBps: "250", fxRate: "", enabled: false });

  const countries = useQuery({
    queryKey: ["admin", "remittance", "countries"],
    queryFn: () => listAdminRemittanceCountries(token!), enabled: Boolean(token),
  });
  const corridors = useQuery({
    queryKey: ["admin", "remittance", "corridors"],
    queryFn: () => listRemittanceCorridors(token!), enabled: Boolean(token),
  });

  const refresh = async () => {
    await Promise.all([
      cache.invalidateQueries({ queryKey: ["admin", "remittance", "countries"] }),
      cache.invalidateQueries({ queryKey: ["admin", "remittance", "corridors"] }),
      cache.invalidateQueries({ queryKey: ["remittance", "countries"] }),
    ]);
  };
  const countryMutation = useMutation({
    mutationFn: () => saveRemittanceCountry(token!, countryForm),
    onSuccess: async () => { setCountryForm({ code: "", name: "", dialCode: "+", currency: "EUR", enabled: true, sendEnabled: false, receiveEnabled: true }); await refresh(); },
  });
  const networkMutation = useMutation({
    mutationFn: () => editingNetwork
      ? updateRemittanceNetwork(token!, networkCountry, { ...editingNetwork, ...networkForm })
      : addRemittanceNetwork(token!, networkCountry, networkForm),
    onSuccess: async () => { setEditingNetwork(null); setNetworkForm({ code: "", name: "", flutterwaveCode: "", paystackCode: "", enabled: true }); await refresh(); },
  });
  const corridorMutation = useMutation({
    mutationFn: () => saveRemittanceCorridor(token!, {
      ...corridorForm,
      minAmount: corridorForm.minAmount,
      maxAmount: corridorForm.maxAmount || null,
      feeFixedAmount: corridorForm.feeFixedAmount,
      feeBps: Number(corridorForm.feeBps),
      fxRate: corridorForm.fxRate || null,
    }),
    onSuccess: refresh,
  });

  const countryList = countries.data ?? [];
  const operationError = countryMutation.error ?? networkMutation.error ?? corridorMutation.error;
  const loadError = countries.error ?? corridors.error;

  return (
    <section className="mx-auto min-h-dvh max-w-6xl space-y-6 py-8">
      <Link to="/admin/payment-providers" className="inline-flex items-center gap-2 text-sm text-slate-400 hover:text-white"><ArrowLeft size={16}/> Back to administration</Link>
      <header>
        <p className="text-sm font-medium text-slate-400">Platform administration</p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight">Remittance coverage</h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-400">Add countries, mobile money networks, provider codes, and routes. Provider codes must match the selected provider’s documentation. RackPay currently supports these currencies: {currencies.join(", ")}.</p>
      </header>

      {operationError || loadError ? <div role="alert" className="flex gap-3 rounded-2xl border border-rose-900/70 bg-rose-950/40 p-4 text-sm text-rose-200"><CircleAlert size={18} className="mt-0.5 shrink-0"/><p>{messageFor(operationError ?? loadError)}</p></div> : null}
      {(countries.isPending || corridors.isPending) ? <div className="rounded-2xl border border-slate-800 bg-slate-900 p-4 text-sm text-slate-400">Loading remittance configuration…</div> : null}

      <form onSubmit={(event) => { event.preventDefault(); countryMutation.mutate(); }} className="space-y-4 rounded-2xl border border-slate-800 bg-slate-900 p-5">
        <div className="flex items-center gap-2"><Globe2 className="text-cyan-300" size={19}/><h2 className="font-semibold">Add or update a country</h2></div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <label><span className={labelClass}>ISO country code</span><input className={inputClass} required minLength={2} maxLength={2} value={countryForm.code} placeholder="GH" onChange={(e) => setCountryForm({ ...countryForm, code: e.target.value.toUpperCase() })}/></label>
          <label><span className={labelClass}>Country name</span><input className={inputClass} required maxLength={100} value={countryForm.name} placeholder="Ghana" onChange={(e) => setCountryForm({ ...countryForm, name: e.target.value })}/></label>
          <label><span className={labelClass}>Dial code</span><input className={inputClass} required value={countryForm.dialCode} placeholder="+233" onChange={(e) => setCountryForm({ ...countryForm, dialCode: e.target.value })}/></label>
          <label><span className={labelClass}>Currency</span><select className={inputClass} value={countryForm.currency} onChange={(e) => setCountryForm({ ...countryForm, currency: e.target.value as RemittanceCurrency })}>{currencies.map((code) => <option key={code}>{code}</option>)}</select></label>
        </div>
        <div className="flex flex-wrap gap-5 text-sm text-slate-300">
          <label className="flex items-center gap-2"><input type="checkbox" checked={countryForm.enabled} onChange={(e) => setCountryForm({ ...countryForm, enabled: e.target.checked })}/>Country enabled</label>
          <label className="flex items-center gap-2"><input type="checkbox" checked={countryForm.sendEnabled} onChange={(e) => setCountryForm({ ...countryForm, sendEnabled: e.target.checked })}/>Send from this country</label>
          <label className="flex items-center gap-2"><input type="checkbox" checked={countryForm.receiveEnabled} onChange={(e) => setCountryForm({ ...countryForm, receiveEnabled: e.target.checked })}/>Receive in this country</label>
        </div>
        <button className="button button-primary" disabled={countryMutation.isPending}><Plus size={16}/>{countryMutation.isPending ? "Saving…" : "Save country"}</button>
      </form>

      <form onSubmit={(event) => { event.preventDefault(); networkMutation.mutate(); }} className="space-y-4 rounded-2xl border border-slate-800 bg-slate-900 p-5">
        <h2 className="font-semibold">Add a mobile money network</h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <label><span className={labelClass}>Country</span><select required className={inputClass} value={networkCountry} onChange={(e) => setNetworkCountry(e.target.value)}><option value="">Choose country</option>{countryList.map((c) => <option key={c.code} value={c.code}>{c.name} ({c.code})</option>)}</select></label>
          <label><span className={labelClass}>RackPay code</span><input className={inputClass} required maxLength={30} disabled={Boolean(editingNetwork)} value={networkForm.code} placeholder="MTN" onChange={(e) => setNetworkForm({ ...networkForm, code: e.target.value.toUpperCase() })}/></label>
          <label><span className={labelClass}>Display name</span><input className={inputClass} required maxLength={100} value={networkForm.name} placeholder="MTN Mobile Money" onChange={(e) => setNetworkForm({ ...networkForm, name: e.target.value })}/></label>
          <label><span className={labelClass}>Flutterwave code</span><input className={inputClass} value={networkForm.flutterwaveCode} placeholder="e.g. MTN or MPS" onChange={(e) => setNetworkForm({ ...networkForm, flutterwaveCode: e.target.value })}/></label>
          <label><span className={labelClass}>Paystack code</span><input className={inputClass} value={networkForm.paystackCode} placeholder="Provider bank code" onChange={(e) => setNetworkForm({ ...networkForm, paystackCode: e.target.value })}/></label>
        </div>
        <label className="flex items-center gap-2 text-sm text-slate-300"><input type="checkbox" checked={networkForm.enabled} onChange={(e) => setNetworkForm({ ...networkForm, enabled: e.target.checked })}/>Network enabled</label>
        <div className="flex gap-2"><button className="button button-primary" disabled={!networkCountry || networkMutation.isPending}><Plus size={16}/>{networkMutation.isPending ? "Saving…" : editingNetwork ? "Update network" : "Add network"}</button>{editingNetwork ? <button type="button" className="button button-secondary" onClick={() => { setEditingNetwork(null); setNetworkForm({ code: "", name: "", flutterwaveCode: "", paystackCode: "", enabled: true }); }}>Cancel edit</button> : null}</div>
        <div className="grid gap-3 md:grid-cols-2">
          {countryList.map((country) => <article key={country.code} className="rounded-xl border border-slate-800 p-3"><h3 className="text-sm font-medium">{country.name} · {country.currency}</h3><div className="mt-2 space-y-2">{country.networks.length ? country.networks.map((network) => <div key={network.id} className="flex items-center justify-between gap-2 text-xs"><span className="text-slate-400">{network.name} ({network.code}) · {network.enabled ? "Enabled" : "Disabled"}</span><button type="button" className="text-cyan-300 hover:text-cyan-200" onClick={() => { setNetworkCountry(country.code); setEditingNetwork(network); setNetworkForm({ code: network.code, name: network.name, flutterwaveCode: network.flutterwaveCode ?? "", paystackCode: network.paystackCode ?? "", enabled: network.enabled }); }}>Edit</button></div>) : <p className="text-xs text-slate-500">No networks added yet</p>}</div></article>)}
        </div>
      </form>

      <form onSubmit={(event) => { event.preventDefault(); corridorMutation.mutate(); }} className="space-y-4 rounded-2xl border border-slate-800 bg-slate-900 p-5">
        <div className="flex items-center gap-2"><Route className="text-cyan-300" size={19}/><h2 className="font-semibold">Add or update a remittance route</h2></div>
        <p className="text-xs leading-5 text-slate-500">A country won’t appear as a send or receive option until an enabled route connects it. Saving the same source, destination, and currencies updates that route. Set an FX rate here to manage it in the admin; leave blank to use the backend FX provider.</p>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <label><span className={labelClass}>Send from</span><select required className={inputClass} value={corridorForm.sourceCountryCode} onChange={(e) => { const c=countryList.find(x=>x.code===e.target.value); setCorridorForm({ ...corridorForm, sourceCountryCode: e.target.value, sourceCurrency: c?.currency ?? corridorForm.sourceCurrency, feeCurrency: c?.currency ?? corridorForm.feeCurrency }); }}><option value="">Choose country</option>{countryList.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}</select></label>
          <label><span className={labelClass}>Send currency</span><select className={inputClass} value={corridorForm.sourceCurrency} onChange={(e) => setCorridorForm({ ...corridorForm, sourceCurrency: e.target.value as RemittanceCurrency, feeCurrency: e.target.value as RemittanceCurrency })}>{currencies.map((code) => <option key={code}>{code}</option>)}</select></label>
          <label><span className={labelClass}>Pay out to</span><select required className={inputClass} value={corridorForm.destinationCountryCode} onChange={(e) => { const c=countryList.find(x=>x.code===e.target.value); setCorridorForm({ ...corridorForm, destinationCountryCode: e.target.value, destinationCurrency: c?.currency ?? corridorForm.destinationCurrency }); }}><option value="">Choose country</option>{countryList.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}</select></label>
          <label><span className={labelClass}>Payout currency</span><select className={inputClass} value={corridorForm.destinationCurrency} onChange={(e) => setCorridorForm({ ...corridorForm, destinationCurrency: e.target.value as RemittanceCurrency })}>{currencies.map((code) => <option key={code}>{code}</option>)}</select></label>
          <label><span className={labelClass}>Minimum send amount</span><input className={inputClass} required type="number" min="0.01" step="0.01" value={corridorForm.minAmount} onChange={(e) => setCorridorForm({ ...corridorForm, minAmount: e.target.value })}/></label>
          <label><span className={labelClass}>Maximum send amount</span><input className={inputClass} type="number" min="0.01" step="0.01" value={corridorForm.maxAmount} onChange={(e) => setCorridorForm({ ...corridorForm, maxAmount: e.target.value })}/></label>
          <label><span className={labelClass}>Fixed fee</span><input className={inputClass} required type="number" min="0" step="0.01" value={corridorForm.feeFixedAmount} onChange={(e) => setCorridorForm({ ...corridorForm, feeFixedAmount: e.target.value })}/></label>
          <label><span className={labelClass}>Fee currency</span><select className={inputClass} value={corridorForm.feeCurrency} onChange={(e) => setCorridorForm({ ...corridorForm, feeCurrency: e.target.value as RemittanceCurrency })}>{currencies.map((code) => <option key={code}>{code}</option>)}</select></label>
          <label><span className={labelClass}>Variable fee (basis points)</span><input className={inputClass} required type="number" min="0" max="10000" step="1" value={corridorForm.feeBps} onChange={(e) => setCorridorForm({ ...corridorForm, feeBps: e.target.value })}/></label>
          <label><span className={labelClass}>FX rate (destination per source)</span><input className={inputClass} type="number" min="0.000000000000000001" step="any" value={corridorForm.fxRate} onChange={(e) => setCorridorForm({ ...corridorForm, fxRate: e.target.value })}/></label>
        </div>
        <label className="flex items-center gap-2 text-sm text-slate-300"><input type="checkbox" checked={corridorForm.enabled} onChange={(e) => setCorridorForm({ ...corridorForm, enabled: e.target.checked })}/>Enable route for customers</label>
        <button className="button button-primary" disabled={corridorMutation.isPending || !corridorForm.sourceCountryCode || !corridorForm.destinationCountryCode}><Plus size={16}/>{corridorMutation.isPending ? "Saving…" : "Save route"}</button>
        <div className="space-y-2">{corridors.data?.map((route) => <div key={route.id} className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-slate-800 p-3 text-sm"><span>{route.sourceCountryCode} ({route.sourceCurrency}) → {route.destinationCountryCode} ({route.destinationCurrency}){route.fxRate ? ` · FX ${route.fxRate}` : " · FX from provider"}</span><span className={route.enabled ? "text-emerald-300" : "text-amber-300"}>{route.enabled ? "Enabled" : "Disabled"} · {route.feeBps} bps + {route.feeFixedAmount} {route.feeCurrency}</span></div>)}</div>
      </form>
    </section>
  );
}
