package com.vcs.customvcs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;

import com.vcs.customvcs.dto.CreateRepositoryRequest;
import com.vcs.customvcs.entity.RepositoryEntity;
import com.vcs.customvcs.entity.RepositoryStatus;
import com.vcs.customvcs.service.RepositoryService;
import com.vcs.customvcs.service.RepositoryStorage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RepositoryApiTest {
    @TempDir Path temporary;
    ApiTestWorkspace workspace;

    @BeforeEach void setup() {
        workspace = new ApiTestWorkspace(temporary.resolve("repositories"));
    }

    @Test void creationUsesTheCanonicalRouteAndReturnsReadyMetadata() throws Exception {
        workspace.mvc.perform(post("/api/repositories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"backend-demo\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("READY"));
        UUID id = workspace.rows.keySet().iterator().next();
        assertTrue(Files.exists(workspace.open(id).metadataDirectory().resolve("HEAD")));
        workspace.mvc.perform(get("/api/repositories/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test void listingPreservesPageMetadataAndStableSorting() throws Exception {
        RepositoryEntity entity = RepositoryEntity.builder().id(UUID.randomUUID())
                .name("demo").createdAt(Instant.EPOCH).status(RepositoryStatus.READY).build();
        when(workspace.metadata.findAllByOwnerId(any(UUID.class), any(Pageable.class))).thenAnswer(call ->
                new PageImpl<>(List.of(entity), call.getArgument(1), 3));
        workspace.mvc.perform(get("/api/repositories").param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.repositories[0].name").value("demo"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3));
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(workspace.metadata).findAllByOwnerId(any(UUID.class), captor.capture());
        assertEquals(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
                captor.getValue().getSort());
    }

    @Test void invalidBodiesAndParametersProduceProblemDetails() throws Exception {
        workspace.mvc.perform(post("/api/repositories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());
        workspace.mvc.perform(post("/api/repositories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request body"));
        workspace.mvc.perform(get("/api/repositories/not-a-uuid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        for (String size : List.of("0", "101", "abc")) {
            workspace.mvc.perform(get("/api/repositories").param("size", size))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        workspace.mvc.perform(get("/api/repositories").param("page", "-1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").exists());
    }

    @Test void standardMvcErrorsKeepTheirHttpStatus() throws Exception {
        workspace.mvc.perform(put("/api/repositories"))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.status").value(405));
        workspace.mvc.perform(post("/api/repositories").contentType(MediaType.TEXT_PLAIN).content("bad"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.status").value(415));
    }

    @Test void renameDoesNotMoveTheStorageDirectory() throws Exception {
        UUID id = workspace.create();
        Path original = workspace.open(id).workingDirectory();
        workspace.mvc.perform(patch("/api/repositories/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"renamed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("renamed"));
        assertEquals(original, workspace.open(id).workingDirectory());
    }

    @Test void deletionRemovesStorageAndMetadata() throws Exception {
        UUID id = workspace.create();
        Path directory = workspace.open(id).workingDirectory();
        Files.writeString(directory.resolve("draft.txt"), "draft");
        workspace.mvc.perform(delete("/api/repositories/{id}", id)).andExpect(status().isNoContent());
        assertFalse(Files.exists(directory));
        assertFalse(workspace.rows.containsKey(id));
        workspace.mvc.perform(get("/api/repositories/{id}", id)).andExpect(status().isNotFound());
    }

    @Test void failedDeletionCanBeRetriedWithoutPublishingReady() throws Exception {
        UUID id = workspace.create();
        RepositoryStorage failingStorage = mock(RepositoryStorage.class);
        doThrow(new IOException("private filesystem detail"))
                .doNothing().when(failingStorage).delete(id);
        RepositoryService service = new RepositoryService(workspace.metadata, failingStorage, workspace.access);
        assertThrows(IOException.class, () -> service.delete(id));
        assertEquals(RepositoryStatus.DELETING, workspace.rows.get(id).getStatus());
        service.delete(id);
        assertFalse(workspace.rows.containsKey(id));
    }

    @Test void failedInitializationRetainsFailedMetadata() throws Exception {
        RepositoryStorage failingStorage = mock(RepositoryStorage.class);
        doThrow(new IOException("storage unavailable")).when(failingStorage).initialize(any());
        RepositoryService service = new RepositoryService(workspace.metadata, failingStorage, workspace.access);
        assertThrows(IOException.class, () -> service.create(new CreateRepositoryRequest("demo")));
        assertEquals(RepositoryStatus.FAILED, workspace.rows.values().iterator().next().getStatus());
    }

    @Test void incompleteRepositoriesCannotBeUsedOrDeletedDuringCreation() throws Exception {
        UUID id = workspace.create();
        workspace.rows.get(id).setStatus(RepositoryStatus.INITIALIZING);
        workspace.mvc.perform(get("/api/repositories/{id}/files", id)).andExpect(status().isConflict());
        workspace.mvc.perform(delete("/api/repositories/{id}", id)).andExpect(status().isConflict());
        workspace.mvc.perform(patch("/api/repositories/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"racing-rename\"}"))
                .andExpect(status().isConflict());
        assertTrue(Files.exists(temporary.resolve("repositories").resolve(id.toString())));
    }
}
