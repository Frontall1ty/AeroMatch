import ApiStatus from "./api-status";

export default function Home() {
  return (
    <main className="landing">
      <h1>AeroMatch</h1>
      <p className="subtitle">Air cargo quote and booking coordination</p>
      <ApiStatus />
    </main>
  );
}
