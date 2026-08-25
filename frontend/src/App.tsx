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

function createFormFromApplication(application: JobApplication): ApplicationForm {
  return {
    company: application.company,
    position: application.position,
    status: application.status,
    location: application.location ?? "",
    jobUrl: application.jobUrl ?? "",
    appliedDate: application.appliedDate ?? "",
    notes: application.notes ?? "",
  };
}

function createRequestBody(form: ApplicationForm) {
  return {
    company: form.company.trim(),
    position: form.position.trim(),
    status: form.status,
    location: form.location.trim() || null,
    jobUrl: form.jobUrl.trim() || null,
    appliedDate: form.appliedDate || null,
    notes: form.notes.trim() || null,
  };
}

function formatAppliedDate(appliedDate: string | null) {
  if (!appliedDate) {
    return null;
  }

  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(new Date(`${appliedDate}T00:00:00`));
}

function App() {
  const [applications, setApplications] = useState<JobApplication[]>([]);
  const [form, setForm] = useState<ApplicationForm>(emptyForm);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    const controller = new AbortController();

    fetch(API_URL, { signal: controller.signal })
      .then((response) => {
        if (!response.ok) {
          throw new Error("Could not load applications.");
        }
        return response.json() as Promise<JobApplication[]>;
      })
      .then((data) => {
        setApplications(data);
      })
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === "AbortError") {
          return;
        }

        setError("Could not reach the backend. Make sure it is running on port 8080.");
      })
      .finally(() => {
        setIsLoading(false);
      });

    return () => {
      controller.abort();
    };
  }, []);

  function resetForm() {
    setForm(emptyForm);
    setEditingId(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSubmitting(true);
    setError("");

    const isEditing = editingId !== null;
    const requestUrl = isEditing ? `${API_URL}/${editingId}` : API_URL;
    const requestMethod = isEditing ? "PUT" : "POST";

    try {
      const response = await fetch(requestUrl, {
        method: requestMethod,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(createRequestBody(form)),
      });

      if (!response.ok) {
        throw new Error(isEditing ? "Could not update the application." : "Could not create the application.");
      }

      const savedApplication: JobApplication = await response.json();
      setApplications((currentApplications) => {
        if (isEditing) {
          return currentApplications.map((application) =>
            application.id === savedApplication.id ? savedApplication : application,
          );
        }

        return [savedApplication, ...currentApplications];
      });
      resetForm();
    } catch {
      setError(
        isEditing
          ? "Could not update this application. Please try again."
          : "Could not save this application. Please try again.",
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleEditStart(application: JobApplication) {
    setError("");
    setEditingId(application.id);
    setForm(createFormFromApplication(application));
  }

  async function handleDelete(application: JobApplication) {
    const shouldDelete = window.confirm(
      `Delete ${application.company} - ${application.position}? This cannot be undone.`,
    );

    if (!shouldDelete) {
      return;
    }

    setDeletingId(application.id);
    setError("");

    try {
      const response = await fetch(`${API_URL}/${application.id}`, {
        method: "DELETE",
      });

      if (!response.ok) {
        throw new Error("Could not delete the application.");
      }

      setApplications((currentApplications) =>
        currentApplications.filter((currentApplication) => currentApplication.id !== application.id),
      );

      if (editingId === application.id) {
        resetForm();
      }
    } catch {
      setError("Could not delete this application. Please try again.");
    } finally {
      setDeletingId(null);
    }
  }

  const summaryStatuses = Object.entries(statusLabels).filter(([, label]) =>
    applications.some((application) => statusLabels[application.status] === label),
  );

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
              <h2>{editingId === null ? "Add an opportunity" : "Update an opportunity"}</h2>
              <p>
                {editingId === null
                  ? "Start with the details you know. You can refine it later."
                  : "Make changes here, then save them back to your tracker."}
              </p>
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
                rows={4}
              />
            </label>
          </div>

          <div className="form-actions">
            <button type="submit" disabled={isSubmitting}>
              {isSubmitting
                ? editingId === null
                  ? "Saving..."
                  : "Updating..."
                : editingId === null
                  ? "Save application"
                  : "Update application"}
            </button>
            {editingId !== null && (
              <button
                type="button"
                className="secondary-button"
                onClick={resetForm}
                disabled={isSubmitting}
              >
                Cancel edit
              </button>
            )}
          </div>
        </form>

        <section className="application-list" aria-live="polite">
          <div className="section-heading list-heading">
            <p className="section-number">02</p>
            <div>
              <h2>Your opportunities</h2>
              <p>{applications.length} tracked so far</p>
            </div>
          </div>

          {applications.length > 0 && (
            <div className="summary-row" aria-label="Application status summary">
              {summaryStatuses.map(([status, label]) => (
                <p className="summary-pill" key={status}>
                  <span>{label}</span>
                  <strong>
                    {
                      applications.filter((application) => application.status === status).length
                    }
                  </strong>
                </p>
              ))}
            </div>
          )}

          {error && <p className="message error-message">{error}</p>}
          {isLoading && <p className="message">Loading applications...</p>}
          {!isLoading && !error && applications.length === 0 && (
            <p className="message empty-message">Your saved applications will appear here.</p>
          )}
          {!isLoading && applications.length > 0 && (
            <div className="cards">
              {applications.map((application) => {
                const appliedDate = formatAppliedDate(application.appliedDate);

                return (
                  <article className="application-card" key={application.id}>
                    <div className="card-content">
                      <div>
                        <p className="company">{application.company}</p>
                        <h3>{application.position}</h3>
                        {(application.location || appliedDate) && (
                          <p className="details">
                            {[application.location, appliedDate].filter(Boolean).join(" | ")}
                          </p>
                        )}
                      </div>

                      {application.notes && <p className="notes">{application.notes}</p>}

                      {application.jobUrl && (
                        <p className="link-row">
                          <a href={application.jobUrl} target="_blank" rel="noreferrer">
                            View posting
                          </a>
                        </p>
                      )}
                    </div>

                    <div className="card-side">
                      <span className={`status status-${application.status.toLowerCase()}`}>
                        {statusLabels[application.status]}
                      </span>

                      <div className="card-actions">
                        <button
                          type="button"
                          className="ghost-button"
                          onClick={() => handleEditStart(application)}
                          disabled={isSubmitting || deletingId === application.id}
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          className="ghost-button danger-button"
                          onClick={() => handleDelete(application)}
                          disabled={deletingId === application.id}
                        >
                          {deletingId === application.id ? "Deleting..." : "Delete"}
                        </button>
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </section>
      </section>
    </main>
  );
}

export default App;
