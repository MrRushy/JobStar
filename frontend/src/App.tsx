import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import "./App.css";

const API_ROOT = "http://localhost:8080/api";
const APPLICATIONS_URL = `${API_ROOT}/applications`;
const FOLLOW_UPS_URL = `${API_ROOT}/follow-ups`;

type ApplicationStatus = "SAVED" | "APPLIED" | "INTERVIEWING" | "OFFER" | "REJECTED" | "WITHDRAWN";
type InterviewType = "PHONE" | "VIDEO" | "ONSITE" | "TECHNICAL" | "OTHER";
type JobApplication = { id: number; company: string; position: string; status: ApplicationStatus; location: string | null; jobUrl: string | null; appliedDate: string | null; notes: string | null; jobDescription: string | null };
type ApplicationForm = { company: string; position: string; status: ApplicationStatus; location: string; jobUrl: string; appliedDate: string; notes: string; jobDescription: string };
type Interview = { id: number; scheduledAt: string; type: InterviewType; interviewer: string | null; notes: string | null };
type InterviewForm = { scheduledAt: string; type: InterviewType; interviewer: string; notes: string };
type Contact = { id: number; name: string; role: string | null; email: string | null; profileUrl: string | null; notes: string | null };
type ContactForm = { name: string; role: string; email: string; profileUrl: string; notes: string };
type ResumeVersion = { id: number; label: string; documentUrl: string | null; notes: string | null };
type ResumeVersionForm = { label: string; documentUrl: string; notes: string };
type FollowUpReminder = { id: number; dueDate: string; description: string; completed: boolean; contact: Contact | null; application: JobApplication };
type FollowUpForm = { dueDate: string; description: string; completed: boolean; contactId: string };
type Account = { id: number; email: string };
type CredentialsForm = { email: string; password: string };
type CsrfToken = { headerName: string; token: string };
type ApplicationSort = "NEWEST" | "OLDEST" | "COMPANY_ASC" | "COMPANY_DESC";

const emptyForm: ApplicationForm = { company: "", position: "", status: "SAVED", location: "", jobUrl: "", appliedDate: "", notes: "", jobDescription: "" };
const emptyInterviewForm: InterviewForm = { scheduledAt: "", type: "VIDEO", interviewer: "", notes: "" };
const emptyContactForm: ContactForm = { name: "", role: "", email: "", profileUrl: "", notes: "" };
const emptyResumeVersionForm: ResumeVersionForm = { label: "", documentUrl: "", notes: "" };
const emptyFollowUpForm: FollowUpForm = { dueDate: "", description: "", completed: false, contactId: "" };
const emptyCredentials: CredentialsForm = { email: "", password: "" };
const statusLabels: Record<ApplicationStatus, string> = { SAVED: "Saved", APPLIED: "Applied", INTERVIEWING: "Interviewing", OFFER: "Offer", REJECTED: "Rejected", WITHDRAWN: "Withdrawn" };
const interviewTypeLabels: Record<InterviewType, string> = { PHONE: "Phone", VIDEO: "Video", ONSITE: "Onsite", TECHNICAL: "Technical", OTHER: "Other" };

function createFormFromApplication(application: JobApplication): ApplicationForm {
  return { company: application.company, position: application.position, status: application.status, location: application.location ?? "", jobUrl: application.jobUrl ?? "", appliedDate: application.appliedDate ?? "", notes: application.notes ?? "", jobDescription: application.jobDescription ?? "" };
}

function createRequestBody(form: ApplicationForm) {
  return { company: form.company.trim(), position: form.position.trim(), status: form.status, location: form.location.trim() || null, jobUrl: form.jobUrl.trim() || null, appliedDate: form.appliedDate || null, notes: form.notes.trim() || null, jobDescription: form.jobDescription.trim() || null };
}

function formatAppliedDate(date: string | null) {
  return date ? new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric" }).format(new Date(`${date}T00:00:00`)) : null;
}

function formatInterviewDateTime(dateTime: string) {
  return new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric", hour: "numeric", minute: "2-digit" }).format(new Date(dateTime));
}

function createInterviewForm(interview: Interview): InterviewForm {
  return { scheduledAt: interview.scheduledAt.slice(0, 16), type: interview.type, interviewer: interview.interviewer ?? "", notes: interview.notes ?? "" };
}

function createContactForm(contact: Contact): ContactForm {
  return { name: contact.name, role: contact.role ?? "", email: contact.email ?? "", profileUrl: contact.profileUrl ?? "", notes: contact.notes ?? "" };
}

function createResumeVersionForm(resumeVersion: ResumeVersion): ResumeVersionForm {
  return { label: resumeVersion.label, documentUrl: resumeVersion.documentUrl ?? "", notes: resumeVersion.notes ?? "" };
}

function createFollowUpForm(reminder: FollowUpReminder): FollowUpForm {
  return { dueDate: reminder.dueDate, description: reminder.description, completed: reminder.completed, contactId: reminder.contact?.id.toString() ?? "" };
}

function isOverdue(dueDate: string) {
  const now = new Date();
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
  return dueDate < today;
}

function FormattedText({ content, className = "" }: { content: string; className?: string }) {
  const blocks: Array<{ type: "text"; lines: string[] } | { type: "list"; items: string[] }> = [];

  for (const line of content.split(/\r?\n/)) {
    const bullet = line.match(/^\s*[-*]\s+(.+)$/);
    const previousBlock = blocks.at(-1);
    if (bullet) {
      if (previousBlock?.type === "list") previousBlock.items.push(bullet[1]);
      else blocks.push({ type: "list", items: [bullet[1]] });
    } else if (previousBlock?.type === "text") {
      previousBlock.lines.push(line);
    } else {
      blocks.push({ type: "text", lines: [line] });
    }
  }

  return <div className={`formatted-text ${className}`}>{blocks.map((block, index) => block.type === "list"
    ? <ul key={index}>{block.items.map((item, itemIndex) => <li key={itemIndex}>{item}</li>)}</ul>
    : <p key={index}>{block.lines.join("\n")}</p>)}</div>;
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
  const [selectedApplication, setSelectedApplication] = useState<JobApplication | null>(null);
  const [interviews, setInterviews] = useState<Interview[]>([]);
  const [interviewForm, setInterviewForm] = useState<InterviewForm>(emptyInterviewForm);
  const [editingInterviewId, setEditingInterviewId] = useState<number | null>(null);
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [contactForm, setContactForm] = useState<ContactForm>(emptyContactForm);
  const [editingContactId, setEditingContactId] = useState<number | null>(null);
  const [resumeVersions, setResumeVersions] = useState<ResumeVersion[]>([]);
  const [resumeVersionForm, setResumeVersionForm] = useState<ResumeVersionForm>(emptyResumeVersionForm);
  const [editingResumeVersionId, setEditingResumeVersionId] = useState<number | null>(null);
  const [followUps, setFollowUps] = useState<FollowUpReminder[]>([]);
  const [openFollowUps, setOpenFollowUps] = useState<FollowUpReminder[]>([]);
  const [followUpForm, setFollowUpForm] = useState<FollowUpForm>(emptyFollowUpForm);
  const [editingFollowUpId, setEditingFollowUpId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isAuthenticating, setIsAuthenticating] = useState(false);
  const [isDetailsLoading, setIsDetailsLoading] = useState(false);
  const [isInterviewSubmitting, setIsInterviewSubmitting] = useState(false);
  const [deletingInterviewId, setDeletingInterviewId] = useState<number | null>(null);
  const [isContactSubmitting, setIsContactSubmitting] = useState(false);
  const [deletingContactId, setDeletingContactId] = useState<number | null>(null);
  const [isResumeVersionSubmitting, setIsResumeVersionSubmitting] = useState(false);
  const [deletingResumeVersionId, setDeletingResumeVersionId] = useState<number | null>(null);
  const [isFollowUpSubmitting, setIsFollowUpSubmitting] = useState(false);
  const [deletingFollowUpId, setDeletingFollowUpId] = useState<number | null>(null);
  const [copiedEmailId, setCopiedEmailId] = useState<number | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [authError, setAuthError] = useState("");
  const [detailsError, setDetailsError] = useState("");
  const detailsRequestId = useRef(0);

  useEffect(() => {
    const controller = new AbortController();
    async function loadSession() {
      try {
        const response = await fetch(`${API_ROOT}/auth/me`, { credentials: "include", signal: controller.signal });
        if (response.status === 401 || !response.ok) return;
        setCurrentUser((await response.json()) as Account);
        await Promise.all([loadApplications(controller.signal), loadOpenFollowUps(controller.signal)]);
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

  async function loadOpenFollowUps(signal?: AbortSignal) {
    const response = await fetch(FOLLOW_UPS_URL, { credentials: "include", signal });
    if (!response.ok) throw new Error("Could not load follow-up reminders.");
    setOpenFollowUps((await response.json()) as FollowUpReminder[]);
  }

  async function csrfHeaders() {
    const response = await fetch(`${API_ROOT}/auth/csrf`, { credentials: "include" });
    if (!response.ok) throw new Error("Could not prepare a secure request.");
    const token = (await response.json()) as CsrfToken;
    return { [token.headerName]: token.token };
  }

  function resetForm() { setForm(emptyForm); setEditingId(null); }

  function insertTextareaTab(event: KeyboardEvent<HTMLTextAreaElement>, updateValue: (value: string) => void) {
    if (event.key !== "Tab") return;
    event.preventDefault();
    const field = event.currentTarget;
    const nextValue = `${field.value.slice(0, field.selectionStart)}\t${field.value.slice(field.selectionEnd)}`;
    const nextCursorPosition = field.selectionStart + 1;
    updateValue(nextValue);
    window.requestAnimationFrame(() => field.setSelectionRange(nextCursorPosition, nextCursorPosition));
  }

  function closeDetails() {
    detailsRequestId.current += 1;
    setSelectedApplication(null);
    setInterviews([]);
    setInterviewForm(emptyInterviewForm);
    setEditingInterviewId(null);
    setContacts([]);
    setContactForm(emptyContactForm);
    setEditingContactId(null);
    setResumeVersions([]);
    setResumeVersionForm(emptyResumeVersionForm);
    setEditingResumeVersionId(null);
    setFollowUps([]);
    setFollowUpForm(emptyFollowUpForm);
    setEditingFollowUpId(null);
    setDetailsError("");
    setIsDetailsLoading(false);
  }

  function handleEditStart(application: JobApplication) {
    setError("");
    setEditingId(application.id);
    setForm(createFormFromApplication(application));
    closeDetails();
  }

  async function handleDetailsOpen(applicationId: number) {
    const requestId = detailsRequestId.current + 1;
    detailsRequestId.current = requestId;
    setIsDetailsLoading(true);
    setDetailsError("");
    setSelectedApplication(null);
    setInterviews([]);
    setContacts([]);
    setResumeVersions([]);
    setFollowUps([]);

    try {
      const response = await fetch(`${APPLICATIONS_URL}/${applicationId}`, { credentials: "include" });
      if (!response.ok) throw new Error(await responseMessage(response, "Could not load this application."));
      const application = (await response.json()) as JobApplication;
      const [interviewsResponse, contactsResponse, followUpsResponse, resumeVersionsResponse] = await Promise.all([
        fetch(`${APPLICATIONS_URL}/${applicationId}/interviews`, { credentials: "include" }),
        fetch(`${APPLICATIONS_URL}/${applicationId}/contacts`, { credentials: "include" }),
        fetch(`${APPLICATIONS_URL}/${applicationId}/follow-ups`, { credentials: "include" }),
        fetch(`${APPLICATIONS_URL}/${applicationId}/resumes`, { credentials: "include" }),
      ]);
      if (!interviewsResponse.ok) throw new Error(await responseMessage(interviewsResponse, "Could not load interviews."));
      if (!contactsResponse.ok) throw new Error(await responseMessage(contactsResponse, "Could not load contacts."));
      if (!followUpsResponse.ok) throw new Error(await responseMessage(followUpsResponse, "Could not load follow-up reminders."));
      if (!resumeVersionsResponse.ok) throw new Error(await responseMessage(resumeVersionsResponse, "Could not load resume versions."));
      if (detailsRequestId.current === requestId) {
        setSelectedApplication(application);
        setInterviews((await interviewsResponse.json()) as Interview[]);
        setContacts((await contactsResponse.json()) as Contact[]);
        setFollowUps((await followUpsResponse.json()) as FollowUpReminder[]);
        setResumeVersions((await resumeVersionsResponse.json()) as ResumeVersion[]);
      }
    } catch (requestError) {
      if (detailsRequestId.current === requestId) {
        setDetailsError(requestError instanceof Error ? requestError.message : "Could not load this application.");
      }
    } finally {
      if (detailsRequestId.current === requestId) setIsDetailsLoading(false);
    }
  }

  function resetInterviewForm() {
    setInterviewForm(emptyInterviewForm);
    setEditingInterviewId(null);
  }

  async function handleInterviewSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedApplication) return;
    setIsInterviewSubmitting(true);
    setDetailsError("");
    const isEditing = editingInterviewId !== null;
    try {
      const response = await fetch(`${APPLICATIONS_URL}/${selectedApplication.id}/interviews${isEditing ? `/${editingInterviewId}` : ""}`, {
        method: isEditing ? "PUT" : "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", ...(await csrfHeaders()) },
        body: JSON.stringify({ scheduledAt: interviewForm.scheduledAt, type: interviewForm.type, interviewer: interviewForm.interviewer.trim() || null, notes: interviewForm.notes.trim() || null }),
      });
      if (!response.ok) throw new Error(await responseMessage(response, "Could not save this interview."));
      const saved = (await response.json()) as Interview;
      setInterviews((current) => (isEditing ? current.map((interview) => interview.id === saved.id ? saved : interview) : [...current, saved]).sort((first, second) => first.scheduledAt.localeCompare(second.scheduledAt)));
      resetInterviewForm();
    } catch (requestError) {
      setDetailsError(requestError instanceof Error ? requestError.message : "Could not save this interview.");
    } finally {
      setIsInterviewSubmitting(false);
    }
  }

  async function handleInterviewDelete(interview: Interview) {
    if (!selectedApplication || !window.confirm("Delete this interview? This cannot be undone.")) return;
    setDeletingInterviewId(interview.id);
    setDetailsError("");
    try {
      const response = await fetch(`${APPLICATIONS_URL}/${selectedApplication.id}/interviews/${interview.id}`, { method: "DELETE", credentials: "include", headers: await csrfHeaders() });
      if (!response.ok) throw new Error(await responseMessage(response, "Could not delete this interview."));
      setInterviews((current) => current.filter((item) => item.id !== interview.id));
      if (editingInterviewId === interview.id) resetInterviewForm();
    } catch (requestError) {
      setDetailsError(requestError instanceof Error ? requestError.message : "Could not delete this interview.");
    } finally {
      setDeletingInterviewId(null);
    }
  }

  function resetContactForm() {
    setContactForm(emptyContactForm);
    setEditingContactId(null);
  }

  async function handleContactSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedApplication) return;
    setIsContactSubmitting(true);
    setDetailsError("");
    const isEditing = editingContactId !== null;
    try {
      const response = await fetch(`${APPLICATIONS_URL}/${selectedApplication.id}/contacts${isEditing ? `/${editingContactId}` : ""}`, {
        method: isEditing ? "PUT" : "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", ...(await csrfHeaders()) },
        body: JSON.stringify({ name: contactForm.name.trim(), role: contactForm.role.trim() || null, email: contactForm.email.trim() || null, profileUrl: contactForm.profileUrl.trim() || null, notes: contactForm.notes.trim() || null }),
      });
      if (!response.ok) throw new Error(await responseMessage(response, "Could not save this contact."));
      const saved = (await response.json()) as Contact;
      setContacts((current) => (isEditing ? current.map((contact) => contact.id === saved.id ? saved : contact) : [...current, saved]).sort((first, second) => first.name.localeCompare(second.name)));
      resetContactForm();
    } catch (requestError) {
      setDetailsError(requestError instanceof Error ? requestError.message : "Could not save this contact.");
    } finally {
      setIsContactSubmitting(false);
    }
  }

  async function handleContactDelete(contact: Contact) {
    if (!selectedApplication || !window.confirm(`Delete ${contact.name}? This cannot be undone.`)) return;
    setDeletingContactId(contact.id);
    setDetailsError("");
    try {
      const response = await fetch(`${APPLICATIONS_URL}/${selectedApplication.id}/contacts/${contact.id}`, { method: "DELETE", credentials: "include", headers: await csrfHeaders() });
      if (!response.ok) throw new Error(await responseMessage(response, "Could not delete this contact."));
      setContacts((current) => current.filter((item) => item.id !== contact.id));
      if (editingContactId === contact.id) resetContactForm();
      setFollowUps((current) => current.map((reminder) => reminder.contact?.id === contact.id ? { ...reminder, contact: null } : reminder));
      void loadOpenFollowUps();
    } catch (requestError) {
      setDetailsError(requestError instanceof Error ? requestError.message : "Could not delete this contact.");
    } finally {
      setDeletingContactId(null);
    }
  }

  async function handleCopyEmail(email: string, contactId: number) {
    let copied = false;
    try {
      await navigator.clipboard.writeText(email);
      copied = true;
    } catch {
      const temporaryInput = document.createElement("textarea");
      temporaryInput.value = email;
      temporaryInput.style.position = "fixed";
      temporaryInput.style.opacity = "0";
      document.body.append(temporaryInput);
      temporaryInput.select();
      copied = document.execCommand("copy");
      temporaryInput.remove();
    }

    if (!copied) {
      setDetailsError("Could not copy this email address.");
      return;
    }

    setCopiedEmailId(contactId);
    window.setTimeout(() => setCopiedEmailId((current) => current === contactId ? null : current), 1800);
  }

  function resetResumeVersionForm() {
    setResumeVersionForm(emptyResumeVersionForm);
    setEditingResumeVersionId(null);
  }

  async function handleResumeVersionSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedApplication) return;
    setIsResumeVersionSubmitting(true);
    setDetailsError("");
    const isEditing = editingResumeVersionId !== null;
    try {
      const response = await fetch(`${APPLICATIONS_URL}/${selectedApplication.id}/resumes${isEditing ? `/${editingResumeVersionId}` : ""}`, {
        method: isEditing ? "PUT" : "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", ...(await csrfHeaders()) },
        body: JSON.stringify({ label: resumeVersionForm.label.trim(), documentUrl: resumeVersionForm.documentUrl.trim() || null, notes: resumeVersionForm.notes.trim() || null }),
      });
      if (!response.ok) throw new Error(await responseMessage(response, "Could not save this resume version."));
      const saved = (await response.json()) as ResumeVersion;
      setResumeVersions((current) => isEditing ? current.map((resumeVersion) => resumeVersion.id === saved.id ? saved : resumeVersion) : [saved, ...current]);
      resetResumeVersionForm();
    } catch (requestError) {
      setDetailsError(requestError instanceof Error ? requestError.message : "Could not save this resume version.");
    } finally {
      setIsResumeVersionSubmitting(false);
    }
  }

  async function handleResumeVersionDelete(resumeVersion: ResumeVersion) {
    if (!selectedApplication || !window.confirm(`Delete ${resumeVersion.label}? This cannot be undone.`)) return;
    setDeletingResumeVersionId(resumeVersion.id);
    setDetailsError("");
    try {
      const response = await fetch(`${APPLICATIONS_URL}/${selectedApplication.id}/resumes/${resumeVersion.id}`, { method: "DELETE", credentials: "include", headers: await csrfHeaders() });
      if (!response.ok) throw new Error(await responseMessage(response, "Could not delete this resume version."));
      setResumeVersions((current) => current.filter((item) => item.id !== resumeVersion.id));
      if (editingResumeVersionId === resumeVersion.id) resetResumeVersionForm();
    } catch (requestError) {
      setDetailsError(requestError instanceof Error ? requestError.message : "Could not delete this resume version.");
    } finally {
      setDeletingResumeVersionId(null);
    }
  }

  function resetFollowUpForm() {
    setFollowUpForm(emptyFollowUpForm);
    setEditingFollowUpId(null);
  }

  async function handleFollowUpSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedApplication) return;
    setIsFollowUpSubmitting(true);
    setDetailsError("");
    const isEditing = editingFollowUpId !== null;
    try {
      const response = await fetch(`${APPLICATIONS_URL}/${selectedApplication.id}/follow-ups${isEditing ? `/${editingFollowUpId}` : ""}`, {
        method: isEditing ? "PUT" : "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", ...(await csrfHeaders()) },
        body: JSON.stringify({ dueDate: followUpForm.dueDate, description: followUpForm.description.trim(), completed: followUpForm.completed, contactId: followUpForm.contactId ? Number(followUpForm.contactId) : null }),
      });
      if (!response.ok) throw new Error(await responseMessage(response, "Could not save this follow-up reminder."));
      const saved = (await response.json()) as FollowUpReminder;
      setFollowUps((current) => (isEditing ? current.map((reminder) => reminder.id === saved.id ? saved : reminder) : [...current, saved]).sort((first, second) => Number(first.completed) - Number(second.completed) || first.dueDate.localeCompare(second.dueDate)));
      resetFollowUpForm();
      void loadOpenFollowUps();
    } catch (requestError) {
      setDetailsError(requestError instanceof Error ? requestError.message : "Could not save this follow-up reminder.");
    } finally {
      setIsFollowUpSubmitting(false);
    }
  }

  async function handleFollowUpCompletion(reminder: FollowUpReminder) {
    if (!selectedApplication) return;
    setIsFollowUpSubmitting(true);
    setDetailsError("");
    try {
      const response = await fetch(`${APPLICATIONS_URL}/${selectedApplication.id}/follow-ups/${reminder.id}`, {
        method: "PUT",
        credentials: "include",
        headers: { "Content-Type": "application/json", ...(await csrfHeaders()) },
        body: JSON.stringify({ dueDate: reminder.dueDate, description: reminder.description, completed: !reminder.completed, contactId: reminder.contact?.id ?? null }),
      });
      if (!response.ok) throw new Error(await responseMessage(response, "Could not update this follow-up reminder."));
      const saved = (await response.json()) as FollowUpReminder;
      setFollowUps((current) => current.map((item) => item.id === saved.id ? saved : item).sort((first, second) => Number(first.completed) - Number(second.completed) || first.dueDate.localeCompare(second.dueDate)));
      void loadOpenFollowUps();
    } catch (requestError) {
      setDetailsError(requestError instanceof Error ? requestError.message : "Could not update this follow-up reminder.");
    } finally {
      setIsFollowUpSubmitting(false);
    }
  }

  async function handleFollowUpDelete(reminder: FollowUpReminder) {
    if (!selectedApplication || !window.confirm("Delete this follow-up reminder? This cannot be undone.")) return;
    setDeletingFollowUpId(reminder.id);
    setDetailsError("");
    try {
      const response = await fetch(`${APPLICATIONS_URL}/${selectedApplication.id}/follow-ups/${reminder.id}`, { method: "DELETE", credentials: "include", headers: await csrfHeaders() });
      if (!response.ok) throw new Error(await responseMessage(response, "Could not delete this follow-up reminder."));
      setFollowUps((current) => current.filter((item) => item.id !== reminder.id));
      if (editingFollowUpId === reminder.id) resetFollowUpForm();
      void loadOpenFollowUps();
    } catch (requestError) {
      setDetailsError(requestError instanceof Error ? requestError.message : "Could not delete this follow-up reminder.");
    } finally {
      setDeletingFollowUpId(null);
    }
  }

  async function handleAuthentication(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsAuthenticating(true);
    setAuthError("");
    try {
      const response = await fetch(`${API_ROOT}/auth/${authMode}`, { method: "POST", credentials: "include", headers: { "Content-Type": "application/json", ...(await csrfHeaders()) }, body: JSON.stringify(credentials) });
      if (!response.ok) throw new Error(await responseMessage(response, authMode === "register" ? "Could not create your account." : "Could not log you in."));
      setCurrentUser((await response.json()) as Account);
      setCredentials(emptyCredentials);
      await Promise.all([loadApplications(), loadOpenFollowUps()]);
    } catch (requestError) {
      setAuthError(requestError instanceof Error ? requestError.message : "Could not complete that request.");
    } finally { setIsAuthenticating(false); }
  }

  async function handleLogout() {
    setAuthError("");
    try {
      const response = await fetch(`${API_ROOT}/auth/logout`, { method: "POST", credentials: "include", headers: await csrfHeaders() });
      if (!response.ok) throw new Error(await responseMessage(response, "Could not log you out."));
      setCurrentUser(null); setApplications([]); setOpenFollowUps([]); resetForm();
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
      if (selectedApplication?.id === saved.id) setSelectedApplication(saved);
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
      if (selectedApplication?.id === application.id) closeDetails();
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
      <section className="follow-up-overview" aria-labelledby="follow-up-overview-title">
        <div><p className="dashboard-section-label">Next actions</p><h3 id="follow-up-overview-title">Follow-ups</h3></div><span>{openFollowUps.length} open</span>
        {openFollowUps.length === 0 ? <p className="follow-up-overview-empty">No open follow-ups. Add one from an application when you have a next step.</p> : <div className="follow-up-overview-list">{openFollowUps.slice(0, 5).map((reminder) => <button type="button" className={`follow-up-overview-item ${isOverdue(reminder.dueDate) ? "is-overdue" : ""}`} key={reminder.id} onClick={() => handleDetailsOpen(reminder.application.id)}><span>{isOverdue(reminder.dueDate) ? "Overdue" : formatAppliedDate(reminder.dueDate)}</span><strong>{reminder.description}</strong><small>{reminder.application.company} - {reminder.application.position}</small></button>)}</div>}
      </section>
    </section>}

    {!isLoading && currentUser && (isDetailsLoading || selectedApplication || detailsError) && <div className="detail-backdrop" onClick={closeDetails}>
      <section className="detail-panel" role="dialog" aria-modal="true" aria-labelledby="detail-title" onClick={(event) => event.stopPropagation()}>
        <button type="button" className="detail-close" onClick={closeDetails} aria-label="Close application details">Close</button>
        {isDetailsLoading && <p className="message">Loading application details...</p>}
        {detailsError && <p className="message error-message">{detailsError}</p>}
        {selectedApplication && <>
          <div className="detail-heading"><p className="company">{selectedApplication.company}</p><h2 id="detail-title">{selectedApplication.position}</h2><span className={`status status-${selectedApplication.status.toLowerCase()}`}>{statusLabels[selectedApplication.status]}</span></div>
          <div className="detail-grid">
            <div><p>Location</p><strong>{selectedApplication.location ?? "Not specified"}</strong></div>
            <div><p>Date applied</p><strong>{formatAppliedDate(selectedApplication.appliedDate) ?? "Not specified"}</strong></div>
            <div className="detail-notes"><p>Notes</p>{selectedApplication.notes ? <FormattedText content={selectedApplication.notes} /> : <strong>No notes yet.</strong>}</div>
            <div><p>Job posting</p>{selectedApplication.jobUrl ? <a href={selectedApplication.jobUrl} target="_blank" rel="noreferrer">Open posting</a> : <strong>Not saved</strong>}</div>
          </div>
          <section className="job-description-section" aria-labelledby="job-description-title">
            <div><p className="detail-label">Source material</p><h3 id="job-description-title">Job description</h3></div>
            {selectedApplication.jobDescription ? <FormattedText content={selectedApplication.jobDescription} className="job-description-content" /> : <p className="job-description-empty">No job description saved yet. Paste it below so the role details stay available even if the posting closes.</p>}
            <button type="button" className="job-description-edit" onClick={() => handleEditStart(selectedApplication)}>{selectedApplication.jobDescription ? "Edit job description" : "Add job description"}</button>
          </section>
          <section className="resume-section" aria-labelledby="resume-title">
            <div className="resume-heading"><div><p className="detail-label">Application materials</p><h3 id="resume-title">Resume versions</h3></div><span>{resumeVersions.length}</span></div>
            {resumeVersions.length === 0 ? <p className="resume-empty">No resume version saved yet. Record the version tailored for this application.</p> : <div className="resume-list">{resumeVersions.map((resumeVersion) => <article className="resume-card" key={resumeVersion.id}><div><strong>{resumeVersion.label}</strong>{resumeVersion.documentUrl && <a href={resumeVersion.documentUrl} target="_blank" rel="noreferrer">Open document</a>}{resumeVersion.notes && <FormattedText content={resumeVersion.notes} className="resume-notes" />}</div><div className="resume-actions"><button type="button" className="text-button" onClick={() => { setResumeVersionForm(createResumeVersionForm(resumeVersion)); setEditingResumeVersionId(resumeVersion.id); }}>Edit</button><button type="button" className="text-button danger-text" onClick={() => handleResumeVersionDelete(resumeVersion)} disabled={deletingResumeVersionId === resumeVersion.id}>{deletingResumeVersionId === resumeVersion.id ? "Deleting..." : "Delete"}</button></div></article>)}</div>}
            <form className="resume-form" onSubmit={handleResumeVersionSubmit}>
              <h4>{editingResumeVersionId === null ? "Add a resume version" : "Update resume version"}</h4>
              <div className="resume-form-grid">
                <label>Version label<input required value={resumeVersionForm.label} onChange={(event) => setResumeVersionForm({ ...resumeVersionForm, label: event.target.value })} placeholder="Software Resume - September 2026" /></label>
                <label>Document link<input type="url" value={resumeVersionForm.documentUrl} onChange={(event) => setResumeVersionForm({ ...resumeVersionForm, documentUrl: event.target.value })} placeholder="https://drive.google.com/..." /></label>
                <label className="full-width">Tailoring notes<textarea value={resumeVersionForm.notes} onChange={(event) => setResumeVersionForm({ ...resumeVersionForm, notes: event.target.value })} onKeyDown={(event) => insertTextareaTab(event, (notes) => setResumeVersionForm({ ...resumeVersionForm, notes }))} placeholder="Skills emphasized, sections adjusted... Use - for bullet points." rows={3} /></label>
              </div>
              <div className="resume-form-actions"><button type="submit" disabled={isResumeVersionSubmitting}>{isResumeVersionSubmitting ? "Saving..." : editingResumeVersionId === null ? "Add resume version" : "Update resume version"}</button>{editingResumeVersionId !== null && <button type="button" className="secondary-button" onClick={resetResumeVersionForm} disabled={isResumeVersionSubmitting}>Cancel edit</button>}</div>
            </form>
          </section>
          <section className="interview-section" aria-labelledby="interview-title">
            <div className="interview-heading"><div><p className="detail-label">Interview plan</p><h3 id="interview-title">Interviews</h3></div><span>{interviews.length}</span></div>
            {interviews.length === 0 ? <p className="interview-empty">No interviews scheduled yet.</p> : <div className="interview-list">{interviews.map((interview) => <article className="interview-card" key={interview.id}><div><p className="interview-date">{formatInterviewDateTime(interview.scheduledAt)}</p><strong>{interviewTypeLabels[interview.type]} interview</strong>{interview.interviewer && <span>With {interview.interviewer}</span>}{interview.notes && <FormattedText content={interview.notes} className="interview-notes" />}</div><div className="interview-actions"><button type="button" className="text-button" onClick={() => { setInterviewForm(createInterviewForm(interview)); setEditingInterviewId(interview.id); }}>Edit</button><button type="button" className="text-button danger-text" onClick={() => handleInterviewDelete(interview)} disabled={deletingInterviewId === interview.id}>{deletingInterviewId === interview.id ? "Deleting..." : "Delete"}</button></div></article>)}</div>}
            <form className="interview-form" onSubmit={handleInterviewSubmit}>
              <h4>{editingInterviewId === null ? "Schedule an interview" : "Update interview"}</h4>
              <div className="interview-form-grid">
                <label>Date and time<input type="datetime-local" required value={interviewForm.scheduledAt} onChange={(event) => setInterviewForm({ ...interviewForm, scheduledAt: event.target.value })} /></label>
                <label>Type<select value={interviewForm.type} onChange={(event) => setInterviewForm({ ...interviewForm, type: event.target.value as InterviewType })}>{Object.entries(interviewTypeLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
                <label className="full-width">Interviewer<input value={interviewForm.interviewer} onChange={(event) => setInterviewForm({ ...interviewForm, interviewer: event.target.value })} placeholder="Name, recruiter, or panel" /></label>
                <label className="full-width">Preparation notes<textarea value={interviewForm.notes} onChange={(event) => setInterviewForm({ ...interviewForm, notes: event.target.value })} onKeyDown={(event) => insertTextareaTab(event, (notes) => setInterviewForm({ ...interviewForm, notes }))} placeholder="Topics to prepare, questions to ask... Use - for bullet points." rows={3} /></label>
              </div>
              <div className="interview-form-actions"><button type="submit" disabled={isInterviewSubmitting}>{isInterviewSubmitting ? "Saving..." : editingInterviewId === null ? "Add interview" : "Update interview"}</button>{editingInterviewId !== null && <button type="button" className="secondary-button" onClick={resetInterviewForm} disabled={isInterviewSubmitting}>Cancel edit</button>}</div>
            </form>
          </section>
          <section className="contact-section" aria-labelledby="contact-title">
            <div className="contact-heading"><div><p className="detail-label">People</p><h3 id="contact-title">Contacts</h3></div><span>{contacts.length}</span></div>
            {contacts.length === 0 ? <p className="contact-empty">No contacts saved for this application yet.</p> : <div className="contact-list">{contacts.map((contact) => <article className="contact-card" key={contact.id}><div><strong>{contact.name}</strong>{contact.role && <span>{contact.role}</span>}<div className="contact-links">{contact.email && <><a href={`mailto:${contact.email}`}>Email {contact.name}</a><button type="button" className="text-button copy-email-button" onClick={() => handleCopyEmail(contact.email!, contact.id)}>{copiedEmailId === contact.id ? "Copied" : "Copy email"}</button></>}{contact.profileUrl && <a href={contact.profileUrl} target="_blank" rel="noreferrer">Open profile</a>}</div>{contact.notes && <FormattedText content={contact.notes} className="contact-notes" />}</div><div className="contact-actions"><button type="button" className="text-button" onClick={() => { setContactForm(createContactForm(contact)); setEditingContactId(contact.id); }}>Edit</button><button type="button" className="text-button danger-text" onClick={() => handleContactDelete(contact)} disabled={deletingContactId === contact.id}>{deletingContactId === contact.id ? "Deleting..." : "Delete"}</button></div></article>)}</div>}
            <form className="contact-form" onSubmit={handleContactSubmit}>
              <h4>{editingContactId === null ? "Add a contact" : "Update contact"}</h4>
              <div className="contact-form-grid">
                <label>Name<input required value={contactForm.name} onChange={(event) => setContactForm({ ...contactForm, name: event.target.value })} placeholder="Taylor Morgan" /></label>
                <label>Role or relationship<input value={contactForm.role} onChange={(event) => setContactForm({ ...contactForm, role: event.target.value })} placeholder="Recruiter" /></label>
                <label>Email<input type="email" value={contactForm.email} onChange={(event) => setContactForm({ ...contactForm, email: event.target.value })} placeholder="taylor@example.com" /></label>
                <label>Profile link<input type="url" value={contactForm.profileUrl} onChange={(event) => setContactForm({ ...contactForm, profileUrl: event.target.value })} placeholder="https://linkedin.com/in/..." /></label>
                <label className="full-width">Notes<textarea value={contactForm.notes} onChange={(event) => setContactForm({ ...contactForm, notes: event.target.value })} onKeyDown={(event) => insertTextareaTab(event, (notes) => setContactForm({ ...contactForm, notes }))} placeholder="How you met, preferred contact method... Use - for bullet points." rows={3} /></label>
              </div>
              <div className="contact-form-actions"><button type="submit" disabled={isContactSubmitting}>{isContactSubmitting ? "Saving..." : editingContactId === null ? "Add contact" : "Update contact"}</button>{editingContactId !== null && <button type="button" className="secondary-button" onClick={resetContactForm} disabled={isContactSubmitting}>Cancel edit</button>}</div>
            </form>
          </section>
          <section className="follow-up-section" aria-labelledby="follow-up-title">
            <div className="follow-up-heading"><div><p className="detail-label">Next actions</p><h3 id="follow-up-title">Follow-ups</h3></div><span>{followUps.filter((reminder) => !reminder.completed).length} open</span></div>
            {followUps.length === 0 ? <p className="follow-up-empty">No follow-ups scheduled for this application yet.</p> : <div className="follow-up-list">{followUps.map((reminder) => <article className={`follow-up-card ${reminder.completed ? "is-completed" : ""} ${!reminder.completed && isOverdue(reminder.dueDate) ? "is-overdue" : ""}`} key={reminder.id}><button type="button" className="follow-up-toggle" onClick={() => handleFollowUpCompletion(reminder)} disabled={isFollowUpSubmitting} aria-label={reminder.completed ? "Reopen follow-up" : "Mark follow-up complete"}>{reminder.completed ? "Done" : "Mark done"}</button><div><p>{reminder.completed ? "Completed" : isOverdue(reminder.dueDate) ? "Overdue" : "Due"} {formatAppliedDate(reminder.dueDate)}</p><strong>{reminder.description}</strong>{reminder.contact && <span>Contact: {reminder.contact.name}</span>}</div><div className="follow-up-actions"><button type="button" className="text-button" onClick={() => { setFollowUpForm(createFollowUpForm(reminder)); setEditingFollowUpId(reminder.id); }}>Edit</button><button type="button" className="text-button danger-text" onClick={() => handleFollowUpDelete(reminder)} disabled={deletingFollowUpId === reminder.id}>{deletingFollowUpId === reminder.id ? "Deleting..." : "Delete"}</button></div></article>)}</div>}
            <form className="follow-up-form" onSubmit={handleFollowUpSubmit}>
              <h4>{editingFollowUpId === null ? "Add a follow-up" : "Update follow-up"}</h4>
              <div className="follow-up-form-grid">
                <label>Due date<input type="date" required value={followUpForm.dueDate} onChange={(event) => setFollowUpForm({ ...followUpForm, dueDate: event.target.value })} /></label>
                <label>Linked contact<select value={followUpForm.contactId} onChange={(event) => setFollowUpForm({ ...followUpForm, contactId: event.target.value })}><option value="">No specific contact</option>{contacts.map((contact) => <option value={contact.id} key={contact.id}>{contact.name}{contact.role ? ` - ${contact.role}` : ""}</option>)}</select></label>
                <label className="full-width">Next action<textarea required value={followUpForm.description} onChange={(event) => setFollowUpForm({ ...followUpForm, description: event.target.value })} onKeyDown={(event) => insertTextareaTab(event, (description) => setFollowUpForm({ ...followUpForm, description }))} placeholder="Email the recruiter after the interview" rows={3} /></label>
                {editingFollowUpId !== null && <label className="follow-up-complete"><input type="checkbox" checked={followUpForm.completed} onChange={(event) => setFollowUpForm({ ...followUpForm, completed: event.target.checked })} /> Mark as complete</label>}
              </div>
              <div className="follow-up-form-actions"><button type="submit" disabled={isFollowUpSubmitting}>{isFollowUpSubmitting ? "Saving..." : editingFollowUpId === null ? "Add follow-up" : "Update follow-up"}</button>{editingFollowUpId !== null && <button type="button" className="secondary-button" onClick={resetFollowUpForm} disabled={isFollowUpSubmitting}>Cancel edit</button>}</div>
            </form>
          </section>
          <div className="detail-actions"><button type="button" onClick={() => handleEditStart(selectedApplication)}>Edit application</button><button type="button" className="ghost-button danger-button" onClick={() => handleDelete(selectedApplication)} disabled={deletingId === selectedApplication.id}>{deletingId === selectedApplication.id ? "Deleting..." : "Delete application"}</button></div>
        </>}
      </section>
    </div>}

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
          <label className="full-width">Notes<textarea value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} onKeyDown={(event) => insertTextareaTab(event, (notes) => setForm({ ...form, notes }))} placeholder="What makes this role interesting? Use - for bullet points." rows={4} /></label>
          <label className="full-width">Job description<textarea value={form.jobDescription} onChange={(event) => setForm({ ...form, jobDescription: event.target.value })} onKeyDown={(event) => insertTextareaTab(event, (jobDescription) => setForm({ ...form, jobDescription }))} placeholder="Paste the posting details, responsibilities, requirements, compensation, and benefits here. Use - for bullet points." rows={8} /></label>
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
        {visibleApplications.length > 0 && <div className="cards">{visibleApplications.map((application) => { const appliedDate = formatAppliedDate(application.appliedDate); return <article className="application-card" key={application.id}><div className="card-content"><div><p className="company">{application.company}</p><h3>{application.position}</h3>{(application.location || appliedDate) && <p className="details">{[application.location, appliedDate].filter(Boolean).join(" | ")}</p>}</div>{application.notes && <FormattedText content={application.notes} className="notes" />}{application.jobUrl && <p className="link-row"><a href={application.jobUrl} target="_blank" rel="noreferrer">View posting</a></p>}</div><div className="card-side"><span className={`status status-${application.status.toLowerCase()}`}>{statusLabels[application.status]}</span><div className="card-actions"><button type="button" className="ghost-button" onClick={() => handleDetailsOpen(application.id)} disabled={isDetailsLoading || deletingId === application.id}>View details</button><button type="button" className="ghost-button" onClick={() => handleEditStart(application)} disabled={isSubmitting || deletingId === application.id}>Edit</button><button type="button" className="ghost-button danger-button" onClick={() => handleDelete(application)} disabled={deletingId === application.id}>{deletingId === application.id ? "Deleting..." : "Delete"}</button></div></div></article>; })}</div>}
      </section>
    </section>}
  </main>;
}

export default App;
