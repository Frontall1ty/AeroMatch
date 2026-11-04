import ApiStatus from "./api-status";

export default function Home() {
  return (
    <main className="landing">
      <h1>AeroMatch</h1>
      <p className="subtitle">Air cargo quote and booking coordination</p>
      {process.env.NODE_ENV === "development" && <ApiStatus />}
    </main>
  );
}
