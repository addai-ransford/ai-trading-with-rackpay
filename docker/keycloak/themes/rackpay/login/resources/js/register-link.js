function initializeRackPayLogin() {
  if (document.querySelector(".rackpay-auth-page")) return;

  const card = document.querySelector(".pf-v5-c-login__main") || document.querySelector("#kc-content") || document.querySelector(".login-pf-page") || document.querySelector("form");
  if (!card) return;

  // Prefer the app origin carried in Keycloak's registered redirect_uri.
  // This avoids guessing production hostnames (for example, auth -> app).
  function getAppUrl() {
    if (typeof window.RACKPAY_APP_URL === "string" && /^https?:\/\//i.test(window.RACKPAY_APP_URL)) {
      return new URL(window.RACKPAY_APP_URL).origin;
    }

    const params = new URLSearchParams(window.location.search);
    const redirectUri = params.get("redirect_uri");
    if (redirectUri) {
      try {
        const candidate = new URL(redirectUri);
        if (candidate.protocol === "http:" || candidate.protocol === "https:") {
          return candidate.origin;
        }
      } catch (_) {
        // Ignore malformed redirect_uri and use the safe fallback below.
      }
    }

    // Local development uses the Vite app on port 4000 while Keycloak has
    // its own origin. In production, configure RACKPAY_APP_URL or pass
    // redirect_uri from the client.
    if (window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1") {
      return window.location.protocol + "//" + window.location.hostname + ":4000";
    }
    return window.location.origin;
  }

  const appUrl = getAppUrl();
  const signInUrl = new URL("/login", appUrl).toString();
  const registerUrl = new URL("/login?mode=register", appUrl).toString();
  const isRegistering = false;

  document.body.innerHTML = "";
  
  const layout = document.createElement("main");
  layout.className = "rackpay-auth-page";

  const intro = document.createElement("div");
  intro.className = "auth-intro";
  intro.innerHTML = `
    <a href="${appUrl}" class="auth-brand" aria-label="Back to RackPay home">
      <span class="brand-mark">R</span> rack<span class="brand-light">pay</span>
    </a>
    <p class="eyebrow">YOUR FINANCIAL SPACE</p>
    <h1>Money management that moves with you.</h1>
    <p>Access your wallet, international transfers and trading controls from one secure workspace.</p>
    <div class="auth-promise">
      <span class="brand-light-bg">
        <svg xmlns="http://www.w3.org/2000/svg" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/><path d="m9 12 2 2 4-4"/></svg>
      </span>
      <div>
        <strong>Designed around security</strong>
        <small>Authentication and permissions are verified by RackPay and Keycloak.</small>
      </div>
    </div>
    <div class="auth-promise">
      <span class="brand-light-bg">
        <svg xmlns="http://www.w3.org/2000/svg" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="16" r="1"/><rect x="3" y="10" width="18" height="12" rx="2"/><path d="M7 10V7a5 5 0 0 1 10 0v3"/></svg>
      </span>
      <div>
        <strong>One account, one workspace</strong>
        <small>Your account and wallet are created together during registration.</small>
      </div>
    </div>
  `;

  card.className = "auth-card";

  const nativeTitle = card.querySelector("#kc-page-title");
  if (nativeTitle) nativeTitle.style.display = "none";
  const nativeHeader = card.querySelector(".pf-v5-c-login__main-header");
  if (nativeHeader) nativeHeader.style.display = "none";

  let cardHeading = card.querySelector(".auth-card-heading");
  if (!cardHeading) {
    cardHeading = document.createElement("div");
    cardHeading.className = "auth-card-heading";
    cardHeading.innerHTML = `
      <p class="eyebrow">${isRegistering ? "GET STARTED" : "WELCOME BACK"}</p>
      <p class="auth-card-sub">${isRegistering ? "Create your account and wallet securely." : "Sign in to continue to your account."}</p>
    `;
    card.insertBefore(cardHeading, card.firstChild);
  }

  // Modernize password field structure by wrapping password input + toggle button together
  const passwordInput = card.querySelector("input[type='password']");
  if (passwordInput) {
    const parentGroup = passwordInput.closest(".pf-v5-c-form-group") || passwordInput.parentElement;
    const toggleBtn = card.querySelector("button[aria-label*='password']") || card.querySelector(".pf-v5-c-button.pf-m-control") || card.querySelector("button.pf-v5-c-button");
    
    if (toggleBtn && parentGroup) {
      let customWrap = parentGroup.querySelector(".custom-password-wrap");
      if (!customWrap) {
        customWrap = document.createElement("div");
        customWrap.className = "custom-password-wrap";
        passwordInput.parentNode.insertBefore(customWrap, passwordInput);
        customWrap.appendChild(passwordInput);
        customWrap.appendChild(toggleBtn);
      }
    }
  }

  const formArea = card.querySelector("form") || card.querySelector("#kc-form-login");
  let actionsArea = card.querySelector(".auth-actions");
  
  if (!actionsArea && formArea) {
    actionsArea = document.createElement("div");
    actionsArea.className = "auth-actions toggle-buttons";

    const submitBtn = card.querySelector("#kc-login") || card.querySelector("input[type='submit']") || card.querySelector("button[type='submit']");
    if(submitBtn) {
      submitBtn.className = "auth-btn-primary";
      if (!isRegistering) {
        submitBtn.value = "";
        submitBtn.innerHTML = `Submit<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>`;
      } 
      actionsArea.appendChild(submitBtn);
    }

    const secondaryBtn = document.createElement("button");
    secondaryBtn.type = "button";
    secondaryBtn.className = "auth-btn-secondary";
    if (isRegistering) {
      secondaryBtn.textContent = "Back to sign in";
      secondaryBtn.addEventListener("click", () => {
        window.location.href = signInUrl;
      });
    }

    formArea.appendChild(actionsArea);
  }

  const footnoteText = isRegistering 
    ? "Your password is set securely through RackPay's identity service." 
    : "Bootstrap administrator? Use the bootstrap email and password configured for your environment, then choose Log in to secure your session.";
  
  let footnote = card.querySelector(".auth-footnote");
  if (!footnote) {
    footnote = document.createElement("p");
    footnote.className = "auth-footnote";
    card.appendChild(footnote);
  }
  footnote.textContent = footnoteText;

  let createAccountLink = card.querySelector(".auth-back-home");
  if (!createAccountLink) {
    createAccountLink = document.createElement("a");
    createAccountLink.className = "auth-back-home";
    card.appendChild(createAccountLink);
  }
  createAccountLink.href = registerUrl;
  createAccountLink.textContent = "Create account";

  document.body.appendChild(layout);
  layout.appendChild(intro);
  layout.appendChild(card);
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", initializeRackPayLogin, { once: true });
} else {
  initializeRackPayLogin();
}