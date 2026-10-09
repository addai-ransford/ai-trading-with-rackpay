import { Outlet, useLocation } from "react-router-dom";

export function AppShell() {
  const pathname = useLocation().pathname;
  const isLoginRoute = pathname === "/login";
  const isAdminRoute = pathname.startsWith("/admin");

  return (
    <div className="min-h-dvh bg-slate-950 text-slate-100">
      <main
        className={`mx-auto min-h-dvh w-full pb-safe pt-safe ${
          isLoginRoute ? "max-w-7xl" : isAdminRoute ? "max-w-4xl px-5" : "max-w-md px-5"
        }`}
      >
        <Outlet />
      </main>
    </div>
  );
}
