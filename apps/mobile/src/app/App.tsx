import { AppRouter } from "./router/AppRouter";
import { AppProviders } from "./providers/AppProviders";
import { AuthBootstrap } from "../shared/auth/AuthBootstrap";

export function App() {
  return (
    <AuthBootstrap>
      <AppProviders>
        <AppRouter />
      </AppProviders>
    </AuthBootstrap>
  );
}
