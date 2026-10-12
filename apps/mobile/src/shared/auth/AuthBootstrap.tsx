import { useEffect, type PropsWithChildren } from "react";
import { keycloak } from "./keycloak";
import { useAuthStore } from "./authStore";

// React StrictMode can mount effects twice in development. Keep Keycloak's
// one-time initialization outside the component lifecycle.
let initialization: Promise<boolean> | undefined;

function initializeKeycloak(): Promise<boolean> {
  initialization ??= keycloak.init({
    onLoad: "check-sso",
    pkceMethod: "S256",
    checkLoginIframe: false,
  });
  return initialization;
}

export function AuthBootstrap({ children }: PropsWithChildren) {
  const setAccessToken = useAuthStore((state) => state.setAccessToken);
  const setInitialized = useAuthStore((state) => state.setInitialized);
  const setError = useAuthStore((state) => state.setError);

  useEffect(() => {
    let active = true;

    keycloak.onAuthSuccess = () => {
      if (!active) return;
      setAccessToken(keycloak.token);
      setError(undefined);
    };

    keycloak.onAuthLogout = () => {
      if (!active) return;
      setAccessToken(undefined);
    };

    keycloak.onAuthRefreshSuccess = () => {
      if (!active) return;
      setAccessToken(keycloak.token);
      setError(undefined);
    };

    keycloak.onAuthRefreshError = () => {
      if (!active) return;
      setAccessToken(undefined);
      setError("Your session could not be refreshed. Please sign in again.");
    };

    keycloak.onTokenExpired = () => {
      void keycloak.updateToken(30)
        .then((refreshed) => {
          if (!active) return;
          if (refreshed || keycloak.authenticated) {
            setAccessToken(keycloak.token);
            setError(undefined);
          } else {
            setAccessToken(undefined);
          }
        })
        .catch(() => {
          if (!active) return;
          setAccessToken(undefined);
          setError("Your session expired. Please sign in again.");
        });
    };

    void initializeKeycloak()
      .then((authenticated) => {
        if (!active) return;
        setAccessToken(authenticated ? keycloak.token : undefined);
        setError(undefined);
        setInitialized(true);
      })
      .catch(() => {
        if (!active) return;
        setAccessToken(undefined);
        setError("Unable to initialize secure sign-in. Check the Keycloak configuration and try again.");
        setInitialized(true);
      });

    return () => {
      active = false;
    };
  }, [setAccessToken, setInitialized, setError]);

  return children;
}
