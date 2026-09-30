import { useEffect, type PropsWithChildren } from "react";
import { keycloak } from "./keycloak";
import { useAuthStore } from "./authStore";

export function AuthBootstrap({ children }: PropsWithChildren) {
  const setAccessToken = useAuthStore((state) => state.setAccessToken);
  const setInitialized = useAuthStore((state) => state.setInitialized);

  useEffect(() => {
    let active = true;

    void keycloak
      .init({
        onLoad: "check-sso",
        pkceMethod: "S256",
        checkLoginIframe: false,
      })
      .then((authenticated) => {
        if (!active) return;
        setAccessToken(authenticated ? keycloak.token : undefined);
        setInitialized(true);
      })
      .catch(() => {
        if (!active) return;
        setAccessToken(undefined);
        setInitialized(true);
      });

    return () => {
      active = false;
    };
  }, [setAccessToken, setInitialized]);

  return children;
}
