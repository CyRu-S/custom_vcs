package com.vcs.customvcs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;

import com.vcs.customvcs.dto.WriteFileRequest;
import com.vcs.customvcs.service.WorkingFileService;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WorkingFilesApiTest {
    @TempDir Path temporary;
    ApiTestWorkspace workspace;
    UUID id;
    Path root;

    @BeforeEach void setup() throws Exception {
        workspace = new ApiTestWorkspace(temporary.resolve("repositories"));
        id = workspace.create();
        root = workspace.open(id).workingDirectory();
    }

    @Test void editingRoundTripsUtf8WithoutStagingAndKeepsMetadataHidden() throws Exception {
        workspace.mvc.perform(put("/api/repositories/{id}/files/content", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"src/hello.txt\",\"content\":\"Hello \\u03bb\\n\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.path").value("src/hello.txt"));
        workspace.mvc.perform(get("/api/repositories/{id}/files/content", id)
                        .param("path", "src/hello.txt"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("Hello λ\n"));
        workspace.mvc.perform(get("/api/repositories/{id}/files", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("src"));
        assertFalse(Files.exists(root.resolve(".customvcs/index")));
    }

    @Test void traversalMetadataAndWindowsAliasesAreRejected() throws Exception {
        String head = Files.readString(root.resolve(".customvcs/HEAD"));
        for (String path : List.of("../outside", ".customvcs/HEAD", ".CUSTOMVCS/HEAD",
                "src/../../outside", "C:/secret", "src\\secret", "/absolute", "src//file",
                "src/./file", "CON", "aux.txt", "file:stream", "trailing.", "trailing ")) {
            workspace.mvc.perform(get("/api/repositories/{id}/files/content", id).param("path", path))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(head, Files.readString(root.resolve(".customvcs/HEAD")));
        assertFalse(Files.exists(temporary.resolve("outside")));
    }

    @Test void linkedParentsCannotExposeOrOverwriteOutsideFiles() throws Exception {
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "private");
        try {
            Files.createSymbolicLink(root.resolve("link"), outside);
        } catch (java.io.IOException | UnsupportedOperationException exception) {
            assumeTrue(false, "Creating links requires OS support and permission.");
        }
        workspace.mvc.perform(get("/api/repositories/{id}/files/content", id)
                        .param("path", "link/secret.txt"))
                .andExpect(status().isBadRequest());
        WorkingFileService files = new WorkingFileService(workspace.access);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> files.write(id, new WriteFileRequest("link/secret.txt", "overwrite")));
        assertEquals("private", Files.readString(outside.resolve("secret.txt")));
    }

    @Test void binaryAndOversizedFilesCannotBeLoadedIntoTheTextEditor() throws Exception {
        Files.write(root.resolve("binary"), new byte[] {(byte) 0xff, 0});
        workspace.mvc.perform(get("/api/repositories/{id}/files/content", id).param("path", "binary"))
                .andExpect(status().is(422));
        Files.write(root.resolve("large"), new byte[1_048_577]);
        workspace.mvc.perform(get("/api/repositories/{id}/files/content", id).param("path", "large"))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test void deleteRequiresASingleRegularFile() throws Exception {
        Files.createDirectory(root.resolve("directory"));
        workspace.mvc.perform(delete("/api/repositories/{id}/files", id).param("path", "directory"))
                .andExpect(status().isConflict());
        Files.writeString(root.resolve("draft"), "draft");
        workspace.mvc.perform(delete("/api/repositories/{id}/files", id).param("path", "draft"))
                .andExpect(status().isNoContent());
        workspace.mvc.perform(get("/api/repositories/{id}/files/content", id).param("path", "draft"))
                .andExpect(status().isNotFound());
        workspace.mvc.perform(get("/api/repositories/{id}/files/content", id))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test void missingRepositoryIsNotTreatedAsAStorageError() throws Exception {
        workspace.mvc.perform(get("/api/repositories/{id}/files", UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }
}
