import { create } from "zustand";

type AuthState = {
  accessToken?: string;
  initialized: boolean;
  error?: string;
  setAccessToken: (accessToken?: string) => void;
  setInitialized: (initialized: boolean) => void;
  setError: (error?: string) => void;
};

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: undefined,
  initialized: false,
  error: undefined,
  setAccessToken: (accessToken) => set({ accessToken }),
  setInitialized: (initialized) => set({ initialized }),
  setError: (error) => set({ error }),
}));
