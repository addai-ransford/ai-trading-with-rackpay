import { Link } from "react-router-dom";
import { login, register } from "../../../shared/auth/keycloak";
import { useAuthStore } from "../../../shared/auth/authStore";

export function LoginPage() {
  const error = useAuthStore((state) => state.error);

  return (
    <section className="flex min-h-dvh flex-col justify-center gap-8">
      <div>
        <p className="text-sm font-medium text-slate-400">Welcome back</p>
        <h1 className="mt-2 text-4xl font-semibold tracking-tight">Sign in to RackPay</h1>
        <p className="mt-3 text-base leading-7 text-slate-400">
          Continue securely with your RackPay account.
        </p>
      </div>

      {error ? (
        <div className="rounded-2xl border border-red-900 bg-red-950/40 p-4 text-sm text-red-200">
          {error}
        </div>
      ) : null}

      <div className="grid gap-3">
        <button
          type="button"
          onClick={() => void login()}
          className="rounded-2xl bg-white px-4 py-3 font-semibold text-slate-950 transition hover:bg-slate-200"
        >
          Sign in
        </button>
        <button
          type="button"
          onClick={() => void register()}
          className="rounded-2xl border border-slate-700 px-4 py-3 font-semibold text-slate-100 transition hover:bg-slate-900"
        >
          Create account
        </button>
      </div>

      <p className="text-center text-xs text-slate-500">
        Authentication is handled by Keycloak. RackPay never receives your password.
      </p>

      <Link
        to="/"
        className="text-center text-sm text-slate-400 underline-offset-4 hover:underline"
      >
        Back to home
      </Link>
    </section>
  );
}
