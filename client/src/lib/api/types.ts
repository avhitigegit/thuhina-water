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

// Administration (M01)
export type UserResponse = Schemas["UserResponse"];
export type CreateUserRequest = Schemas["CreateUserRequest"];
export type UpdateUserRequest = Schemas["UpdateUserRequest"];
export type ResetPasswordResponse = Schemas["ResetPasswordResponse"];
export type RoleCode = CreateUserRequest["role"];
export type RolesMatrixResponse = Schemas["RolesMatrixResponse"];
export type AccessCell = Schemas["Cell"];
export type AuditEntry = Schemas["AuditEntryResponse"];
export type AuditPage = Schemas["PageResponseAuditEntryResponse"];
export type AuditFilterOptions = Schemas["AuditFilterOptions"];

/** GET/PUT /settings/company – a JSON object on the server, so typed here (design 4.3 app_setting "company"). */
export interface CompanySettings {
  name: string;
  address: string | null;
  phone: string | null;
  email: string | null;
  regNo: string | null;
  /** Stored file key; set only by the logo upload. */
  logo: string | null;
  /** Address of the logo image (cache-safe), or null. */
  logoUrl: string | null;
}

// Master data – Bottles & Products (M02)
export type BottleType = Schemas["BottleTypeResponse"];
export type CreateBottleTypeRequest = Schemas["CreateBottleTypeRequest"];
export type UpdateBottleTypeRequest = Schemas["UpdateBottleTypeRequest"];
export type Product = Schemas["ProductResponse"];
export type CreateProductRequest = Schemas["CreateProductRequest"];
export type UpdateProductRequest = Schemas["UpdateProductRequest"];
export type CustomerType = Schemas["CustomerTypeResponse"];
export type Area = Schemas["AreaResponse"];
export type OldBottleBrand = Schemas["OldBottleBrandResponse"];
export type CreateOldBottleBrandRequest = Schemas["CreateOldBottleBrandRequest"];
export type UpdateOldBottleBrandRequest = Schemas["UpdateOldBottleBrandRequest"];
export type PriceValue = Schemas["PriceValue"];
/** `current` / `next` are null when there is no such price (springdoc drops "nullable" on object references). */
export type PriceCell = Omit<Schemas["PriceCell"], "current" | "next"> & {
  current: PriceValue | null;
  next: PriceValue | null;
};
export type PriceMatrix = Omit<Schemas["PriceMatrixResponse"], "water" | "deposits"> & {
  water: PriceCell[];
  deposits: PriceCell[];
};
export type PriceHistoryRow = Schemas["PriceHistoryRow"];
export type SetPriceRequest = Schemas["SetPriceRequest"];
