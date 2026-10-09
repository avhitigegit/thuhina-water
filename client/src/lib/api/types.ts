/*
 * API types, generated from the server's OpenAPI description (design 14):
 *   start the API (local profile), then `npm run api:types` → src/lib/api/schema.d.ts (do not edit that file).
 * Name the types each feature uses here.
 */
import type { components } from "./schema";

type Schemas = components["schemas"];

export type MeResponse = Schemas["MeResponse"];
export type UserInfo = Schemas["UserInfo"];
export type MenuModule = Schemas["MenuModule"];
export type MenuItem = Schemas["MenuItem"];
export type LoginRequest = Schemas["LoginRequest"];
export type ChangePasswordRequest = Schemas["ChangePasswordRequest"];
