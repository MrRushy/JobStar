import { useEffect, useState, type FormEvent } from "react";
import "./App.css";

const API_ROOT = "http://localhost:8080/api";
const APPLICATIONS_URL = `${API_ROOT}/applications`;

type ApplicationStatus = "SAVED" | "APPLIED" | "INTERVIEWING" | "OFFER" | "REJECTED" | "WITHDRAWN";
type JobApplication = { id: number; company: string; position: string; status: ApplicationStatus; location: string | null; jobUrl: string | null; appliedDate: string | null; notes: string | null };
type ApplicationForm = { company: string; position: string; status: ApplicationStatus; location: string; jobUrl: string; appliedDate: string; notes: string };
type Account = { id: number; email: string };
type CredentialsForm = { email: string; password: string };
type CsrfToken = { headerName: string; token: string };
type ApplicationSort = "NEWEST" | "OLDEST" | "COMPANY_ASC" | "COMPANY_DESC";

const emptyForm: ApplicationForm = { company: "", position: "", status: "SAVED", location: "", jobUrl: "", appliedDate: "", notes: "" };
const emptyCredentials: CredentialsForm = { email: "", password: "" };
const statusLabels: Record<ApplicationStatus, string> = { SAVED: "Saved", APPLIED: "Applied", INTERVIEWING: "Interviewing", OFFER: "Offer", REJECTED: "Rejected", WITHDRAWN: "Withdrawn" };

function createFormFromApplication(application: JobApplication): ApplicationForm {
  return { company: application.company, position: application.position, status: application.status, location: application.location ?? "", jobUrl: application.jobUrl ?? "", appliedDate: application.appliedDate ?? "", notes: application.notes ?? "" };
}

function createRequestBody(form: ApplicationForm) {
  return { company: form.company.trim(), position: form.position.trim(), status: form.status, location: form.location.trim() || null, jobUrl: form.jobUrl.trim() || null, appliedDate: form.appliedDate || null, notes: form.notes.trim() || null };
}

function formatAppliedDate(date: string | null) {
  return date ? new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric" }).format(new Date(`${date}T00:00:00`)) : null;
}

async function responseMessage(response: Response, fallback: string) {
  const body = (await response.json().catch(() => null)) as { detail?: string; message?: string } | null;
  return body?.detail ?? body?.message ?? fallback;
}

function App() {
  const [applications, setApplications] = useState<JobApplication[]>([]);
  const [form, setForm] = useState<ApplicationForm>(emptyForm);
  const [credentials, setCredentials] = useState<CredentialsForm>(emptyCredentials);
  const [currentUser, setCurrentUser] = useState<Account | null>(null);
  const [authMode, setAuthMode] = useState<"login" | "register">("register");
  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<ApplicationStatus | "ALL">("ALL");
  const [sort, setSort] = useState<ApplicationSort>("NEWEST");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isAuthenticating, setIsAuthenticating] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [authError, setAuthError] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    async function loadSession() {
      try {
        const response = await fetch(`${API_ROOT}/auth/me`, { credentials: "include", signal: controller.signal });
        if (response.status === 401 || !response.ok) return;
        setCurrentUser((await response.json()) as Account);
        await loadApplications(controller.signal);
      } catch (requestError: unknown) {
        if (requestError instanceof DOMException && requestError.name === "AbortError") return;
      } finally {
        if (!controller.signal.aborted) setIsLoading(false);
      }
    }
    void loadSession();
    return () => controller.abort();
  }, []);

  async function loadApplications(signal?: AbortSignal) {
    const response = await fetch(APPLICATIONS_URL, { credentials: "include", signal });
    if (!response.ok) throw new Error("Could not load applications.");
    setApplications((await response.json()) as JobApplication[]);
  }

  async function csrfHeaders() {
    const response = await fetch(`${API_ROOT}/auth/csrf`, { credentials: "include" });
    if (!response.ok) throw new Error("Could not prepare a secure request.");
    const token = (await response.json()) as CsrfToken;
    return { [token.headerName]: token.token };
  }

  function resetForm() { setForm(emptyForm); setEditingId(null); }

  async function handleAuthentication(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsAuthenticating(true);
    setAuthError("");
    try {
      const response = await fetch(`${API_ROOT}/auth/${authMode}`, { method: "POST", credentials: "include", headers: { "Content-Type": "application/json", ...(await csrfHeaders()) }, body: JSON.stringify(credentials) });
      if (!response.ok) throw new Error(await responseMessage(response, authMode === "register" ? "Could not create your account." : "Could not log you in."));
      setCurrentUser((await response.json()) as Account);
      setCredentials(emptyCredentials);
      await loadApplications();
    } catch (requestError) {
      setAuthError(requestError instanceof Error ? requestError.message : "Could not complete that request.");
    } finally { setIsAuthenticating(false); }
  }

  async function handleLogout() {
    setAuthError("");
    try {
      const response = await fetch(`${API_ROOT}/auth/logout`, { method: "POST", credentials: "include", headers: await csrfHeaders() });
      if (!response.ok) throw new Error(await responseMessage(response, "Could not log you out."));
      setCurrentUser(null); setApplications([]); resetForm();
    } catch (requestError) { setAuthError(requestError instanceof Error ? requestError.message : "Could not log you out."); }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setIsSubmitting(true); setError("");
    const isEditing = editingId !== null;
    try {
      const response = await fetch(isEditing ? `${APPLICATIONS_URL}/${editingId}` : APPLICATIONS_URL, { method: isEditing ? "PUT" : "POST", credentials: "include", headers: { "Content-Type": "application/json", ...(await csrfHeaders()) }, body: JSON.stringify(createRequestBody(form)) });
      if (!response.ok) throw new Error();
      const saved = (await response.json()) as JobApplication;
      setApplications((current) => isEditing ? current.map((application) => application.id === saved.id ? saved : application) : [saved, ...current]);
      resetForm();
    } catch { setError(isEditing ? "Could not update this application. Please try again." : "Could not save this application. Please try again."); }
    finally { setIsSubmitting(false); }
  }

  async function handleDelete(application: JobApplication) {
    if (!window.confirm(`Delete ${application.company} - ${application.position}? This cannot be undone.`)) return;
    setDeletingId(application.id); setError("");
    try {
      const response = await fetch(`${APPLICATIONS_URL}/${application.id}`, { method: "DELETE", credentials: "include", headers: await csrfHeaders() });
      if (!response.ok) throw new Error();
      setApplications((current) => current.filter((item) => item.id !== application.id));
      if (editingId === application.id) resetForm();
    } catch { setError("Could not delete this application. Please try again."); }
    finally { setDeletingId(null); }
  }

  const visibleApplications = applications
    .filter((application) => {
      const matchesSearch = `${application.company} ${application.position}`
        .toLowerCase()
        .includes(searchQuery.trim().toLowerCase());
      const matchesStatus = statusFilter === "ALL" || application.status === statusFilter;
      return matchesSearch && matchesStatus;
    })
    .sort((first, second) => {
      if (sort === "OLDEST") return first.id - second.id;
      if (sort === "COMPANY_ASC") return first.company.localeCompare(second.company);
      if (sort === "COMPANY_DESC") return second.company.localeCompare(first.company);
      return second.id - first.id;
    });
  const summaryStatuses = Object.entries(statusLabels).filter(([, label]) => visibleApplications.some((application) => statusLabels[application.status] === label));
  const statusCounts = Object.fromEntries(
    Object.keys(statusLabels).map((status) => [
      status,
      applications.filter((application) => application.status === status).length,
    ]),
  ) as Record<ApplicationStatus, number>;

  return <main className="app-shell">
    <header className="masthead">
      <p className="eyebrow">Your job search, in focus</p><h1>JobStar</h1>
      <p className="intro">Keep every opportunity organized from first save to final offer.</p>
      {currentUser && <div className="session-bar"><span>Signed in as {currentUser.email}</span><button type="button" className="header-button" onClick={handleLogout}>Log out</button></div>}
    </header>

    {isLoading && <p className="message">Checking your secure session...</p>}
    {!isLoading && !currentUser && <section className="auth-panel" aria-label="Account access">
      <div className="section-heading"><p className="section-number">01</p><div><h2>{authMode === "register" ? "Create your account" : "Welcome back"}</h2><p>{authMode === "register" ? "Your existing applications will be connected to this first account." : "Sign in to access your private application tracker."}</p></div></div>
      <form onSubmit={handleAuthentication}><div className="form-grid">
        <label className="full-width">Email<input type="email" required autoComplete="email" value={credentials.email} onChange={(event) => setCredentials({ ...credentials, email: event.target.value })} placeholder="you@example.com" /></label>
        <label className="full-width">Password<input type="password" required minLength={authMode === "register" ? 12 : undefined} autoComplete={authMode === "register" ? "new-password" : "current-password"} value={credentials.password} onChange={(event) => setCredentials({ ...credentials, password: event.target.value })} placeholder={authMode === "register" ? "At least 12 characters" : "Your password"} /></label>
      </div>{authError && <p className="message error-message">{authError}</p>}<div className="form-actions"><button type="submit" disabled={isAuthenticating}>{isAuthenticating ? authMode === "register" ? "Creating account..." : "Signing in..." : authMode === "register" ? "Create account" : "Sign in"}</button><button type="button" className="secondary-button" onClick={() => { setAuthMode(authMode === "register" ? "login" : "register"); setAuthError(""); }} disabled={isAuthenticating}>{authMode === "register" ? "I already have an account" : "Create a new account"}</button></div></form>
    </section>}

    {!isLoading && currentUser && <section className="dashboard" aria-label="Application dashboard">
      <div className="dashboard-heading"><div><p className="eyebrow">Dashboard</p><h2>Your search at a glance</h2><p>Live totals from every application in your tracker.</p></div><p className="dashboard-note">{applications.length === 0 ? "Add your first opportunity to start building a picture." : "Keep adding applications to make these totals more useful."}</p></div>
      <div className="metric-grid">
        <article className="metric-card metric-card-total"><p>Total tracked</p><strong>{applications.length}</strong><span>Every opportunity</span></article>
        <article className="metric-card"><p>Saved / to apply</p><strong>{statusCounts.SAVED}</strong><span>Ready for your next step</span></article>
        <article className="metric-card"><p>Interviews</p><strong>{statusCounts.INTERVIEWING}</strong><span>Moving forward</span></article>
        <article className="metric-card"><p>Offers</p><strong>{statusCounts.OFFER}</strong><span>Worth celebrating</span></article>
      </div>
      <div className="dashboard-breakdown"><p>Pipeline breakdown</p><div>{Object.entries(statusLabels).map(([status, label]) => <span key={status}>{label}<strong>{statusCounts[status as ApplicationStatus]}</strong></span>)}</div></div>
    </section>}

    {!isLoading && currentUser && <section className="workspace" aria-label="Application tracker">
      <form className="application-form" onSubmit={handleSubmit}>
        <div className="section-heading"><p className="section-number">02</p><div><h2>{editingId === null ? "Add an opportunity" : "Update an opportunity"}</h2><p>{editingId === null ? "Start with the details you know. You can refine it later." : "Make changes here, then save them back to your tracker."}</p></div></div>
        <div className="form-grid">
          <label>Company<input required value={form.company} onChange={(event) => setForm({ ...form, company: event.target.value })} placeholder="Acme Inc." /></label>
          <label>Position<input required value={form.position} onChange={(event) => setForm({ ...form, position: event.target.value })} placeholder="Junior Software Developer" /></label>
          <label>Status<select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value as ApplicationStatus })}>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
          <label>Date applied<input type="date" value={form.appliedDate} onChange={(event) => setForm({ ...form, appliedDate: event.target.value })} /></label>
          <label>Location<input value={form.location} onChange={(event) => setForm({ ...form, location: event.target.value })} placeholder="New York, NY" /></label>
          <label>Job link<input type="url" value={form.jobUrl} onChange={(event) => setForm({ ...form, jobUrl: event.target.value })} placeholder="https://..." /></label>
          <label className="full-width">Notes<textarea value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} placeholder="What makes this role interesting?" rows={4} /></label>
        </div>
        <div className="form-actions"><button type="submit" disabled={isSubmitting}>{isSubmitting ? editingId === null ? "Saving..." : "Updating..." : editingId === null ? "Save application" : "Update application"}</button>{editingId !== null && <button type="button" className="secondary-button" onClick={resetForm} disabled={isSubmitting}>Cancel edit</button>}</div>
      </form>
      <section className="application-list" aria-live="polite">
        <div className="section-heading list-heading"><p className="section-number">03</p><div><h2>Your opportunities</h2><p>{visibleApplications.length} of {applications.length} shown</p></div></div>
        {applications.length > 0 && <div className="list-controls">
          <label className="search-control">Search<input value={searchQuery} onChange={(event) => setSearchQuery(event.target.value)} placeholder="Company or position" /></label>
          <label>Status<select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as ApplicationStatus | "ALL")}><option value="ALL">All statuses</option>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
          <label>Sort<select value={sort} onChange={(event) => setSort(event.target.value as ApplicationSort)}><option value="NEWEST">Newest added</option><option value="OLDEST">Oldest added</option><option value="COMPANY_ASC">Company A-Z</option><option value="COMPANY_DESC">Company Z-A</option></select></label>
        </div>}
        {visibleApplications.length > 0 && <div className="summary-row" aria-label="Application status summary">{summaryStatuses.map(([status, label]) => <p className="summary-pill" key={status}><span>{label}</span><strong>{visibleApplications.filter((application) => application.status === status).length}</strong></p>)}</div>}
        {error && <p className="message error-message">{error}</p>}{applications.length === 0 && <p className="message empty-message">Your saved applications will appear here.</p>}
        {applications.length > 0 && visibleApplications.length === 0 && <p className="message empty-message">No applications match these controls.</p>}
        {visibleApplications.length > 0 && <div className="cards">{visibleApplications.map((application) => { const appliedDate = formatAppliedDate(application.appliedDate); return <article className="application-card" key={application.id}><div className="card-content"><div><p className="company">{application.company}</p><h3>{application.position}</h3>{(application.location || appliedDate) && <p className="details">{[application.location, appliedDate].filter(Boolean).join(" | ")}</p>}</div>{application.notes && <p className="notes">{application.notes}</p>}{application.jobUrl && <p className="link-row"><a href={application.jobUrl} target="_blank" rel="noreferrer">View posting</a></p>}</div><div className="card-side"><span className={`status status-${application.status.toLowerCase()}`}>{statusLabels[application.status]}</span><div className="card-actions"><button type="button" className="ghost-button" onClick={() => { setError(""); setEditingId(application.id); setForm(createFormFromApplication(application)); }} disabled={isSubmitting || deletingId === application.id}>Edit</button><button type="button" className="ghost-button danger-button" onClick={() => handleDelete(application)} disabled={deletingId === application.id}>{deletingId === application.id ? "Deleting..." : "Delete"}</button></div></div></article>; })}</div>}
      </section>
    </section>}
  </main>;
}

export default App;
