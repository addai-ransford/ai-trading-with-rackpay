import { useEffect, type PropsWithChildren } from "react";
import { keycloak } from "./keycloak";
import { useAuthStore, type AuthUser } from "./authStore";

function readUser(): AuthUser | undefined {
  const parsed = keycloak.tokenParsed;
  if (!parsed) return undefined;

  return {
    subject: parsed.sub,
    username: parsed.preferred_username,
    name: parsed.name,
    email: parsed.email,
  };
}

export function AuthBootstrap({ children }: PropsWithChildren) {
  const initialized = useAuthStore((state) => state.initialized);
  const setSession = useAuthStore((state) => state.setSession);
  const setInitialized = useAuthStore((state) => state.setInitialized);
  const setError = useAuthStore((state) => state.setError);
  const clearSession = useAuthStore((state) => state.clearSession);

  useEffect(() => {
    let active = true;

    const syncSession = () => {
      if (!active) return;
      setSession(keycloak.token, readUser());
    };

    keycloak.onAuthSuccess = syncSession;
    keycloak.onAuthRefreshSuccess = syncSession;
    keycloak.onAuthLogout = clearSession;
    keycloak.onTokenExpired = () => {
      void keycloak.updateToken(30).then(syncSession).catch(() => {
        clearSession();
      });
    };

    void keycloak
      .init({
        onLoad: "check-sso",
        pkceMethod: "S256",
        checkLoginIframe: false,
      })
      .then((authenticated) => {
        if (!active) return;
        if (authenticated) {
          syncSession();
        } else {
          clearSession();
        }
        setInitialized(true);
      })
      .catch((error: unknown) => {
        if (!active) return;
        clearSession();
        setError(
          error instanceof Error
            ? error.message
            : "Authentication could not be initialized.",
        );
        setInitialized(true);
      });

    const refreshTimer = window.setInterval(() => {
      if (!active || !keycloak.authenticated) return;
      void keycloak.updateToken(30).then(syncSession).catch(() => {
        clearSession();
      });
    }, 20_000);

    return () => {
      active = false;
      window.clearInterval(refreshTimer);
      keycloak.onAuthSuccess = undefined;
      keycloak.onAuthRefreshSuccess = undefined;
      keycloak.onAuthLogout = undefined;
      keycloak.onTokenExpired = undefined;
    };
  }, [clearSession, setError, setInitialized, setSession]);

  if (!initialized) {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-slate-950 text-slate-300">
        <p className="text-sm">Starting RackPay…</p>
      </div>
    );
  }

  return children;
}
