import { useEffect, useState, type FormEvent } from "react";
import "./App.css";

const API_URL = "http://localhost:8080/api/applications";

type ApplicationStatus =
  | "SAVED"
  | "APPLIED"
  | "INTERVIEWING"
  | "OFFER"
  | "REJECTED"
  | "WITHDRAWN";

type JobApplication = {
  id: number;
  company: string;
  position: string;
  status: ApplicationStatus;
  location: string | null;
  jobUrl: string | null;
  appliedDate: string | null;
  notes: string | null;
};

type ApplicationForm = {
  company: string;
  position: string;
  status: ApplicationStatus;
  location: string;
  jobUrl: string;
  appliedDate: string;
  notes: string;
};

const emptyForm: ApplicationForm = {
  company: "",
  position: "",
  status: "SAVED",
  location: "",
  jobUrl: "",
  appliedDate: "",
  notes: "",
};

const statusLabels: Record<ApplicationStatus, string> = {
  SAVED: "Saved",
  APPLIED: "Applied",
  INTERVIEWING: "Interviewing",
  OFFER: "Offer",
  REJECTED: "Rejected",
  WITHDRAWN: "Withdrawn",
};

function App() {
  const [applications, setApplications] = useState<JobApplication[]>([]);
  const [form, setForm] = useState<ApplicationForm>(emptyForm);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let isCurrent = true;

    fetch(API_URL)
      .then((response) => {
        if (!response.ok) {
          throw new Error("Could not load applications.");
        }
        return response.json() as Promise<JobApplication[]>;
      })
      .then((data) => {
        if (isCurrent) {
          setApplications(data);
        }
      })
      .catch(() => {
        if (isCurrent) {
          setError("Could not reach the backend. Make sure it is running on port 8080.");
        }
      })
      .finally(() => {
        if (isCurrent) {
          setIsLoading(false);
        }
      });

    return () => {
      isCurrent = false;
    };
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSubmitting(true);
    setError("");

    try {
      const response = await fetch(API_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ...form,
          appliedDate: form.appliedDate || null,
        }),
      });

      if (!response.ok) {
        throw new Error("Could not create the application.");
      }

      const createdApplication: JobApplication = await response.json();
      setApplications((currentApplications) => [createdApplication, ...currentApplications]);
      setForm(emptyForm);
    } catch {
      setError("Could not save this application. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="app-shell">
      <header className="masthead">
        <p className="eyebrow">Your job search, in focus</p>
        <h1>JobStar</h1>
        <p className="intro">Keep every opportunity organized from first save to final offer.</p>
      </header>

      <section className="workspace" aria-label="Application tracker">
        <form className="application-form" onSubmit={handleSubmit}>
          <div className="section-heading">
            <p className="section-number">01</p>
            <div>
              <h2>Add an opportunity</h2>
              <p>Start with the details you know. You can refine it later.</p>
            </div>
          </div>

          <div className="form-grid">
            <label>
              Company
              <input
                required
                value={form.company}
                onChange={(event) => setForm({ ...form, company: event.target.value })}
                placeholder="Acme Inc."
              />
            </label>
            <label>
              Position
              <input
                required
                value={form.position}
                onChange={(event) => setForm({ ...form, position: event.target.value })}
                placeholder="Junior Software Developer"
              />
            </label>
            <label>
              Status
              <select
                value={form.status}
                onChange={(event) =>
                  setForm({ ...form, status: event.target.value as ApplicationStatus })
                }
              >
                {Object.entries(statusLabels).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Date applied
              <input
                type="date"
                value={form.appliedDate}
                onChange={(event) => setForm({ ...form, appliedDate: event.target.value })}
              />
            </label>
            <label>
              Location
              <input
                value={form.location}
                onChange={(event) => setForm({ ...form, location: event.target.value })}
                placeholder="New York, NY"
              />
            </label>
            <label>
              Job link
              <input
                type="url"
                value={form.jobUrl}
                onChange={(event) => setForm({ ...form, jobUrl: event.target.value })}
                placeholder="https://..."
              />
            </label>
            <label className="full-width">
              Notes
              <textarea
                value={form.notes}
                onChange={(event) => setForm({ ...form, notes: event.target.value })}
                placeholder="What makes this role interesting?"
                rows={3}
              />
            </label>
          </div>

          <button type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Saving..." : "Save application"}
          </button>
        </form>

        <section className="application-list" aria-live="polite">
          <div className="section-heading list-heading">
            <p className="section-number">02</p>
            <div>
              <h2>Your opportunities</h2>
              <p>{applications.length} tracked so far</p>
            </div>
          </div>

          {error && <p className="message error-message">{error}</p>}
          {isLoading && <p className="message">Loading applications...</p>}
          {!isLoading && !error && applications.length === 0 && (
            <p className="message empty-message">Your saved applications will appear here.</p>
          )}
          {!isLoading && applications.length > 0 && (
            <div className="cards">
              {applications.map((application) => (
                <article className="application-card" key={application.id}>
                  <div>
                    <p className="company">{application.company}</p>
                    <h3>{application.position}</h3>
                    {(application.location || application.appliedDate) && (
                      <p className="details">
                        {[application.location, application.appliedDate].filter(Boolean).join(" | ")}
                      </p>
                    )}
                  </div>
                  <span className={`status status-${application.status.toLowerCase()}`}>
                    {statusLabels[application.status]}
                  </span>
                </article>
              ))}
            </div>
          )}
        </section>
      </section>
    </main>
  );
}

export default App;
