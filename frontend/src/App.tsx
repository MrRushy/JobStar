import { useEffect, useState } from "react";

function App() {
  const [message, setMessage] = useState("Checking backend...");

  useEffect(() => {
    fetch("http://localhost:8080/api/health")
      .then((response) => response.text())
      .then((data) => setMessage(data))
      .catch(() => setMessage("Could not connect to backend."));
  }, []);

  return (
    <main>
      <h1>JobStar</h1>
      <p>Your guide through the job search.</p>

      <h2>Backend Status</h2>
      <p>{message}</p>
    </main>
  );
}

export default App;