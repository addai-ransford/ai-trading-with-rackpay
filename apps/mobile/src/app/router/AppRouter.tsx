import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "../shell/AppShell";
import { HomePage } from "../../features/home/pages/HomePage";
import { AuthPage } from "../../features/auth/pages/AuthPage";
import { WalletPage, RemittancePage, TradingPage, SettingsPage } from "../../features/workspace/WorkspacePages";

export function AppRouter() {
  return <Routes><Route element={<AppShell />}>
    <Route path="login" element={<AuthPage />} />
    <Route index element={<HomePage />} />
    <Route path="wallet" element={<WalletPage />} />
    <Route path="remittance" element={<RemittancePage />} />
    <Route path="trading" element={<TradingPage />} />
    <Route path="settings" element={<SettingsPage />} />
    <Route path="*" element={<Navigate to="/" replace />} />
  </Route></Routes>;
}
