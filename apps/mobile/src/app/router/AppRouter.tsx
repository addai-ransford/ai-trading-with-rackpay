import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "../shell/AppShell";
import { HomePage } from "../../features/home/pages/HomePage";
import { LoginPage } from "../../features/auth/pages/LoginPage";
import { ProtectedRoute } from "../../features/auth/routes/ProtectedRoute";
import { WalletPage } from "../../features/wallet/pages/WalletPage";

export function AppRouter() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route index element={<HomePage />} />
          <Route path="/wallet" element={<WalletPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
