import type { ReactNode } from "react";
import { AppShell } from "@/components/layout/AppShell";

/** Every page in this folder needs a login; the shell checks the role may open the page. */
export default function ProtectedLayout({ children }: { children: ReactNode }) {
  return <AppShell>{children}</AppShell>;
}
