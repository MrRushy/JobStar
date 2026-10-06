package com.jobstar.backend.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobstar.backend.repository.ApplicationRepository;
import com.jobstar.backend.repository.ResumeVersionRepository;
import com.jobstar.backend.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private ResumeVersionRepository resumeVersionRepository;

    @BeforeEach
    void clearDatabase() {
        applicationRepository.deleteAll();
        resumeVersionRepository.deleteAll();
        userAccountRepository.deleteAll();
    }

    @Test
    void applicationsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isForbidden());
    }

    @Test
    void signedOutBrowserFileRequestsReceiveAProductErrorPage() throws Exception {
        mockMvc.perform(get("/api/resumes/1/file").accept(MediaType.TEXT_HTML))
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Sign in to the account")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"));
    }

    @Test
    void signedOutUsersCanLoadTheFrontendButNotProtectedApis() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl("index.html"));
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Public page fixture")));
        mockMvc.perform(get("/api/resumes"))
                .andExpect(status().isForbidden());
    }

    @Test
    void signedInUserCanCreateAndReadApplications() throws Exception {
        MockHttpSession session = register("user@example.com");

        mockMvc.perform(post("/api/applications")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"Acme", "position":"Developer", "status":"APPLIED", "jobDescription":"Build reliable web applications."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.company").value("Acme"))
                .andExpect(jsonPath("$.position").value("Developer"))
                .andExpect(jsonPath("$.jobDescription").value("Build reliable web applications."));

        mockMvc.perform(get("/api/applications").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].company").value("Acme"));
    }

    @Test
    void signedInUserCanUpdateAndDeleteTheirApplication() throws Exception {
        MockHttpSession session = register("user@example.com");
        String applicationId = createApplication(session, "Acme", "Developer");

        mockMvc.perform(put("/api/applications/{id}", applicationId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"Acme", "position":"Senior Developer", "status":"INTERVIEWING"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value("Senior Developer"))
                .andExpect(jsonPath("$.status").value("INTERVIEWING"));

        mockMvc.perform(delete("/api/applications/{id}", applicationId)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/applications/{id}", applicationId).session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCannotReadAnotherUsersApplication() throws Exception {
        MockHttpSession firstSession = register("first@example.com");
        MockHttpSession secondSession = register("second@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/applications")
                        .session(firstSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"Private Company", "position":"Developer"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String applicationId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(get("/api/applications/{id}", applicationId).session(secondSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void createApplicationRejectsMissingCompany() throws Exception {
        MockHttpSession session = register("user@example.com");

        mockMvc.perform(post("/api/applications")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"", "position":"Developer"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginRejectsIncorrectPassword() throws Exception {
        register("user@example.com");

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com", "password":"incorrect-password"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedInUserCanManageInterviewsForTheirApplication() throws Exception {
        MockHttpSession session = register("user@example.com");
        String applicationId = createApplication(session, "Acme", "Developer");

        MvcResult createResult = mockMvc.perform(post("/api/applications/{applicationId}/interviews", applicationId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledAt":"2026-10-15T14:30:00", "type":"VIDEO", "interviewer":"Sam Lee", "notes":"Bring portfolio examples."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("VIDEO"))
                .andExpect(jsonPath("$.interviewer").value("Sam Lee"))
                .andReturn();

        String interviewId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(get("/api/applications/{applicationId}/interviews", applicationId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notes").value("Bring portfolio examples."));

        mockMvc.perform(put("/api/applications/{applicationId}/interviews/{interviewId}", applicationId, interviewId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledAt":"2026-10-16T10:00:00", "type":"TECHNICAL", "interviewer":"Sam Lee"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TECHNICAL"));

        mockMvc.perform(delete("/api/applications/{applicationId}/interviews/{interviewId}", applicationId, interviewId)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void userCannotAccessAnotherUsersInterviews() throws Exception {
        MockHttpSession firstSession = register("first@example.com");
        MockHttpSession secondSession = register("second@example.com");
        String applicationId = createApplication(firstSession, "Private Company", "Developer");

        mockMvc.perform(get("/api/applications/{applicationId}/interviews", applicationId).session(secondSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void signedInUserCanManageContactsForTheirApplication() throws Exception {
        MockHttpSession session = register("user@example.com");
        String applicationId = createApplication(session, "Acme", "Developer");

        MvcResult createResult = mockMvc.perform(post("/api/applications/{applicationId}/contacts", applicationId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sam Lee", "role":"Recruiter", "email":"sam@example.com", "profileUrl":"https://linkedin.com/in/samlee", "notes":"Met at the career fair."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sam Lee"))
                .andExpect(jsonPath("$.role").value("Recruiter"))
                .andReturn();

        String contactId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(get("/api/applications/{applicationId}/contacts", applicationId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("sam@example.com"));

        mockMvc.perform(put("/api/applications/{applicationId}/contacts/{contactId}", applicationId, contactId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sam Lee", "role":"Senior Recruiter", "email":"sam@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("Senior Recruiter"));

        mockMvc.perform(delete("/api/applications/{applicationId}/contacts/{contactId}", applicationId, contactId)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void userCannotAccessAnotherUsersContacts() throws Exception {
        MockHttpSession firstSession = register("first@example.com");
        MockHttpSession secondSession = register("second@example.com");
        String applicationId = createApplication(firstSession, "Private Company", "Developer");

        mockMvc.perform(get("/api/applications/{applicationId}/contacts", applicationId).session(secondSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void signedInUserCanManageFollowUpReminders() throws Exception {
        MockHttpSession session = register("user@example.com");
        String applicationId = createApplication(session, "Acme", "Developer");
        String contactId = createContact(session, applicationId, "Sam Lee");

        MvcResult createResult = mockMvc.perform(post("/api/applications/{applicationId}/follow-ups", applicationId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dueDate":"2026-10-20", "description":"Send portfolio follow-up", "contactId":%s}
                                """.formatted(contactId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Send portfolio follow-up"))
                .andExpect(jsonPath("$.contact.name").value("Sam Lee"))
                .andReturn();

        String reminderId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(get("/api/follow-ups").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].application.company").value("Acme"));

        mockMvc.perform(delete("/api/applications/{applicationId}/contacts/{contactId}", applicationId, contactId)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/applications/{applicationId}/follow-ups", applicationId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contact").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(put("/api/applications/{applicationId}/follow-ups/{reminderId}", applicationId, reminderId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dueDate":"2026-10-21", "description":"Send portfolio follow-up", "completed":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));

        mockMvc.perform(get("/api/follow-ups").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(delete("/api/applications/{applicationId}/follow-ups/{reminderId}", applicationId, reminderId)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void userCannotAccessAnotherUsersFollowUpReminders() throws Exception {
        MockHttpSession firstSession = register("first@example.com");
        MockHttpSession secondSession = register("second@example.com");
        String applicationId = createApplication(firstSession, "Private Company", "Developer");

        mockMvc.perform(get("/api/applications/{applicationId}/follow-ups", applicationId).session(secondSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void signedInUserCanManageResumeVersionsForTheirApplication() throws Exception {
        MockHttpSession session = register("user@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/resumes")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Software Resume - September 2026", "documentUrl":"https://drive.example.com/resume", "notes":"Emphasized React and Spring Boot projects."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Software Resume - September 2026"))
                .andReturn();

        String resumeVersionId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(get("/api/resumes").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notes").value("Emphasized React and Spring Boot projects."));

        mockMvc.perform(put("/api/resumes/{resumeVersionId}", resumeVersionId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Software Resume - Revised", "documentUrl":"https://drive.example.com/resume"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Software Resume - Revised"));

        mockMvc.perform(delete("/api/resumes/{resumeVersionId}", resumeVersionId)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void userCannotAccessAnotherUsersResumeVersions() throws Exception {
        MockHttpSession firstSession = register("first@example.com");
        MockHttpSession secondSession = register("second@example.com");
        mockMvc.perform(post("/api/resumes")
                        .session(firstSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Private resume\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/resumes").session(secondSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    private MockHttpSession register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s", "password":"long-enough-password"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String createApplication(MockHttpSession session, String company, String position) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/applications")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"%s", "position":"%s"}
                                """.formatted(company, position)))
                .andExpect(status().isCreated())
                .andReturn();

        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
    }

    @Test
    void resumeCreationIgnoresClientSuppliedStorageMetadata() throws Exception {
        MockHttpSession session = register("metadata@example.com");
        MvcResult result = mockMvc.perform(post("/api/resumes")
                        .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Link-only resume", "storageKey":"users/other/resume.pdf",
                                 "fileName":"forged.pdf", "contentType":"text/html", "fileSize":10}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storageKey").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.fileName").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.contentType").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.fileSize").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();
        String resumeId = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
        mockMvc.perform(get("/api/resumes/{id}/file", resumeId).session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/resumes/{id}", resumeId).session(session).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void applicationCreationCannotBypassResumeOwnershipChecks() throws Exception {
        MockHttpSession owner = register("resume-owner@example.com");
        MockHttpSession other = register("resume-other@example.com");
        MvcResult resume = mockMvc.perform(post("/api/resumes").session(owner).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Private resume\"}"))
                .andExpect(status().isCreated()).andReturn();
        String resumeId = com.jayway.jsonpath.JsonPath.read(resume.getResponse().getContentAsString(), "$.id").toString();
        mockMvc.perform(get("/api/resumes/{id}/file", resumeId).session(other).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("cannot be accessed from your account")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Private resume"))));
        MvcResult application = mockMvc.perform(post("/api/applications").session(other).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"Acme", "position":"Developer", "resumeVersion":{"id":%s}}
                                """.formatted(resumeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resumeVersion").value(org.hamcrest.Matchers.nullValue())).andReturn();
        String applicationId = com.jayway.jsonpath.JsonPath.read(application.getResponse().getContentAsString(), "$.id").toString();
        mockMvc.perform(put("/api/applications/{id}/resume", applicationId).session(other).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resumeVersionId\":" + resumeId + "}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/resumes/{id}", resumeId).session(owner).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private String createContact(MockHttpSession session, String applicationId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/applications/{applicationId}/contacts", applicationId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();

        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
    }
}
