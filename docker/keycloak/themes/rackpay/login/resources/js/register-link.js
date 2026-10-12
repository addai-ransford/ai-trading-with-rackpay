function initializeRackPayLogin() {
  const page = document.querySelector(".pf-v5-c-login") || document.querySelector(".login-pf-page");
  const container = document.querySelector(".pf-v5-c-login__container");
  const card = document.querySelector(".pf-v5-c-login__main") || document.querySelector("#kc-content");
  const body = document.querySelector(".pf-v5-c-login__main-body") || document.querySelector("#kc-content-wrapper");
  const form = document.querySelector("#kc-form-login");
  const header = document.querySelector(".pf-v5-c-login__main-header") || card?.querySelector("#kc-page-title")?.parentElement;

  if (!page || !card || !body || !form || document.querySelector(".rackpay-auth-layout")) return;

  // Keep the same centered account card and input styling as the RackPay app.
  const layout = document.createElement("main");
  layout.className = "rackpay-auth-layout";

  const toggle = document.createElement("nav");
  toggle.className = "rackpay-auth-toggle";
  toggle.setAttribute("aria-label", "Account access");
  const signIn = document.createElement("a");
  signIn.href = window.location.href;
  signIn.textContent = "Sign in";
  signIn.setAttribute("aria-current", "page");
  const createAccount = document.createElement("a");
  createAccount.href = getRegistrationUrl();
  createAccount.textContent = "Create account";
  toggle.append(signIn, createAccount);

  function getRegistrationUrl() {
    const params = new URLSearchParams(window.location.search);
    const redirectUri = params.get("redirect_uri");
    let targetOrigin = window.location.origin;
    try {
      if (redirectUri && /^https?:\/\//i.test(redirectUri)) {
        targetOrigin = new URL(redirectUri).origin;
      }
    } catch (_) {}
    return new URL("/login?mode=register", targetOrigin).toString();
  }

  const title = document.querySelector("#kc-page-title");
  if (title) title.textContent = "Sign in to RackPay";
  const username = form.querySelector("#username, input[name='username'], input[name='email']");
  if (username) username.setAttribute("autocomplete", "username");
  const password = form.querySelector("#password");
  if (password) password.setAttribute("autocomplete", "current-password");

  // Move only the Keycloak card; keep its native form, errors and submission.
  const originalParent = card.parentElement;
  page.appendChild(layout);
  layout.appendChild(card);
  if (originalParent && originalParent !== card && originalParent !== page &&
      originalParent !== container && !originalParent.contains(layout)) {
    originalParent.style.display = "none";
  }

  // The switch sits above the card heading, just like a mode toggle.
  if (header) header.insertBefore(toggle, header.firstChild);
  else card.insertBefore(toggle, card.firstChild);

  const note = document.createElement("p");
  note.className = "rackpay-entry-note";
  note.textContent = "Sign-in is handled securely by Keycloak. Registration is completed on RackPay.";
  body.appendChild(note);
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", initializeRackPayLogin, { once: true });
} else {
  initializeRackPayLogin();
}