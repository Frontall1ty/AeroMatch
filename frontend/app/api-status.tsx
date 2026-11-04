"use client";

import { useEffect, useState } from "react";
import { API_BASE_URL } from "@/lib/api/client";

type Status = "checking" | "reachable" | "unreachable";

export default function ApiStatus() {
  const [status, setStatus] = useState<Status>("checking");

  useEffect(() => {
    const controller = new AbortController();
    fetch(`${API_BASE_URL}/actuator/health`, { signal: controller.signal })
      .then((res) => setStatus(res.ok ? "reachable" : "unreachable"))
      .catch((err: unknown) => {
        if (!(err instanceof DOMException && err.name === "AbortError")) {
          setStatus("unreachable");
        }
      });
    return () => controller.abort();
  }, []);

  const label =
    status === "checking"
      ? "Checking API..."
      : status === "reachable"
        ? "API reachable"
        : "API unreachable";

  return (
    <section className="status" aria-live="polite">
      <h2>Status</h2>
      <p className={`status-line status-${status}`}>
        <span className="dot" aria-hidden="true" />
        {label}
      </p>
    </section>
  );
}
