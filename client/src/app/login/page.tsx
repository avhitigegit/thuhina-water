"use client";

/*
 * Log in (FR-56) – as prototype login.html, without the demo-user list. No public signup.
 * Messages come from the server: "Invalid username or password.", "This account is deactivated. Contact the Admin.",
 * "Too many attempts. Try again in 15 minutes."
 */
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";
import { afterLoginPath } from "@/features/auth/landing";
import { useLogin } from "@/features/auth/api";
import { ApiError } from "@/lib/api/client";

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const login = useLogin();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const un = username.trim();
    if (!un || !password) {
      setError("Enter your username and password.");
      return;
    }
    setError("");
    login.mutate(
      { username: un, password },
      {
        onSuccess: (me) => router.replace(afterLoginPath(me, params.get("next"))),
        onError: (err) => {
          setPassword("");
          setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
        },
      },
    );
  }

  return (
    <form onSubmit={onSubmit} autoComplete="on" noValidate>
      <div className="field" style={{ marginBottom: 10 }}>
        <label htmlFor="username">Username</label>
        <input
          id="username"
          name="username"
          autoComplete="username"
          placeholder="e.g. nimal.admin"
          autoFocus
          value={username}
          onChange={(e) => setUsername(e.target.value)}
        />
      </div>
      <div className="field" style={{ marginBottom: 10 }}>
        <label htmlFor="password">Password</label>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </div>
      {error && (
        <div className="banner bad" role="alert">
          {error}
        </div>
      )}
      <button
        className="btn primary"
        style={{ width: "100%", justifyContent: "center", padding: 9 }}
        type="submit"
        disabled={login.isPending}
      >
        {login.isPending ? "Logging in…" : "Log in"}
      </button>
    </form>
  );
}

export default function LoginPage() {
  return (
    <div className="login-page">
      <div className="login-card">
        <div className="logo">T</div>
        <h1 style={{ marginBottom: 2 }}>Thuhina Water</h1>
        <p className="muted">Inventory &amp; Sales Management System</p>
        <Suspense>
          <LoginForm />
        </Suspense>
        <p className="small muted" style={{ margin: "12px 0 4px" }}>
          There is no public signup. Users are created by the Admin under <b>Administration › Users</b>.
        </p>
      </div>
    </div>
  );
}
