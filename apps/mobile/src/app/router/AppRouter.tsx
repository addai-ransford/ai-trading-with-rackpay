import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "../shell/AppShell";
import { HomePage } from "../../features/home/pages/HomePage";
import { LoginPage } from "../../features/auth/pages/LoginPage";
import { ProtectedRoute } from "../../features/auth/routes/ProtectedRoute";
import { WalletPage } from "../../features/wallet/pages/WalletPage";
import { RemittancePage } from "../../features/remittance/pages/RemittancePage";
import { RemittanceHistoryPage } from "../../features/remittance/pages/RemittanceHistoryPage";
import { PaymentPage } from "../../features/payments/pages/PaymentPage";
import { TradingPage } from "../../features/trading/pages/TradingPage";
import { PaymentProviderAdminPage } from "../../features/admin/pages/PaymentProviderAdminPage";
import { AdminUserAdminPage } from "../../features/admin/pages/AdminUserAdminPage";
import { SettingsPage } from "../../features/settings/pages/SettingsPage";

export function AppRouter() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route index element={<HomePage />} />
          <Route path="/wallet" element={<WalletPage />} />
          <Route path="/wallet/add-money" element={<PaymentPage />} />
          <Route path="/remittance" element={<RemittancePage />} />
          <Route path="/remittance/history" element={<RemittanceHistoryPage />} />
          <Route path="/remittance/history/:remittanceId" element={<RemittanceHistoryPage />} />
          <Route path="/trading" element={<TradingPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/admin/payment-providers" element={<PaymentProviderAdminPage />} />
          <Route path="/admin/admins/new" element={<AdminUserAdminPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
