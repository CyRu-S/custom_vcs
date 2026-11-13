package com.vcs.customvcs;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.vcs.customvcs.controller.WorkspaceController;
import com.vcs.customvcs.exception.ApiExceptionHandler;
import com.vcs.customvcs.service.WorkspaceService;
import com.vcs.customvcs.service.RepositoryVcsService;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WorkspaceApiTest {
    @TempDir Path temporary;

    @Test void workspaceIncludesWorkingFilesAndBothKindsOfChanges() throws Exception {
        ApiTestWorkspace fixture = new ApiTestWorkspace(temporary.resolve("repositories"));
        var id = fixture.create();
        var root = fixture.open(id).workingDirectory();
        Files.writeString(root.resolve("hello.txt"), "staged");
        var staging = new RepositoryVcsService(fixture.access);
        staging.stage(id, "hello.txt");
        Files.writeString(root.resolve("hello.txt"), "working");
        var mvc = MockMvcBuilders.standaloneSetup(new WorkspaceController(new WorkspaceService(fixture.access, fixture.repositories)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/repositories/{id}/workspace", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.files['hello.txt']").value("working"))
                .andExpect(jsonPath("$.changes.length()").value(2));
        staging.unstage(id, "hello.txt");
        assertTrue(new WorkspaceService(fixture.access, fixture.repositories).read(id).changes().stream().noneMatch(WorkspaceService.Change::staged));
    }
}
