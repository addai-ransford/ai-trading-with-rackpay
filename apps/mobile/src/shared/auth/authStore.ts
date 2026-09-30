import { create } from "zustand";

type AuthState = {
  accessToken?: string;
  initialized: boolean;
  setAccessToken: (accessToken?: string) => void;
  setInitialized: (initialized: boolean) => void;
};

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: undefined,
  initialized: false,
  setAccessToken: (accessToken) => set({ accessToken }),
  setInitialized: (initialized) => set({ initialized }),
}));
