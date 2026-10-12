import { useMutation } from "@tanstack/react-query";
import { ArrowLeft, CircleAlert, UserPlus } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../../shared/api/httpClient";
import { useAuthStore } from "../../../shared/auth/authStore";
import { createAdministrator } from "../api/adminUserAdminApi";

export function AdminUserAdminPage() {
  const token = useAuthStore((state) => state.accessToken);
  const [email, setEmail] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState("");

  const mutation = useMutation({
    mutationFn: () => {
      if (!token) throw new Error("Missing access token");
      return createAdministrator(token, {
        email: email.trim(),
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        phone: phone.trim() || undefined,
        password,
      });
    },
    onSuccess: () => {
      setError("");
      setEmail("");
      setFirstName("");
      setLastName("");
      setPhone("");
      setPassword("");
      setConfirmation("");
    },
    onError: (cause: unknown) => {
      if (cause instanceof ApiError && cause.status === 403) {
        setError("Only the bootstrap administrator can create additional administrators.");
      } else if (cause instanceof ApiError && cause.correlationId) {
        setError(`${cause.message} (Reference: ${cause.correlationId})`);
      } else {
        setError(cause instanceof Error ? cause.message : "Could not create administrator.");
      }
    },
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    if (password.length < 12) {
      setError("Password must be at least 12 characters.");
      return;
    }
    if (password !== confirmation) {
      setError("Passwords do not match.");
      return;
    }
    mutation.mutate();
  }

  return (
    <section className="min-h-dvh space-y-6 py-8">
      <Link to="/admin/payment-providers" className="inline-flex items-center gap-2 text-sm text-slate-400 hover:text-white">
        <ArrowLeft size={16} /> Back to platform settings
      </Link>
      <header className="flex items-start gap-3">
        <span className="rounded-2xl border border-slate-800 bg-slate-900 p-3 text-cyan-300"><UserPlus size={22} /></span>
        <div>
          <p className="text-sm font-medium text-slate-400">Platform administration</p>
          <h1 className="mt-1 text-2xl font-semibold">Create administrator</h1>
          <p className="mt-2 text-sm leading-6 text-slate-400">Create a RackPay account with platform-admin access. Only the bootstrap administrator can complete this action.</p>
        </div>
      </header>

      {mutation.isSuccess ? <div role="status" className="rounded-2xl border border-emerald-900 bg-emerald-950/40 p-4 text-sm text-emerald-200">Administrator created for {mutation.data.email}. This account is not the bootstrap administrator.</div> : null}
      {error ? <div role="alert" className="flex gap-2 rounded-2xl border border-rose-900 bg-rose-950/40 p-4 text-sm text-rose-200"><CircleAlert size={18} />{error}</div> : null}

      <form onSubmit={submit} className="space-y-4 rounded-2xl border border-slate-800 bg-slate-900 p-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block space-y-2 text-sm">First name<input required maxLength={100} autoComplete="given-name" value={firstName} onChange={e => setFirstName(e.target.value)} className="w-full rounded-xl border border-slate-700 bg-slate-950 px-3 py-3 outline-none focus:border-cyan-400" /></label>
          <label className="block space-y-2 text-sm">Last name<input required maxLength={100} autoComplete="family-name" value={lastName} onChange={e => setLastName(e.target.value)} className="w-full rounded-xl border border-slate-700 bg-slate-950 px-3 py-3 outline-none focus:border-cyan-400" /></label>
        </div>
        <label className="block space-y-2 text-sm">Email address<input required type="email" maxLength={320} autoComplete="email" value={email} onChange={e => setEmail(e.target.value)} className="w-full rounded-xl border border-slate-700 bg-slate-950 px-3 py-3 outline-none focus:border-cyan-400" /></label>
        <label className="block space-y-2 text-sm">Phone number (optional)<input type="tel" maxLength={30} autoComplete="tel" value={phone} onChange={e => setPhone(e.target.value)} className="w-full rounded-xl border border-slate-700 bg-slate-950 px-3 py-3 outline-none focus:border-cyan-400" /></label>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block space-y-2 text-sm">Temporary password<input required type="password" minLength={12} maxLength={128} autoComplete="new-password" value={password} onChange={e => setPassword(e.target.value)} className="w-full rounded-xl border border-slate-700 bg-slate-950 px-3 py-3 outline-none focus:border-cyan-400" /><span className="text-xs text-slate-500">At least 12 characters.</span></label>
          <label className="block space-y-2 text-sm">Confirm password<input required type="password" minLength={12} maxLength={128} autoComplete="new-password" value={confirmation} onChange={e => setConfirmation(e.target.value)} className="w-full rounded-xl border border-slate-700 bg-slate-950 px-3 py-3 outline-none focus:border-cyan-400" /></label>
        </div>
        <button type="submit" disabled={mutation.isPending} className="w-full rounded-xl bg-cyan-400 px-4 py-3 font-semibold text-slate-950 hover:bg-cyan-300 disabled:opacity-50">{mutation.isPending ? "Creating…" : "Create administrator"}</button>
        <p className="text-xs leading-5 text-slate-500">The backend enforces the bootstrap-admin rule and validates the request. Frontend checks are only for usability.</p>
      </form>
    </section>
  );
}
