import { useState, type FormEvent } from "react";
import { ArrowRight, LockKeyhole, ShieldCheck } from "lucide-react";
import { Link, useSearchParams } from "react-router-dom";
import { apiFetch } from "../../../shared/api/httpClient";
import { login } from "../../../shared/auth/keycloak";
import { useAuthStore } from "../../../shared/auth/authStore";

export function LoginPage() {
  const error = useAuthStore((state) => state.error);
  const [searchParams, setSearchParams] = useSearchParams();
  const registering = searchParams.get("mode") === "register";
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string>();
  const [notice, setNotice] = useState<string>();

  function setRegistering(value: boolean) {
    const nextParams = new URLSearchParams(searchParams);
    if (value) {
      nextParams.set("mode", "register");
    } else {
      nextParams.delete("mode");
    }
    setSearchParams(nextParams, { replace: true });
  }

  async function handleRegister(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setFormError(undefined);
    setNotice(undefined);

    const formData = new FormData(event.currentTarget);
    try {
      await apiFetch("/api/v1/auth/register", {
        method: "POST",
        body: JSON.stringify({
          email: formData.get("email"),
          firstName: formData.get("firstName"),
          lastName: formData.get("lastName"),
          phone: formData.get("phone") || null,
          password: formData.get("password"),
        }),
      });
      setRegistering(false);
      setNotice("Account created. Sign in with your new credentials.");
    } catch (cause) {
      setFormError(
        cause instanceof Error ? cause.message : "Account creation failed.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="auth-page">
      <div className="auth-intro">
        <Link to="/" className="auth-brand" aria-label="Back to RackPay home"><span className="brand-mark">R</span> rack<span className="brand-light">pay</span></Link>
        <p className="eyebrow">YOUR FINANCIAL SPACE</p>
        <h1>Money management that moves with you.</h1>
        <p>Access your wallet, international transfers and trading controls from one secure workspace.</p>
        <div className="auth-promise"><span><ShieldCheck size={17}/></span><div><strong>Designed around security</strong><small>Authentication and permissions are verified by RackPay and Keycloak.</small></div></div>
        <div className="auth-promise"><span><LockKeyhole size={17}/></span><div><strong>One account, one workspace</strong><small>Your account and wallet are created together during registration.</small></div></div>
      </div>
      <div className="auth-card">
        <div className="auth-card-heading">
          <p className="eyebrow">{registering ? "GET STARTED" : "WELCOME BACK"}</p>
          <h2 className="text-2xl font-semibold text-slate-50">
            {registering ? "Create account" : "Sign in"}
          </h2>
          <p className="mt-2 text-sm leading-6 text-slate-400">
            {registering
              ? "Create your account and wallet securely."
              : "Sign in to continue to your account."}
          </p>
        </div>

        {notice ? (
          <div
            className="mt-5 rounded-md border border-emerald-900 bg-emerald-950/40 p-3 text-sm text-emerald-200"
            role="status"
          >
            {notice}
          </div>
        ) : null}

        {formError || error ? (
          <div
            className="mt-5 rounded-md border border-red-900 bg-red-950/40 p-3 text-sm text-red-200"
            role="alert"
          >
            {formError ?? error}
          </div>
        ) : null}

        {registering ? (
          <form
            className="auth-form mt-6 grid gap-4"
            onSubmit={(event) => void handleRegister(event)}
          >
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="grid gap-2 text-sm font-medium text-slate-300">
                First name
                <input
                  name="firstName"
                  autoComplete="given-name"
                  required
                  className="min-w-0 rounded-md border border-slate-700 bg-slate-950 px-3 py-3 text-base text-slate-100 outline-none transition placeholder:text-slate-500 focus:border-slate-400 focus:ring-2 focus:ring-slate-400/20"
                />
              </label>
              <label className="grid gap-2 text-sm font-medium text-slate-300">
                Last name
                <input
                  name="lastName"
                  autoComplete="family-name"
                  required
                  className="min-w-0 rounded-md border border-slate-700 bg-slate-950 px-3 py-3 text-base text-slate-100 outline-none transition placeholder:text-slate-500 focus:border-slate-400 focus:ring-2 focus:ring-slate-400/20"
                />
              </label>
            </div>
            <label className="grid gap-2 text-sm font-medium text-slate-300">
              Email
              <input
                name="email"
                type="email"
                autoComplete="email"
                required
                className="min-w-0 rounded-md border border-slate-700 bg-slate-950 px-3 py-3 text-base text-slate-100 outline-none transition placeholder:text-slate-500 focus:border-slate-400 focus:ring-2 focus:ring-slate-400/20"
              />
            </label>
            <label className="grid gap-2 text-sm font-medium text-slate-300">
              Phone <span className="font-normal text-slate-500">Optional</span>
              <input
                name="phone"
                type="tel"
                autoComplete="tel"
                className="min-w-0 rounded-md border border-slate-700 bg-slate-950 px-3 py-3 text-base text-slate-100 outline-none transition placeholder:text-slate-500 focus:border-slate-400 focus:ring-2 focus:ring-slate-400/20"
              />
            </label>
            <label className="grid gap-2 text-sm font-medium text-slate-300">
              Password
              <input
                name="password"
                type="password"
                autoComplete="new-password"
                minLength={12}
                maxLength={128}
                required
                className="min-w-0 rounded-md border border-slate-700 bg-slate-950 px-3 py-3 text-base text-slate-100 outline-none transition placeholder:text-slate-500 focus:border-slate-400 focus:ring-2 focus:ring-slate-400/20"
              />
              <span className="text-xs font-normal text-slate-500">
                Use at least 12 characters.
              </span>
            </label>
            <button
              type="submit"
              disabled={submitting}
              className="mt-1 rounded-md bg-white px-4 py-3 font-semibold text-slate-950 transition hover:bg-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {submitting ? "Creating account…" : "Create account"}
            </button>
            <button
              type="button"
              onClick={() => setRegistering(false)}
              className="rounded-md border border-slate-700 px-4 py-3 font-semibold text-slate-200 transition hover:bg-slate-800"
            >
              Back to sign in
            </button>
          </form>
        ) : (
          <div className="auth-actions mt-6 grid gap-3">
            <button
              type="button"
              onClick={() => void login()}
              className="rounded-md bg-white px-4 py-3 font-semibold text-slate-950 transition hover:bg-slate-200"
            >
              Continue to secure sign in <ArrowRight size={16}/>
            </button>
            <button
              type="button"
              onClick={() => setRegistering(true)}
              className="rounded-md border border-slate-700 px-4 py-3 font-semibold text-slate-200 transition hover:bg-slate-800"
            >
              Create account
            </button>
          </div>
        )}

        <p className="auth-footnote">
          {registering
            ? "Your password is set securely through RackPay's identity service."
            : "Bootstrap administrator? Use the bootstrap email and password configured for your environment, then choose Continue to secure sign in."}
        </p>
        <Link to="/" className="auth-back-home">Back to RackPay home</Link>
      </div>
    </section>
  );
}
