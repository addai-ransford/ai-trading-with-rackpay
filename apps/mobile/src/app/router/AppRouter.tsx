import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "../shell/AppShell";
import { HomePage } from "../../features/home/pages/HomePage";

export function AppRouter() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<HomePage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
