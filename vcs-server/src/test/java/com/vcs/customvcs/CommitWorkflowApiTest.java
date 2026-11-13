package com.vcs.customvcs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;

import com.vcs.core.CommitService;
import com.vcs.core.ObjectStore;
import com.vcs.core.ReferenceStore;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Exercise HTTP -> service -> engine using disposable repository files.
class CommitWorkflowApiTest {
    @TempDir Path temporary;
    ApiTestWorkspace workspace;
    UUID id;

    @BeforeEach void setup() throws Exception {
        workspace = new ApiTestWorkspace(temporary.resolve("repositories"));
        id = workspace.create();
    }

    @Test void commitUsesStagedBytesAndHistoryLinksBothCommits() throws Exception {
        write("staged version");
        stage();
        write("later edit");
        String first = commit();

        var repository = workspace.open(id);
        CommitService engine = new CommitService(repository);
        var snapshot = engine.readSnapshot(engine.readCommit(first).snapshotId());
        byte[] saved = new ObjectStore(repository).readBlob(snapshot.get("hello.txt"));
        assertEquals("staged version", new String(saved, StandardCharsets.UTF_8));
        workspace.mvc.perform(get("/api/repositories/{id}/status", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staged.modified").isEmpty())
                .andExpect(jsonPath("$.working.modified[0]").value("hello.txt"));

        stage();
        String second = commit();
        workspace.mvc.perform(get("/api/repositories/{id}/commits", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[0].commit.parentId").value(first))
                .andExpect(jsonPath("$[1].id").value(first));
        workspace.mvc.perform(get("/api/repositories/{id}/status", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.working.modified").isEmpty());
    }

    @Test void deletionMustBeStagedAndDoesNotEraseEarlierSnapshots() throws Exception {
        write("original");
        stage();
        String first = commit();
        var repository = workspace.open(id);
        Files.delete(repository.workingDirectory().resolve("hello.txt"));
        stage();
        workspace.mvc.perform(get("/api/repositories/{id}/status", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staged.deleted[0]").value("hello.txt"));
        String second = commit();
        CommitService engine = new CommitService(repository);
        assertTrue(engine.readSnapshot(engine.readCommit(second).snapshotId()).isEmpty());
        assertTrue(engine.readSnapshot(engine.readCommit(first).snapshotId()).containsKey("hello.txt"));
    }

    @Test void emptyHistoryAndCommitPreconditionsHaveExpectedResponses() throws Exception {
        workspace.mvc.perform(get("/api/repositories/{id}/commits", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        workspace.mvc.perform(post("/api/repositories/{id}/commits", id)
                        .contentType(MediaType.APPLICATION_JSON).content(commitBody()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        workspace.mvc.perform(post("/api/repositories/{id}/commits", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"\",\"message\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.author").doesNotExist())
                .andExpect(jsonPath("$.errors.message").exists());
    }

    @Test void stagingRejectsInvalidPathsAndMissingRepositories() throws Exception {
        for (String path : List.of("../outside", ".customvcs/HEAD", ".CUSTOMVCS/HEAD", "C:/outside")) {
            workspace.mvc.perform(post("/api/repositories/{id}/stage", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"path\":\"" + path + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        workspace.mvc.perform(post("/api/repositories/{id}/stage", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"path\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.path").exists());
        workspace.mvc.perform(post("/api/repositories/{id}/stage", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"path\":\"hello.txt\"}"))
                .andExpect(status().isNotFound());
        workspace.mvc.perform(post("/api/repositories/{id}/stage", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"path\":\"missing.txt\"}"))
                .andExpect(status().isConflict());
    }

    private void write(String content) throws Exception {
        workspace.mvc.perform(put("/api/repositories/{id}/files/content", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"hello.txt\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isOk());
    }

    private void stage() throws Exception {
        workspace.mvc.perform(post("/api/repositories/{id}/stage", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"path\":\"hello.txt\"}"))
                .andExpect(status().isNoContent());
    }

    private String commit() throws Exception {
        workspace.mvc.perform(post("/api/repositories/{id}/commits", id)
                        .contentType(MediaType.APPLICATION_JSON).content(commitBody()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isString());
        return new ReferenceStore(workspace.open(id)).currentCommitId();
    }

    private String commitBody() {
        return "{\"author\":\"Test Author\",\"message\":\"Test commit\"}";
    }
}
