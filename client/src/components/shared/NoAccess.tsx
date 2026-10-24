import Link from "next/link";

/** Shown when the role may not open a page (prototype "No access" card). */
export function NoAccess({
  roleName,
  pageTitle,
  home,
}: {
  roleName: string;
  pageTitle: string;
  home: string;
}) {
  return (
    <div className="card no-access">
      <h2>No access</h2>
      <p>
        The <b>{roleName}</b> role cannot open <b>{pageTitle}</b>.
      </p>
      <p>
        <Link className="btn primary" href={home}>
          Go to my home page
        </Link>
      </p>
    </div>
  );
}
