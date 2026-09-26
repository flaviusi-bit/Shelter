package com.shelter.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.http.HttpMethod;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@ContextConfiguration(classes = SecurityConfigTest.TestConfig.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired MockMvc mvc;

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCanReadButCannotMutate() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/api/animals")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/animals")).andExpect(status().isForbidden());
        mvc.perform(request(HttpMethod.POST, "/api/tasks")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COORDINATOR")
    void coordinatorCanManageAnimalsAndTasksButNotMedicalRecords() throws Exception {
        mvc.perform(request(HttpMethod.POST, "/api/animals")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.PUT, "/api/animals/1")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/tasks")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/tasks/1/complete")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/tasks/sync-medical-reminders")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/animals/1/vaccinations")).andExpect(status().isForbidden());
        mvc.perform(request(HttpMethod.POST, "/api/animals/1/treatments/1/administrations/1/status")).andExpect(status().isForbidden());
        mvc.perform(request(HttpMethod.POST, "/api/animals/1/treatments")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VETERINARIAN")
    void veterinarianCanManageMedicalRecordsButNotUsers() throws Exception {
        mvc.perform(request(HttpMethod.POST, "/api/animals/1/vaccinations")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/animals/1/dewormings")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/animals/1/medical-events")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/animals/1/treatments")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/animals/1/treatments/1/administrations/generate")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/animals/1/treatments/1/administrations/1/administer")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/animals/1/treatments/1/administrations/1/status")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/animals/1/documents")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/animals/1/documents/upload")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.GET, "/api/users")).andExpect(status().isForbidden());
        mvc.perform(request(HttpMethod.GET, "/api/admin/audit")).andExpect(status().isForbidden());
        mvc.perform(request(HttpMethod.GET, "/api/admin/backups")).andExpect(status().isForbidden());
        mvc.perform(request(HttpMethod.POST, "/api/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void volunteerCanManageTasksButNotAnimalsOrMedicalRecords() throws Exception {
        mvc.perform(request(HttpMethod.POST, "/api/tasks")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/tasks/1/complete")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/tasks/1/skip")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/animals/1/treatments/1/administrations/1/status")).andExpect(status().isForbidden());
        mvc.perform(request(HttpMethod.POST, "/api/animals")).andExpect(status().isForbidden());
        mvc.perform(request(HttpMethod.POST, "/api/animals/1/vaccinations")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanManageUsersBackupsAndMedicalRecords() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/api/users")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/users")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.PUT, "/api/users/1")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/users/1/deactivate")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/users/1/reactivate")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/users/1/password")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.GET, "/api/admin/backups")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/admin/backups/backup-1/verify")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.GET, "/api/admin/audit")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/animals/1/vaccinations")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void selfPasswordChangeIsAllowedForAnyAuthenticatedRole() throws Exception {
        mvc.perform(request(HttpMethod.POST, "/api/users/me/password")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void unknownMutatingEndpointFailsClosed() throws Exception {
        mvc.perform(request(HttpMethod.DELETE, "/api/future-resource/1")).andExpect(status().isForbidden());
        mvc.perform(request(HttpMethod.PATCH, "/api/animals/1")).andExpect(status().isForbidden());
    }

    @Configuration
    static class TestConfig {
        @Bean ProbeController probeController() { return new ProbeController(); }
    }

    @RestController
    @RequestMapping("/api")
    static class ProbeController {
        @RequestMapping(value = "/animals", method = RequestMethod.GET)
        String animalsRead() { return "ok"; }
        @RequestMapping(value = "/animals", method = RequestMethod.POST)
        String animalsCreate() { return "ok"; }
        @RequestMapping(value = "/animals/{id}", method = RequestMethod.PUT)
        String animalsUpdate() { return "ok"; }
        @RequestMapping(value = "/animals/{id}/vaccinations", method = RequestMethod.POST)
        String vaccinationCreate() { return "ok"; }
        @RequestMapping(value = "/animals/{id}/dewormings", method = RequestMethod.POST)
        String dewormingCreate() { return "ok"; }
        @RequestMapping(value = "/animals/{id}/medical-events", method = RequestMethod.POST)
        String medicalEventCreate() { return "ok"; }
        @RequestMapping(value = "/animals/{id}/treatments", method = RequestMethod.POST)
        String treatmentCreate() { return "ok"; }
        @RequestMapping(value = "/animals/{id}/treatments/{treatmentId}/administrations/generate", method = RequestMethod.POST)
        String administrationGenerate() { return "ok"; }
        @RequestMapping(value = "/animals/{id}/treatments/{treatmentId}/administrations/{administrationId}/administer", method = RequestMethod.POST)
        String administrationAdminister() { return "ok"; }
        @RequestMapping(value = "/animals/{id}/documents", method = RequestMethod.POST)
        String documentCreate() { return "ok"; }
        @RequestMapping(value = "/animals/{id}/documents/upload", method = RequestMethod.POST)
        String documentUpload() { return "ok"; }
        @RequestMapping(value = "/users", method = RequestMethod.GET)
        String usersRead() { return "ok"; }
        @RequestMapping(value = "/users", method = RequestMethod.POST)
        String usersCreate() { return "ok"; }
        @RequestMapping(value = "/users/{id}", method = RequestMethod.PUT)
        String usersUpdate() { return "ok"; }
        @RequestMapping(value = "/users/{id}/deactivate", method = RequestMethod.POST)
        String userDeactivate() { return "ok"; }
        @RequestMapping(value = "/users/{id}/reactivate", method = RequestMethod.POST)
        String userReactivate() { return "ok"; }
        @RequestMapping(value = "/users/{id}/password", method = RequestMethod.POST)
        String userResetPassword() { return "ok"; }
        @RequestMapping(value = "/users/me/password", method = RequestMethod.POST)
        String userOwnPassword() { return "ok"; }
        @RequestMapping(value = "/admin/backups/{name}/verify", method = RequestMethod.POST)
        String backupVerify() { return "ok"; }
        @RequestMapping(value = "/tasks", method = RequestMethod.POST)
        String taskCreate() { return "ok"; }
        @RequestMapping(value = "/tasks/sync-medical-reminders", method = RequestMethod.POST)
        String taskSyncReminders() { return "ok"; }
        @RequestMapping(value = "/tasks/{id}/complete", method = RequestMethod.POST)
        String taskComplete() { return "ok"; }
        @RequestMapping(value = "/tasks/{id}/skip", method = RequestMethod.POST)
        String taskSkip() { return "ok"; }
        @RequestMapping(value = "/future-resource/{id}", method = RequestMethod.DELETE)
        String futureDelete() { return "ok"; }
        @RequestMapping(value = "/animals/{id}", method = RequestMethod.PATCH)
        String futurePatch() { return "ok"; }
    }
}
