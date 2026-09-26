package com.shelter.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired MockMvc mvc;

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCanReadButCannotMutateAnimals() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/api/animals")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/animals")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COORDINATOR")
    void coordinatorCanManageAnimalsButCannotWriteMedicalRecords() throws Exception {
        mvc.perform(request(HttpMethod.POST, "/api/animals")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/animals/1/vaccinations")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VETERINARIAN")
    void veterinarianCanManageMedicalRecordsButCannotManageUsers() throws Exception {
        mvc.perform(request(HttpMethod.POST, "/api/animals/1/vaccinations")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.GET, "/api/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void volunteerCanManageTasksButCannotWriteAnimals() throws Exception {
        mvc.perform(request(HttpMethod.POST, "/api/tasks")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.PUT, "/api/animals/1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanManageUsersAndMedicalRecords() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/api/users")).andExpect(status().isOk());
        mvc.perform(request(HttpMethod.POST, "/api/animals/1/vaccinations")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void unknownMutatingEndpointFailsClosed() throws Exception {
        mvc.perform(request(HttpMethod.DELETE, "/api/future-resource/1")).andExpect(status().isForbidden());
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

        @RequestMapping(value = "/users", method = RequestMethod.GET)
        String usersRead() { return "ok"; }

        @RequestMapping(value = "/tasks", method = RequestMethod.POST)
        String taskCreate() { return "ok"; }

        @RequestMapping(value = "/future-resource/{id}", method = RequestMethod.DELETE)
        String futureDelete() { return "ok"; }
    }
}
