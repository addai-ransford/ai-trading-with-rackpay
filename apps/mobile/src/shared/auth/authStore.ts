import { create } from "zustand";

export type AuthUser = {
  subject?: string;
  username?: string;
  name?: string;
  email?: string;
};

type AuthState = {
  accessToken?: string;
  initialized: boolean;
  authenticated: boolean;
  user?: AuthUser;
  error?: string;
  setSession: (accessToken: string | undefined, user: AuthUser | undefined) => void;
  setInitialized: (initialized: boolean) => void;
  setError: (error?: string) => void;
  clearSession: () => void;
};

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: undefined,
  initialized: false,
  authenticated: false,
  user: undefined,
  error: undefined,
  setSession: (accessToken, user) =>
    set({
      accessToken,
      authenticated: Boolean(accessToken),
      user,
      error: undefined,
    }),
  setInitialized: (initialized) => set({ initialized }),
  setError: (error) => set({ error }),
  clearSession: () =>
    set({
      accessToken: undefined,
      authenticated: false,
      user: undefined,
    }),
}));
