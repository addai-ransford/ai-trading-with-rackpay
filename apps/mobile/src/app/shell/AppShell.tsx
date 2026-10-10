import { NavLink, Outlet } from "react-router-dom";
import { ArrowLeftRight, ChartNoAxesCombined, CircleHelp, LayoutDashboard, Settings2, WalletCards } from "lucide-react";

const links = [
  { to: "/", label: "Overview", icon: LayoutDashboard, end: true },
  { to: "/wallet", label: "Wallet", icon: WalletCards },
  { to: "/remittance", label: "Remittance", icon: ArrowLeftRight },
  { to: "/trading", label: "AI Trading", icon: ChartNoAxesCombined },
  { to: "/settings", label: "Settings", icon: Settings2 },
];

export function AppShell() {
  return (
    <div className="app-frame">
      <aside className="desktop-sidebar">
        <NavLink to="/" className="brand-lockup">
          <span className="brand-mark">R</span><span>rack<span className="brand-light">pay</span></span>
        </NavLink>
        <p className="nav-caption">WORKSPACE</p>
        <nav className="side-nav" aria-label="Main navigation">
          {links.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}>
              <Icon size={18} strokeWidth={1.8} /><span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="help-card"><CircleHelp size={18}/><div><strong>Need help?</strong><span>Visit our support centre</span></div></div>
          <div className="secure-note"><span className="secure-dot"/> Secure financial workspace</div>
        </div>
      </aside>
      <main className="main-column">
        <header className="topbar">
          <NavLink to="/" className="mobile-brand"><span className="brand-mark">R</span> rack<span className="brand-light">pay</span></NavLink>
          <span className="topbar-label">PERSONAL WORKSPACE</span>
          <div className="topbar-right"><span className="status-dot"/> <span>Secure session</span></div>
        </header>
        <div className="page-content"><Outlet /></div>
        <nav className="mobile-nav" aria-label="Mobile navigation">
          {links.slice(0, 4).map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} className={({ isActive }) => `mobile-nav-link ${isActive ? "active" : ""}`}>
              <Icon size={20}/><span>{label === "Overview" ? "Home" : label === "Remittance" ? "Send" : label === "AI Trading" ? "Trading" : label}</span>
            </NavLink>
          ))}
        </nav>
      </main>
    </div>
  );
}
