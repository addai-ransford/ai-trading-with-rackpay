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

  // Brand the left side while preserving Keycloak's native form, errors and submission.
  const hero = document.createElement("section");
  hero.className = "rackpay-hero";
  hero.setAttribute("aria-label", "About RackPay");
  hero.innerHTML = `
    <p class="rackpay-hero-eyebrow"><span class="rackpay-hero-mark" aria-hidden="true">R</span> One account. More possibilities.</p>
    <h1>Your money, moving with <span>purpose.</span></h1>
    <p class="rackpay-hero-copy">A clearer way to manage your money. Keep track of your wallet, send funds across borders, and explore AI-assisted trading with limits you control.</p>
    <ul class="rackpay-hero-points">
      <li><span class="rackpay-hero-check" aria-hidden="true">✓</span><span><strong>Wallet in one place</strong><br>Keep your money and currency balances organized.</span></li>
      <li><span class="rackpay-hero-check" aria-hidden="true">✓</span><span><strong>Transfers made simpler</strong><br>Send money internationally with a clear, guided flow.</span></li>
      <li><span class="rackpay-hero-check" aria-hidden="true">✓</span><span><strong>Trading with boundaries</strong><br>Set the maximum amount your AI-assisted strategy can use.</span></li>
    </ul>
    <p class="rackpay-hero-footnote">Built to help you stay in control of your financial choices.</p>
  `;

  // Move only the Keycloak card; keep its native form, errors and submission.
  const originalParent = card.parentElement;
  page.appendChild(layout);
  layout.appendChild(hero);
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