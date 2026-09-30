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
  const response = await fetch(`${env.apiBaseUrl}${path}`, {
    ...init,
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...init.headers,
    },
  });

  if (response.ok) {
    return response.status === 204 ? (undefined as T) : response.json() as Promise<T>;
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
