package com.vcs.customvcs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vcs.core.Repository;
import com.vcs.customvcs.config.StorageProperties;
import com.vcs.customvcs.controller.RepositoryController;
import com.vcs.customvcs.controller.RepositoryOperationsController;
import com.vcs.customvcs.controller.RepositoryVcsController;
import com.vcs.customvcs.controller.WorkingFileController;
import com.vcs.customvcs.dto.CreateRepositoryRequest;
import com.vcs.customvcs.entity.RepositoryEntity;
import com.vcs.customvcs.exception.ApiExceptionHandler;
import com.vcs.customvcs.repository.RepositoryMetadataRepository;
import com.vcs.customvcs.service.RepositoryAccess;
import com.vcs.customvcs.service.RepositoryOperationsService;
import com.vcs.customvcs.service.RepositoryService;
import com.vcs.customvcs.service.RepositoryStorage;
import com.vcs.customvcs.service.RepositoryVcsService;
import com.vcs.customvcs.security.CurrentUser;
import com.vcs.customvcs.security.SessionUser;
import com.vcs.customvcs.service.WorkingFileService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Mock only the metadata boundary; HTTP requests still exercise the real engine and files.
final class ApiTestWorkspace {
    final Map<UUID, RepositoryEntity> rows = new HashMap<>();
    final RepositoryMetadataRepository metadata = mock(RepositoryMetadataRepository.class);
    final RepositoryStorage storage;
    final RepositoryService repositories;
    final RepositoryAccess access;
    final MockMvc mvc;

    ApiTestWorkspace(Path root) {
        storage = new RepositoryStorage(new StorageProperties(root));
        CurrentUser current = mock(CurrentUser.class);
        when(current.get()).thenReturn(new SessionUser(UUID.randomUUID(), "Test Author", "test@example.test"));
        access = new RepositoryAccess(metadata, storage, current);
        repositories = new RepositoryService(metadata, storage, access);
        when(metadata.findById(any(UUID.class))).thenAnswer(call ->
                Optional.ofNullable(rows.get(call.getArgument(0))));
        when(metadata.findByIdAndOwnerId(any(UUID.class), any(UUID.class))).thenAnswer(call -> {
            RepositoryEntity entity = rows.get(call.getArgument(0));
            return entity != null && call.getArgument(1).equals(entity.getOwnerId())
                    ? Optional.of(entity) : Optional.empty();
        });
        when(metadata.saveAndFlush(any(RepositoryEntity.class))).thenAnswer(call -> {
            RepositoryEntity entity = call.getArgument(0);
            rows.put(entity.getId(), entity);
            return entity;
        });
        doAnswer(call -> { rows.remove(call.getArgument(0)); return null; })
                .when(metadata).deleteById(any(UUID.class));
        mvc = MockMvcBuilders.standaloneSetup(new RepositoryController(repositories),
                new WorkingFileController(new WorkingFileService(access)),
                new RepositoryOperationsController(new RepositoryOperationsService(access)),
                new RepositoryVcsController(new RepositoryVcsService(access)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    UUID create() throws IOException {
        return repositories.create(new CreateRepositoryRequest("test-repository")).id();
    }

    Repository open(UUID id) throws IOException {
        return storage.open(id);
    }
}
