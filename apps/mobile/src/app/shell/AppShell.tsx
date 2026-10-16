import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { useAuthStore } from "../../shared/auth/authStore";
import {
  ChartNoAxesCombined,
  CircleHelp,
  CircleUserRound,
  ChevronDown,
  LayoutDashboard,
  LogOut,
  Settings2,
  WalletCards,
  ArrowLeftRight,
  Sun,
  Moon,
} from "lucide-react";
import { useTheme } from "../../shared/theme/ThemeProvider";
import { logout } from "../../shared/auth/keycloak";

const links = [
  { to: "/", label: "Overview", icon: LayoutDashboard, end: true },
  { to: "/wallet", label: "Wallet", icon: WalletCards },
  { to: "/remittance", label: "Send money", icon: ArrowLeftRight },
  { to: "/trading", label: "AI Trading", icon: ChartNoAxesCombined },
  { to: "/settings", label: "Settings", icon: Settings2 },
];

export function AppShell() {
  const { pathname } = useLocation();
  const authenticated = useAuthStore((state) => state.authenticated);
  const user = useAuthStore((state) => state.user);
  const { theme, toggleTheme } = useTheme();
  const isLoginRoute = pathname === "/login";
  const isGuestPage = !authenticated;
  return (
    <div className="app-frame">
      {!isLoginRoute ? (
        <aside className="desktop-sidebar">
          <NavLink to="/" className="brand-lockup" aria-label="RackPay overview">
            <span className="brand-mark">R</span>
            <span>rack<span className="brand-light">pay</span></span>
          </NavLink>
          {isGuestPage ? (
            <div className="guest-sidebar-note">
              <span className="secure-dot" />
              <strong>Your money, connected.</strong>
              <p>Wallets, international transfers and risk-controlled trading in one secure space.</p>
              <Link to="/login" className="button button-primary full-width">Sign in or create account</Link>
              <Link to="/rates" className="button button-secondary full-width">Explore exchange rates</Link>
            </div>
          ) : (
            <>
              <p className="nav-caption">WORKSPACE</p>
              <nav className="side-nav" aria-label="Main navigation">
                {links.map(({ to, label, icon: Icon, end }) => (
                  <NavLink key={to} to={to} end={end} className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}>
                    <Icon size={18} strokeWidth={1.8} /><span>{label}</span>
                  </NavLink>
                ))}
              </nav>
            </>
          )}
          <div className="sidebar-bottom">
            <div className="help-card"><CircleHelp size={18}/><div><strong>Need help?</strong><span>Contact RackPay support</span></div></div>
            <div className="secure-note"><span className="secure-dot"/> Secure financial workspace</div>
          </div>
        </aside>
      ) : null}
      <main className={`main-column ${isLoginRoute ? "login-column" : ""}`}>
        {!isLoginRoute ? (
          <header className="topbar">
            <NavLink to="/" className="mobile-brand"><span className="brand-mark">R</span> rack<span className="brand-light">pay</span></NavLink>
            <span className="topbar-label">{authenticated ? "PERSONAL WORKSPACE" : "EXPLORE RACKPAY"}</span>
            <div className="topbar-right">
              <button type="button" className="theme-toggle" onClick={toggleTheme} aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} theme`} title={`Switch to ${theme === "dark" ? "light" : "dark"} theme`}>
                {theme === "dark" ? <Sun size={17} /> : <Moon size={17} />}
                <span>{theme === "dark" ? "Light" : "Dark"}</span>
              </button>
              {authenticated ? (
                <details className="profile-menu" onBlur={(event) => {
                  const nextTarget = event.relatedTarget as Node | null;
                  if (!nextTarget || !event.currentTarget.contains(nextTarget)) event.currentTarget.open = false;
                }}>
                  <summary className="profile-trigger" aria-label="Open profile menu">
                    <span className="profile-avatar">{(user?.name ?? user?.username ?? "U").trim().split(/\s+/).slice(0, 2).map((part) => part[0]?.toUpperCase()).join("")}</span>
                    <span className="profile-trigger-copy"><strong>{user?.name ?? user?.username ?? "My account"}</strong><small>Profile</small></span>
                    <ChevronDown size={15} className="profile-chevron" />
                  </summary>
                  <div className="profile-menu-panel">
                    <div className="profile-menu-identity">
                      <strong>{user?.name ?? user?.username ?? "RackPay user"}</strong>
                      {user?.email ? <span>{user.email}</span> : null}
                    </div>
                    <Link to="/settings" className="profile-menu-item" onClick={(event) => event.currentTarget.closest("details")?.removeAttribute("open")}>
                      <Settings2 size={16} /> Settings
                    </Link>
                    <button type="button" className="profile-menu-item profile-menu-signout" onClick={() => void logout()}>
                      <LogOut size={16} /> Sign out
                    </button>
                  </div>
                </details>
              ) : (
                <Link to="/login" className="account-entry" aria-label="Sign in or create account">
                  <CircleUserRound size={19} /><span>Sign in</span>
                </Link>
              )}
            </div>
          </header>
        ) : null}
        <div className={isLoginRoute ? "login-content" : "page-content"}><Outlet /></div>
        {!isLoginRoute && !isGuestPage ? (
          <nav className="mobile-nav" aria-label="Mobile navigation">
            {links.filter(({ to }) => ["/", "/wallet", "/remittance", "/trading"].includes(to)).map(({ to, label, icon: Icon, end }) => (
              <NavLink key={to} to={to} end={end} className={({ isActive }) => `mobile-nav-link ${isActive ? "active" : ""}`}>
                <Icon size={20}/><span>{label === "Overview" ? "Home" : label === "Send money" ? "Send" : label === "AI Trading" ? "Trading" : label}</span>
              </NavLink>
            ))}
          </nav>
        ) : null}
      </main>
    </div>
  );
}
