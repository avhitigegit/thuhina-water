"use client";

/*
 * Administration (M01, prototype admin/administration.html): Users · Roles & access · Audit log · Company, and on local /
 * UAT the Demo data generator (M12 – only when the server offers it).
 * Admin only – the shell shows "No access" to other roles and the server refuses their calls.
 */
import { Tabs, useHashTab } from "@/components/shared/Tabs";
import { AuditTab } from "@/features/admin/AuditTab";
import { CompanyTab } from "@/features/admin/CompanyTab";
import { DemoDataTab } from "@/features/admin/DemoDataTab";
import { RolesTab } from "@/features/admin/RolesTab";
import { UsersTab } from "@/features/admin/UsersTab";
import { useCan } from "@/features/auth/access";
import { useDemoDataStatus } from "@/features/reports/api";
import { P } from "@/lib/permissions";

const TABS = ["users", "roles", "audit", "company", "demo"] as const;

export default function AdministrationPage() {
  const [tab, setTab] = useHashTab(TABS);
  const demo = useDemoDataStatus(useCan(P.ADMIN_MANAGE));
  return (
    <>
      <Tabs
        tabs={[
          { id: "users", label: "Users" },
          { id: "roles", label: "Roles & access" },
          { id: "audit", label: "Audit log" },
          { id: "company", label: "Company" },
          ...(demo.data ? [{ id: "demo" as const, label: "Demo data" }] : []),
        ]}
        value={tab}
        onChange={setTab}
      />
      {tab === "users" && <UsersTab />}
      {tab === "roles" && <RolesTab />}
      {tab === "audit" && <AuditTab />}
      {tab === "company" && <CompanyTab />}
      {tab === "demo" && demo.data && <DemoDataTab status={demo.data} />}
    </>
  );
}
