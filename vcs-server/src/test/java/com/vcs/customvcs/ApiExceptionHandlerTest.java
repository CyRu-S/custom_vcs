package com.vcs.customvcs;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.vcs.customvcs.exception.ApiExceptionHandler;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiExceptionHandlerTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new ApiExceptionHandler()).build();

    @Test void storageFailuresDoNotExposeLocalPaths() throws Exception {
        mvc.perform(get("/test/storage"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Storage operation failed"))
                .andExpect(content().string(not(containsString("private-directory"))));
    }

    @Test void unexpectedFailuresDoNotExposeDatabaseDetails() throws Exception {
        mvc.perform(get("/test/database"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("The operation could not be completed."))
                .andExpect(content().string(not(containsString("private-table"))));
    }

    @RestController
    static class FailingController {
        @GetMapping("/test/storage")
        public void storage() throws IOException {
            throw new IOException("private-directory/objects could not be opened");
        }

        @GetMapping("/test/database")
        public void database() {
            throw new IllegalStateException("private-table database failure");
        }
    }
}
