function addRegistrationSwitch() {
    const formBody = document.querySelector(".pf-v5-c-login__main-body");
    if (!formBody || formBody.querySelector(".rackpay-registration-switch")) return;

    const link = document.createElement("a");
    link.href = "http://localhost:5173/login?mode=register";
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
