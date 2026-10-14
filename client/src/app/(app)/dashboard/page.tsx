"use client";

/*
 * Dashboard – built in M12. The quick buttons are here already (prototype dashboard.html): Enter bills and Receive
 * payment, each shown only to roles that may use it.
 */
import Link from "next/link";
import { PageActions } from "@/components/layout/PageActions";
import { ComingSoon } from "@/components/shared/ComingSoon";
import { Can } from "@/features/auth/access";
import { P } from "@/lib/permissions";

export default function Page() {
  return (
    <>
      <PageActions>
        <Can permission={P.SALES_EDIT}>
          <Link className="btn primary" href="/sales/enter-bills">
            Enter bills
          </Link>
        </Can>
        <Can permission={P.BILLING_EDIT}>
          <Link className="btn" href="/billing?pay=new">
            Receive payment
          </Link>
        </Can>
      </PageActions>
      <ComingSoon path="/dashboard" />
    </>
  );
}
