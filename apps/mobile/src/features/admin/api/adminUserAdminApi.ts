import { apiFetch } from "../../../shared/api/httpClient";

export type CreateAdminRequest = {
  email: string;
  firstName: string;
  lastName: string;
  phone?: string;
  password: string;
};

export type CreatedAdmin = {
  userId: string;
  email: string;
  admin: boolean;
  bootstrapAdmin: boolean;
};

export function createAdministrator(accessToken: string, request: CreateAdminRequest) {
  return apiFetch<CreatedAdmin>(
    "/api/v1/admin/admins",
    { method: "POST", body: JSON.stringify(request) },
    accessToken,
  );
}
