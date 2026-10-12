import { useState, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";
import { apiFetch } from "../../../shared/api/httpClient";
import { keycloak } from "../../../shared/auth/keycloak";
import { useAuthStore } from "../../../shared/auth/authStore";

const inputStyle = {
  boxSizing: "border-box" as const,
  width: "100%",
  padding: "12px 14px",
  borderRadius: 10,
  border: "1px solid var(--border, #334155)",
  background: "var(--surface-raised, #0f172a)",
  color: "var(--text, #f8fafc)",
  font: "inherit",
};
const labelStyle = {
  display: "grid",
  gap: 7,
  color: "var(--text-muted, #cbd5e1)",
  fontSize: 14,
  fontWeight: 600,
} as const;

export function AuthPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const error = useAuthStore((state) => state.error);
  const registering = searchParams.get("mode") === "register";
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string>();
  const [notice, setNotice] = useState<string>();

  function setRegistering(value: boolean) {
    const next = new URLSearchParams(searchParams);
    if (value) next.set("mode", "register");
    else next.delete("mode");
    setSearchParams(next, { replace: true });
    setFormError(undefined);
    setNotice(undefined);
  }

  async function handleRegister(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setFormError(undefined);
    setNotice(undefined);
    const form = event.currentTarget;
    const data = new FormData(form);
    try {
      await apiFetch("/api/v1/auth/register", {
        method: "POST",
        body: JSON.stringify({
          email: data.get("email"),
          firstName: data.get("firstName"),
          lastName: data.get("lastName"),
          phone: data.get("phone") || null,
          password: data.get("password"),
        }),
      });
      setRegistering(false);
      setNotice("Account created. Sign in with your new credentials.");
      form.reset();
    } catch (cause) {
      setFormError(cause instanceof Error ? cause.message : "Account creation failed.");
    } finally {
      setSubmitting(false);
    }
  }

  const buttonStyle = {
    width: "100%",
    padding: "12px 16px",
    borderRadius: 10,
    border: "1px solid #334155",
    font: "inherit",
    fontWeight: 700,
    cursor: "pointer",
  } as const;

  return (
    <section style={{ width: "100%", maxWidth: 536, margin: "0 auto", padding: "32px 16px", color: "var(--text, #f8fafc)" }}>
      <div style={{ border: "1px solid #1e293b", borderRadius: 12, background: "rgba(15,23,42,.85)", padding: 28, boxShadow: "0 25px 50px -12px rgba(0,0,0,.25)" }}>
        <p style={{ margin: "0 0 8px", color: "#94a3b8", fontSize: 14 }}>RACKPAY ACCOUNT</p>
        <h1 style={{ margin: 0, fontSize: 28, fontWeight: 700 }}>{registering ? "Create your account" : "Sign in to RackPay"}</h1>
        <p style={{ color: "#94a3b8", lineHeight: 1.6 }}>{registering ? "Create your account and wallet securely." : "Continue securely with your RackPay account."}</p>

        {notice ? <p role="status" style={{ padding: 12, borderRadius: 8, background: "rgba(6,95,70,.35)", color: "#a7f3d0" }}>{notice}</p> : null}
        {formError || error ? <p role="alert" style={{ padding: 12, borderRadius: 8, background: "rgba(127,29,29,.35)", color: "#fecaca" }}>{formError ?? error}</p> : null}

        {registering ? (
          <form onSubmit={(event) => void handleRegister(event)} style={{ display: "grid", gap: 16 }}>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 12 }}>
              <label style={labelStyle}>First name<input style={inputStyle} name="firstName" autoComplete="given-name" required /></label>
              <label style={labelStyle}>Last name<input style={inputStyle} name="lastName" autoComplete="family-name" required /></label>
            </div>
            <label style={labelStyle}>Email<input style={inputStyle} name="email" type="email" autoComplete="email" required /></label>
            <label style={labelStyle}>Phone (optional)<input style={inputStyle} name="phone" type="tel" autoComplete="tel" /></label>
            <label style={labelStyle}>Password<input style={inputStyle} name="password" type="password" autoComplete="new-password" minLength={12} maxLength={128} required /><span style={{ fontSize: 12, color: "#94a3b8", fontWeight: 400 }}>Use at least 12 characters.</span></label>
            <button type="submit" disabled={submitting} style={{ ...buttonStyle, background: "#fff", color: "#020617", borderColor: "#fff", opacity: submitting ? .65 : 1 }}>{submitting ? "Creating account…" : "Create account"}</button>
            <button type="button" onClick={() => setRegistering(false)} style={{ ...buttonStyle, background: "transparent", color: "#f8fafc" }}>Back to sign in</button>
          </form>
        ) : (
          <div style={{ display: "grid", gap: 12, marginTop: 24 }}>
            <button type="button" onClick={() => void keycloak.login()} style={{ ...buttonStyle, background: "#fff", color: "#020617", borderColor: "#fff" }}>Sign in</button>
            <button type="button" onClick={() => setRegistering(true)} style={{ ...buttonStyle, background: "transparent", color: "#f8fafc" }}>Create account</button>
          </div>
        )}
        <p style={{ margin: "24px 0 0", color: "#64748b", fontSize: 12, lineHeight: 1.6, textAlign: "center" }}>Sign-in is handled securely by Keycloak. Registration is completed on RackPay.</p>
      </div>
    </section>
  );
}
