import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuthStore } from "../../../shared/auth/authStore";

export function ProtectedRoute() {
  const initialized = useAuthStore((state) => state.initialized);
  const authenticated = useAuthStore((state) => state.authenticated);
  const location = useLocation();

  if (!initialized) {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-slate-950 text-slate-300">
        <p className="text-sm">Checking your session…</p>
      </div>
    );
  }

  if (!authenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <Outlet />;
}
