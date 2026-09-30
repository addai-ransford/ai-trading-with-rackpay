import { env } from "../config/env";

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
    readonly correlationId?: string,
    readonly details?: Record<string, unknown>,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

type ApiErrorPayload = {
  code?: string;
  message?: string;
  correlationId?: string;
  details?: Record<string, unknown>;
};

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
  accessToken?: string,
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  headers.set("Content-Type", "application/json");

  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  const response = await fetch(`${env.apiBaseUrl}${path}`, {
    ...init,
    headers,
  });

  if (response.ok) {
    return response.status === 204
      ? (undefined as T)
      : (await response.json()) as T;
  }

  const payload = (await response.json().catch(() => ({}))) as ApiErrorPayload;

  throw new ApiError(
    payload.message ?? "The request could not be completed.",
    response.status,
    payload.code,
    payload.correlationId,
    payload.details,
  );
}
