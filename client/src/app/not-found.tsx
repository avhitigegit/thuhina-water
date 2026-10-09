import Link from "next/link";

export default function NotFound() {
  return (
    <div className="login-page">
      <div className="login-card">
        <h2>Page not found</h2>
        <p className="muted">The address does not match any screen.</p>
        <Link className="btn primary" href="/">
          Go to my home page
        </Link>
      </div>
    </div>
  );
}
