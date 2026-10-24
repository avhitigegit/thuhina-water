"use client";

/* "/" → the role's landing page (Admin / Accountant → Dashboard, Delivery Staff → Daily Delivery List), or login. */
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useMe } from "@/features/auth/api";
import { afterLoginPath } from "@/features/auth/landing";

export default function Home() {
  const router = useRouter();
  const me = useMe();

  useEffect(() => {
    if (me.data) router.replace(afterLoginPath(me.data, null));
    else if (me.isError) router.replace("/login");
  }, [me.data, me.isError, router]);

  return <div className="loading-page">Loading…</div>;
}
