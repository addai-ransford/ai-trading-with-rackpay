import { Outlet } from "react-router-dom";

export function AppShell() {
  return (
    <div className="min-h-dvh bg-slate-950 text-slate-100">
      <main className="mx-auto min-h-dvh w-full max-w-md px-5 pb-safe pt-safe">
        <Outlet />
      </main>
    </div>
  );
}
