"use client";

/*
 * The company logo from Administration › Company (on a white tile, as the logo has a white background), or the
 * "T" badge while there is none. Used on the login and change-password pages and in the menu.
 */
import { useBranding } from "@/features/admin/api";

export function BrandLogo() {
  const branding = useBranding();
  const url = branding.data?.logoUrl;
  if (url) {
    // eslint-disable-next-line @next/next/no-img-element -- served by the API, size set in CSS
    return <img className="logo img" src={url} alt="" />;
  }
  return <div className="logo">T</div>;
}

/** The company name from Administration › Company, or "Thuhina Water" until it has loaded. */
export function useCompanyName() {
  return useBranding().data?.name || "Thuhina Water";
}
