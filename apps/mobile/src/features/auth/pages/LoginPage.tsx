import { useState, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";
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
    <section className="mx-auto flex min-h-dvh w-full max-w-[536px] flex-col justify-center px-4 py-8 text-slate-100 sm:px-0">
      <div className="w-full rounded-lg border border-slate-800 bg-slate-900/70 p-6 shadow-2xl shadow-black/20 sm:p-8">
        <div>
          <h1 className="text-2xl font-semibold text-slate-50">
            {registering ? "Create account" : "Sign in"}
          </h1>
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
            className="mt-6 grid gap-4"
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
          <div className="mt-6 grid gap-3">
            <button
              type="button"
              onClick={() => void login()}
              className="rounded-md bg-white px-4 py-3 font-semibold text-slate-950 transition hover:bg-slate-200"
            >
              Sign in
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

        <p className="mt-6 text-center text-xs leading-5 text-slate-500">
          Sign-in is handled by Keycloak. Registration details are securely
          processed to create your RackPay account.
        </p>
      </div>
    </section>
  );
}
