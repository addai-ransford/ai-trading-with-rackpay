import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "../shell/AppShell";
import { HomePage } from "../../features/home/pages/HomePage";
import { LoginPage } from "../../features/auth/pages/LoginPage";
import { ProtectedRoute } from "../../features/auth/routes/ProtectedRoute";

export function AppRouter() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route index element={<HomePage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
