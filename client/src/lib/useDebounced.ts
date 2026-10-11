"use client";

import { useEffect, useState } from "react";

/** The value after it has stopped changing for `ms` (live previews and searches do not call the server on every key). */
export function useDebounced<T>(value: T, ms = 300): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}
