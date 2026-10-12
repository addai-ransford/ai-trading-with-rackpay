function initializeRackPayLogin() {
  const page = document.querySelector(".pf-v5-c-login") || document.querySelector(".login-pf-page");
  const container = document.querySelector(".pf-v5-c-login__container");
  const card = document.querySelector(".pf-v5-c-login__main") || document.querySelector("#kc-content");
  const body = document.querySelector(".pf-v5-c-login__main-body");
  const form = document.querySelector("#kc-form-login");

  if (!page || !container || !card || !body || !form || document.querySelector(".rackpay-auth-layout")) return;

  const layout = document.createElement("main");
  layout.className = "rackpay-auth-layout";
  const hero = document.createElement("section");
  hero.className = "rackpay-hero";
  hero.setAttribute("aria-label", "About RackPay");
  hero.innerHTML = `
    <div class="rackpay-brand" aria-label="RackPay"><span class="rackpay-brand-mark">R</span><span class="rackpay-brand-name">rack <span>pay</span></span></div>
    <p class="rackpay-eyebrow">YOUR FINANCIAL SPACE</p>
    <h1>Money management that moves with you.</h1>
    <p class="rackpay-hero-copy">Access your wallet, international transfers and trading controls from one secure workspace.</p>
    <div class="rackpay-feature-list">
      <div class="rackpay-feature"><span class="rackpay-feature-icon" aria-hidden="true">♧</span><div><strong>Designed around security</strong><p>Authentication and permissions are verified by RackPay and Keycloak.</p></div></div>
      <div class="rackpay-feature"><span class="rackpay-feature-icon" aria-hidden="true">♙</span><div><strong>One account, one workspace</strong><p>Your account and wallet are created together during registration.</p></div></div>
    </div>`;

  const actions = document.createElement("div");
  actions.className = "rackpay-entry-actions";
  actions.innerHTML = `
    <button type="button" class="rackpay-continue">Continue to secure sign in <span class="rackpay-arrow" aria-hidden="true">→</span></button>
    <button type="button" class="rackpay-create">Create account</button>
    <p class="rackpay-entry-note">Use your RackPay account credentials to sign in securely. Administrator access follows the roles configured for your account.</p>
    <a class="rackpay-home-link" href="/">Back to RackPay home</a>`;

  // Hide the form only, never its wrapper/card. The real Keycloak form is
  // revealed by the primary action and remains the submitted authentication form.
  form.hidden = true;
  form.setAttribute("aria-hidden", "true");

  const continueButton = actions.querySelector(".rackpay-continue");
  continueButton.addEventListener("click", () => {
    actions.remove();
    form.hidden = false;
    form.removeAttribute("aria-hidden");
    const username = form.querySelector("#username, input[name='username'], input[name='email']");
    if (username) username.focus();
  });

  actions.querySelector(".rackpay-create").addEventListener("click", () => {
    const params = new URLSearchParams(window.location.search);
    const redirectUri = params.get("redirect_uri");
    let targetOrigin = window.location.origin;
    try {
      if (redirectUri && /^https?:\/\//i.test(redirectUri)) targetOrigin = new URL(redirectUri).origin;
    } catch (_) {}
    window.location.assign(new URL("/login?mode=register", targetOrigin).toString());
  });

  body.appendChild(actions);

  // Move the Keycloak card into a full-width custom grid. Do not move the
  // container into its own descendant (the source of the previous DOM error).
  const originalParent = card.parentElement;
  page.appendChild(layout);
  layout.appendChild(hero);
  layout.appendChild(card);
  if (originalParent && originalParent !== card && originalParent !== page && originalParent !== container) {
    originalParent.style.display = "none";
  }
  const title = document.querySelector("#kc-page-title");
  if (title) title.textContent = "Sign in";
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", initializeRackPayLogin, { once: true });
} else {
  initializeRackPayLogin();
}