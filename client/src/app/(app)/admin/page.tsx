"use client";

/*
 * Administration (M01, prototype admin/administration.html): Users · Roles & access · Audit log · Company.
 * Admin only – the shell shows "No access" to other roles and the server refuses their calls.
 */
import { Tabs, useHashTab } from "@/components/shared/Tabs";
import { AuditTab } from "@/features/admin/AuditTab";
import { CompanyTab } from "@/features/admin/CompanyTab";
import { RolesTab } from "@/features/admin/RolesTab";
import { UsersTab } from "@/features/admin/UsersTab";

const TABS = ["users", "roles", "audit", "company"] as const;

export default function AdministrationPage() {
  const [tab, setTab] = useHashTab(TABS);
  return (
    <>
      <Tabs
        tabs={[
          { id: "users", label: "Users" },
          { id: "roles", label: "Roles & access" },
          { id: "audit", label: "Audit log" },
          { id: "company", label: "Company" },
        ]}
        value={tab}
        onChange={setTab}
      />
      {tab === "users" && <UsersTab />}
      {tab === "roles" && <RolesTab />}
      {tab === "audit" && <AuditTab />}
      {tab === "company" && <CompanyTab />}
    </>
  );
}
