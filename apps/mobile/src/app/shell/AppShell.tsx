import { Outlet, useLocation } from "react-router-dom";

export function AppShell() {
  const isLoginRoute = useLocation().pathname === "/login";

  return (
    <div className="min-h-dvh bg-slate-950 text-slate-100">
      <main
        className={`mx-auto min-h-dvh w-full pb-safe pt-safe ${
          isLoginRoute ? "max-w-7xl" : "max-w-md px-5"
        }`}
      >
        <Outlet />
      </main>
    </div>
  );
}
