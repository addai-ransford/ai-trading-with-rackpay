import { ArrowLeftRight, CreditCard, LogOut, ShieldCheck, UserCog, WalletCards } from "lucide-react";
import { Link } from "react-router-dom";
import { useAuthStore } from "../../../shared/auth/authStore";
import { logout } from "../../../shared/auth/keycloak";

export function SettingsPage() {
  const user = useAuthStore((state) => state.user);
  return (
    <section>
      <div className="page-heading">
        <div>
          <p className="eyebrow">RACKPAY WORKSPACE</p>
          <h1>Settings</h1>
          <p className="page-description">Account information, security and platform tools.</p>
        </div>
      </div>
      <div className="settings-list">
        <div className="settings-row">
          <span className="settings-icon"><ShieldCheck size={19}/></span>
          <div>
            <strong>Secure authentication</strong>
            <p>Sign-in and session management are handled by Keycloak.</p>
          </div>
          <span className="feature-tag">Protected</span>
        </div>
        <div className="settings-row">
          <span className="settings-icon"><UserCog size={19}/></span>
          <div>
            <strong>Account</strong>
            <p>{user?.name ?? user?.username ?? "RackPay user"}{user?.email ? ` · ${user.email}` : ""}</p>
          </div>
        </div>
      </div>
      <div className="section-heading"><div><h2>Manage RackPay</h2><p>Shortcuts to your account features</p></div></div>
      <div className="settings-list">
        <Link to="/wallet" className="settings-row">
          <span className="settings-icon"><WalletCards size={19}/></span>
          <div><strong>Wallet</strong><p>Currency balances and transaction history</p></div>
        </Link>
        <Link to="/wallet/add-money" className="settings-row">
          <span className="settings-icon"><CreditCard size={19}/></span>
          <div><strong>Add money</strong><p>Start a wallet-funding checkout</p></div>
        </Link>
        <Link to="/remittance" className="settings-row">
          <span className="settings-icon"><ArrowLeftRight size={19}/></span>
          <div><strong>Remittance</strong><p>Recipient, exchange rate and transfer history</p></div>
        </Link>
        <Link to="/admin/payment-providers" className="settings-row">
          <span className="settings-icon"><UserCog size={19}/></span>
          <div><strong>Platform administration</strong><p>Payment providers and administrator accounts. Backend permissions apply.</p></div>
        </Link>
      </div>
      <button type="button" className="button button-secondary" onClick={() => void logout()}>
        <LogOut size={16}/> Sign out
      </button>
      <p className="compliance-note"><ShieldCheck size={14}/> Financial and administrative actions are authorised by the RackPay backend.</p>
    </section>
  );
}
