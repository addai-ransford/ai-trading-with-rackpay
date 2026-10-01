function addRegistrationSwitch() {
    const formBody = document.querySelector(".pf-v5-c-login__main-body");
    if (!formBody || formBody.querySelector(".rackpay-registration-switch")) return;

    const redirectUri = new URLSearchParams(window.location.search).get("redirect_uri");
    const appOrigin = redirectUri?.startsWith("http")
        ? new URL(redirectUri).origin
        : "http://localhost:4000";
    const link = document.createElement("a");
    link.href = new URL("/login?mode=register", appOrigin).toString();
    link.textContent = "Create account";

    const switcher = document.createElement("p");
    switcher.className = "rackpay-registration-switch";
    switcher.append("New to RackPay? ", link);
    formBody.append(switcher);
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", addRegistrationSwitch, { once: true });
} else {
    addRegistrationSwitch();
}
