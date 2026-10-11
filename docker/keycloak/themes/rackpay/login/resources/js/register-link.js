function initializeRackPayLogin() {
    const keycloakContainer = document.querySelector(".pf-v5-c-login__container");
    const card = document.querySelector(".pf-v5-c-login__main");
    const formBody = document.querySelector(".pf-v5-c-login__main-body");
    const loginForm = document.querySelector("#kc-form-login");
    if (!keycloakContainer || !card || !formBody || !loginForm || document.querySelector(".rackpay-auth-layout")) return;

    const layout = document.createElement("div");
    layout.className = "rackpay-auth-layout";

    const hero = document.createElement("section");
    hero.className = "rackpay-hero";
    hero.setAttribute("aria-label", "About RackPay");
    hero.innerHTML = `
      <div class="rackpay-brand" aria-label="RackPay">
        <span class="rackpay-brand-mark">R</span>
        <span class="rackpay-brand-name">rack <span>pay</span></span>
      </div>
      <p class="rackpay-eyebrow">YOUR FINANCIAL SPACE</p>
      <h1>Money management that moves with you.</h1>
      <p class="rackpay-hero-copy">Access your wallet, international transfers and trading controls from one secure workspace.</p>
      <div class="rackpay-feature-list">
        <div class="rackpay-feature">
          <span class="rackpay-feature-icon" aria-hidden="true">♧</span>
          <div><strong>Designed around security</strong><p>Authentication and permissions are verified by RackPay and Keycloak.</p></div>
        </div>
        <div class="rackpay-feature">
          <span class="rackpay-feature-icon" aria-hidden="true">♙</span>
          <div><strong>One account, one workspace</strong><p>Your account and wallet are created together during registration.</p></div>
        </div>
      </div>`;

    const actions = document.createElement("div");
    actions.className = "rackpay-entry-actions";
    actions.innerHTML = `
      <button type="button" class="rackpay-continue">Continue to secure sign in <span class="rackpay-arrow" aria-hidden="true">→</span></button>
      <button type="button" class="rackpay-create">Create account</button>
      <p class="rackpay-entry-note">Bootstrap administrator? Use the bootstrap email and password configured for your environment, then choose Continue to secure sign in.</p>
      <a class="rackpay-home-link" href="http://localhost:4000/">Back to RackPay home</a>`;

    const formWrapper = loginForm.closest("#kc-form") || loginForm.parentElement;
    const options = document.querySelector("#kc-form-options");
    const buttons = document.querySelector("#kc-form-buttons");
    const info = document.querySelector("#kc-info");
    const initialHidden = [loginForm, formWrapper && formWrapper !== loginForm ? formWrapper : null, options, buttons, info]
      .filter((element, index, array) => element && array.indexOf(element) === index);
    initialHidden.forEach(element => {
      element.dataset.rackpayDisplay = element.style.display || "";
      element.style.display = "none";
    });

    const registrationUrl = () => {
      const redirectUri = new URLSearchParams(window.location.search).get("redirect_uri");
      let origin = "http://localhost:4000";
      try {
        if (redirectUri && /^https?:\/\//i.test(redirectUri)) origin = new URL(redirectUri).origin;
      } catch (_) {}
      return new URL("/login?mode=register", origin).toString();
    };

    actions.querySelector(".rackpay-continue").addEventListener("click", () => {
      actions.remove();
      initialHidden.forEach(element => { element.style.display = element.dataset.rackpayDisplay || ""; });
      const username = document.querySelector("#username, input[name='username'], input[name='email']");
      if (username) username.focus();
    });
    actions.querySelector(".rackpay-create").addEventListener("click", () => {
      window.location.assign(registrationUrl());
    });

    formBody.append(actions);
    layout.append(hero, keycloakContainer);
    keycloakContainer.parentElement.insertBefore(layout, keycloakContainer);
    layout.appendChild(keycloakContainer);

    const title = document.querySelector("#kc-page-title");
    if (title) title.textContent = "Sign in";
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initializeRackPayLogin, { once: true });
} else {
    initializeRackPayLogin();
}