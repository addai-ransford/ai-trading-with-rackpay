function required(name: keyof ImportMetaEnv): string {
  const value = import.meta.env[name];
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

function optional(name: keyof ImportMetaEnv): string | undefined {
  return import.meta.env[name] || undefined;
}

export const env = {
  apiBaseUrl: required("VITE_API_BASE_URL").replace(/\/$/, ""),
  keycloak: {
    url: required("VITE_KEYCLOAK_URL").replace(/\/$/, ""),
    realm: required("VITE_KEYCLOAK_REALM"),
    clientId: required("VITE_KEYCLOAK_CLIENT_ID"),
    redirectUri: optional("VITE_KEYCLOAK_REDIRECT_URI"),
  },
  paymentWebhookUrl: optional("VITE_PAYMENT_WEBHOOK_URL"),
} as const;
