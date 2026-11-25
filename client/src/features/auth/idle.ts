"use client";

/*
 * Log out after a time without use (UAT finding 29). Office users (Admin, Accountant): 30 minutes. Delivery staff use
 * the phone through a delivery round, so they get 4 hours. Activity in any open tab of the app keeps every tab signed
 * in (the last activity time is shared through localStorage). The server login still ends after 12 hours.
 */
import { useEffect } from "react";
import { useLogout } from "./api";

export const IDLE_MINUTES = { office: 30, delivery: 240 } as const;
const KEY = "tw-last-activity";
const EVENTS = ["mousedown", "keydown", "touchstart", "scroll", "wheel"] as const;

export function idleMinutesFor(role: string): number {
  return role === "DELIVERY_STAFF" ? IDLE_MINUTES.delivery : IDLE_MINUTES.office;
}

function readLast(fallback: number): number {
  try {
    const v = Number(window.localStorage.getItem(KEY));
    return Number.isFinite(v) && v > 0 ? Math.max(v, fallback) : fallback;
  } catch {
    return fallback;
  }
}

export function useIdleLogout(role: string) {
  const logout = useLogout();
  const { mutate } = logout;
  useEffect(() => {
    const limit = idleMinutesFor(role) * 60_000;
    let last = Date.now();
    let lastWrite = 0;
    const touch = () => {
      last = Date.now();
      // Write at most every 15 s – enough for the other tabs, cheap on scroll.
      if (last - lastWrite > 15_000) {
        lastWrite = last;
        try {
          window.localStorage.setItem(KEY, String(last));
        } catch {
          /* private window – this tab still keeps its own time */
        }
      }
    };
    touch();
    EVENTS.forEach((e) => window.addEventListener(e, touch, { passive: true }));
    const timer = window.setInterval(() => {
      if (Date.now() - readLast(last) >= limit) {
        window.clearInterval(timer);
        // A full page load clears every cached screen of this user (intended, as Log out in the top bar).
        // eslint-disable-next-line @next/next/no-location-assign-relative-destination
        const done = () => window.location.assign(`/login?idle=${idleMinutesFor(role)}`);
        mutate(undefined, { onSettled: done });
      }
    }, 30_000);
    return () => {
      window.clearInterval(timer);
      EVENTS.forEach((e) => window.removeEventListener(e, touch));
    };
  }, [role, mutate]);
}
