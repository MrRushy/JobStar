package com.jobstar.backend.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobstar.backend.repository.ApplicationRepository;
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

    @BeforeEach
    void clearDatabase() {
        applicationRepository.deleteAll();
        userAccountRepository.deleteAll();
    }

    @Test
    void applicationsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/applications"))
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
                                {"company":"Acme", "position":"Developer", "status":"APPLIED"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.company").value("Acme"))
                .andExpect(jsonPath("$.position").value("Developer"));

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
