export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export type ApiErrorResponse = {
  code: string;
  message: string;
  correlationId?: string;
  details: Record<string, unknown>;
};
