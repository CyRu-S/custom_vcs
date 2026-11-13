package com.vcs.customvcs;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.vcs.core.CommitService;
import com.vcs.core.StagingArea;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RepositoryStatusApiTest {
    @TempDir Path temporary;

    @Test void newRepositoryHasAnEmptyStatusAndNoCommit() throws Exception {
        ApiTestWorkspace workspace = new ApiTestWorkspace(temporary.resolve("repositories"));
        var id = workspace.create();
        workspace.mvc.perform(get("/api/repositories/{id}/status", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branch").value("main"))
                .andExpect(jsonPath("$.commitId").isEmpty())
                .andExpect(jsonPath("$.staged.added").isEmpty())
                .andExpect(jsonPath("$.working.untracked").isEmpty());
    }

    @Test void stagedAndUnstagedChangesRemainSeparate() throws Exception {
        ApiTestWorkspace workspace = new ApiTestWorkspace(temporary.resolve("repositories"));
        var id = workspace.create();
        var repository = workspace.open(id);
        Path file = repository.workingDirectory().resolve("file.txt");
        Files.writeString(file, "initial");
        stage(repository);
        new CommitService(repository).createCommit("Author", "Initial");
        Files.writeString(file, "staged change");
        stage(repository);
        Files.writeString(file, "another edit");
        Files.writeString(repository.workingDirectory().resolve("new.txt"), "untracked");
        workspace.mvc.perform(get("/api/repositories/{id}/status", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staged.modified[0]").value("file.txt"))
                .andExpect(jsonPath("$.working.modified[0]").value("file.txt"))
                .andExpect(jsonPath("$.working.untracked[0]").value("new.txt"));
    }

    private void stage(com.vcs.core.Repository repository) throws Exception {
        StagingArea staging = new StagingArea(repository);
        staging.load();
        staging.stagePath(Path.of("file.txt"));
        staging.save();
    }
}
