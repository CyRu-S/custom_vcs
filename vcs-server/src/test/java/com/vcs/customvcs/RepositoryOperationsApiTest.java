package com.vcs.customvcs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;

import com.vcs.core.CheckoutService;
import com.vcs.core.CommitService;
import com.vcs.core.ReferenceStore;
import com.vcs.core.Repository;
import com.vcs.core.StagingArea;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RepositoryOperationsApiTest {
    @TempDir Path temporary;
    ApiTestWorkspace workspace;
    UUID id;
    Repository repository;

    @BeforeEach void setup() throws Exception {
        workspace = new ApiTestWorkspace(temporary.resolve("repositories"));
        id = workspace.create();
        repository = workspace.open(id);
    }

    @Test void unbornMainIsVisibleButNewBranchesNeedACommit() throws Exception {
        workspace.mvc.perform(get("/api/repositories/{id}/branches", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("main"))
                .andExpect(jsonPath("$[0].current").value(true));
        branch("feature", 409);
        workspace.mvc.perform(get("/api/repositories/{id}/merge", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inProgress").value(false));
    }

    @Test void branchCreationAndCheckoutReuseTheEngine() throws Exception {
        commit("base\n");
        branch("feature", 201);
        branch("feature", 409);
        workspace.mvc.perform(post("/api/repositories/{id}/checkout", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"feature\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("feature"));
        assertEquals("feature", new ReferenceStore(repository).currentBranchName());
        workspace.mvc.perform(post("/api/repositories/{id}/checkout", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"missing\"}"))
                .andExpect(status().isNotFound());
    }

    @Test void checkoutRejectsDirtyWorkWithoutMovingHead() throws Exception {
        commit("base\n");
        branch("feature", 201);
        Files.writeString(repository.workingDirectory().resolve("file.txt"), "dirty\n");
        workspace.mvc.perform(post("/api/repositories/{id}/checkout", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"feature\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        assertEquals("main", new ReferenceStore(repository).currentBranchName());
        assertEquals("dirty\n", Files.readString(repository.workingDirectory().resolve("file.txt")));
    }

    @Test void stagedAndWorkingDiffsUseDifferentBaselines() throws Exception {
        commit("base\n");
        Files.writeString(repository.workingDirectory().resolve("file.txt"), "changed\n");
        workspace.mvc.perform(get("/api/repositories/{id}/diff", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].change").value("MODIFIED"));
        workspace.mvc.perform(get("/api/repositories/{id}/diff", id).param("scope", "staged"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        stage();
        workspace.mvc.perform(get("/api/repositories/{id}/diff", id).param("scope", "staged"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].path").value("file.txt"));
        workspace.mvc.perform(get("/api/repositories/{id}/diff", id).param("scope", "wrong"))
                .andExpect(status().isBadRequest());
    }

    @Test void fastForwardDoesNotSwitchTheCurrentBranch() throws Exception {
        commit("base\n");
        branch("feature", 201);
        new CheckoutService(repository).checkout("feature");
        String target = commit("incoming\n");
        new CheckoutService(repository).checkout("main");
        workspace.mvc.perform(post("/api/repositories/{id}/merge", id)
                        .contentType(MediaType.APPLICATION_JSON).content(mergeRequest()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("FAST_FORWARD"))
                .andExpect(jsonPath("$.commitId").value(target));
        assertEquals("main", new ReferenceStore(repository).currentBranchName());
        assertEquals("incoming\n", Files.readString(repository.workingDirectory().resolve("file.txt")));
    }

    @Test void conflictsAreVisibleAndAbortRestoresTheOriginalSnapshot() throws Exception {
        commit("base\n");
        branch("feature", 201);
        String original = commit("ours\n");
        new CheckoutService(repository).checkout("feature");
        commit("theirs\n");
        new CheckoutService(repository).checkout("main");
        workspace.mvc.perform(post("/api/repositories/{id}/merge", id)
                        .contentType(MediaType.APPLICATION_JSON).content(mergeRequest()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("CONFLICTS"))
                .andExpect(jsonPath("$.conflicts[0]").value("file.txt"));
        workspace.mvc.perform(get("/api/repositories/{id}/merge", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inProgress").value(true));
        workspace.mvc.perform(post("/api/repositories/{id}/merge", id)
                        .contentType(MediaType.APPLICATION_JSON).content(mergeRequest()))
                .andExpect(status().isConflict());
        workspace.mvc.perform(post("/api/repositories/{id}/merge/abort", id))
                .andExpect(status().isNoContent());
        assertEquals(original, new ReferenceStore(repository).currentCommitId());
        assertEquals("ours\n", Files.readString(repository.workingDirectory().resolve("file.txt")));
        workspace.mvc.perform(post("/api/repositories/{id}/merge/abort", id))
                .andExpect(status().isConflict());
    }

    @Test void invalidBranchNamesAreRejectedBeforeFilesystemAccess() throws Exception {
        workspace.mvc.perform(post("/api/repositories/{id}/branches", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"../escape\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());
        workspace.mvc.perform(post("/api/repositories/{id}/branches", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"CON\"}"))
                .andExpect(status().isBadRequest());
    }

    private void branch(String name, int expectedStatus) throws Exception {
        workspace.mvc.perform(post("/api/repositories/{id}/branches", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    // Seed engine state directly; the learner's staging and commit endpoints do not exist yet.
    private String commit(String content) throws Exception {
        Files.writeString(repository.workingDirectory().resolve("file.txt"), content);
        stage();
        return new CommitService(repository).createCommit("Test Author", "Test commit");
    }

    private void stage() throws Exception {
        StagingArea staging = new StagingArea(repository);
        staging.load();
        staging.stagePath(Path.of("file.txt"));
        staging.save();
    }

    private String mergeRequest() {
        return "{\"sourceBranch\":\"feature\",\"author\":\"Test Author\"}";
    }
}
