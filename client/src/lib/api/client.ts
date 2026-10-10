/*
 * API client (design 3.3, 7.1). The browser calls /api on the same site; the login is an httpOnly cookie,
 * so every call sends credentials. Errors come back as { code, message, details } (design 5.8).
 *   401 → back to the login page (session ended, e.g. user deactivated)
 *   403 PASSWORD_CHANGE_REQUIRED → the change-password screen
 *   anything else → ApiError; the query client shows its message as a toast unless the screen handles it.
 */

export const API_BASE = "/api";

export const NETWORK_ERROR_MESSAGE = "Cannot reach the server. Check the connection and try again.";
export const PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

export interface ApiErrorBody {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: Record<string, unknown>;

  constructor(status: number, body: ApiErrorBody) {
    super(body.message);
    this.name = "ApiError";
    this.status = status;
    this.code = body.code;
    this.details = body.details ?? {};
  }

  /** Field messages of a 400 VALIDATION error: { fieldName: "message" }. */
  get fieldErrors(): Record<string, string> {
    const fields = this.details.fields;
    return fields && typeof fields === "object" ? (fields as Record<string, string>) : {};
  }
}

type Query = Record<string, string | number | boolean | null | undefined>;

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  /** JSON body, or FormData for a file upload (the browser sets the multipart header). */
  body?: unknown;
  query?: Query;
  signal?: AbortSignal;
}

/** Calls without the automatic redirects (the login page handles its own errors). */
const NO_REDIRECT = new Set(["/auth/login", "/auth/logout"]);

export function buildUrl(path: string, query?: Query): string {
  const qs = new URLSearchParams();
  Object.entries(query ?? {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== "") qs.set(k, String(v));
  });
  const s = qs.toString();
  return API_BASE + path + (s ? "?" + s : "");
}

function redirectTo(target: string) {
  if (typeof window === "undefined") return;
  if (window.location.pathname === target.split("?")[0]) return;
  window.location.assign(target);
}

export function loginUrlForCurrentPage(): string {
  if (typeof window === "undefined") return "/login";
  const here = window.location.pathname + window.location.search;
  return here === "/" || here.startsWith("/login") ? "/login" : "/login?next=" + encodeURIComponent(here);
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, query, signal } = options;
  const isForm = typeof FormData !== "undefined" && body instanceof FormData;
  let response: Response;
  try {
    response = await fetch(buildUrl(path, query), {
      method,
      credentials: "include",
      headers:
        body !== undefined && !isForm
          ? { "Content-Type": "application/json", Accept: "application/json" }
          : { Accept: "application/json" },
      body: isForm ? (body as FormData) : body !== undefined ? JSON.stringify(body) : undefined,
      signal,
      cache: "no-store",
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw new ApiError(0, { code: "NETWORK", message: NETWORK_ERROR_MESSAGE });
  }

  if (response.status === 204) return undefined as T;

  const text = await response.text();
  let data: unknown = undefined;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = undefined;
    }
  }

  if (response.ok) return data as T;

  const errorBody: ApiErrorBody =
    data && typeof data === "object" && "message" in data
      ? (data as ApiErrorBody)
      : { code: "SERVER_ERROR", message: "Something went wrong. Please try again." };
  const error = new ApiError(response.status, errorBody);

  if (!NO_REDIRECT.has(path)) {
    if (response.status === 401) redirectTo(loginUrlForCurrentPage());
    else if (response.status === 403 && error.code === PASSWORD_CHANGE_REQUIRED)
      redirectTo("/change-password");
  }
  throw error;
}

/** True for errors that already sent the user to another page (no toast needed). */
export function isRedirectError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.status === 401 || (error.status === 403 && error.code === PASSWORD_CHANGE_REQUIRED))
  );
}
