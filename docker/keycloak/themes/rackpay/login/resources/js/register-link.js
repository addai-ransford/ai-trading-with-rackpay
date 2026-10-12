function initializeRackPayLogin() {
  const page = document.querySelector(".pf-v5-c-login") || document.querySelector(".login-pf-page");
  const container = document.querySelector(".pf-v5-c-login__container");
  const card = document.querySelector(".pf-v5-c-login__main") || document.querySelector("#kc-content");
  const body = document.querySelector(".pf-v5-c-login__main-body") || document.querySelector("#kc-content-wrapper");
  const form = document.querySelector("#kc-form-login");

  if (!page || !card || !body || !form || document.querySelector(".rackpay-auth-layout")) return;

  // Match apps/mobile/src/features/auth/pages/AuthPage.tsx: one centered,
  // 536px account card with the same dark palette, spacing, and white actions.
  const layout = document.createElement("main");
  layout.className = "rackpay-auth-layout";
  const actions = document.createElement("div");
  actions.className = "rackpay-entry-actions";
  actions.innerHTML = `
    <button type="button" class="rackpay-create">Create account</button>
    <p class="rackpay-entry-note">Sign-in is handled securely by Keycloak. Registration is completed on RackPay.</p>`;

  const createButton = actions.querySelector(".rackpay-create");
  createButton.addEventListener("click", () => {
    const params = new URLSearchParams(window.location.search);
    const redirectUri = params.get("redirect_uri");
    let targetOrigin = window.location.origin;
    try {
      if (redirectUri && /^https?:\\/\\//i.test(redirectUri)) targetOrigin = new URL(redirectUri).origin;
    } catch (_) {}
    window.location.assign(new URL("/login?mode=register", targetOrigin).toString());
  });

  const title = document.querySelector("#kc-page-title");
  if (title) title.textContent = "Sign in to RackPay";
  const username = form.querySelector("#username, input[name='username'], input[name='email']");
  if (username) username.setAttribute("autocomplete", "username");
  const password = form.querySelector("#password");
  if (password) password.setAttribute("autocomplete", "current-password");

  // Put the actual Keycloak card directly inside the centered layout. Keep
  // the existing form visible and native so validation and submission work.
  const originalParent = card.parentElement;
  page.appendChild(layout);
  layout.appendChild(card);
  if (originalParent && originalParent !== card && originalParent !== page &&
      originalParent !== container && !originalParent.contains(layout)) {
    originalParent.style.display = "none";
  }

  // Append account links/footnote after the form to mirror the app login card.
  body.appendChild(actions);
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", initializeRackPayLogin, { once: true });
} else {
  initializeRackPayLogin();
}