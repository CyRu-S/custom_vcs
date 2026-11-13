package com.vcs.customvcs.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vcs.customvcs.dto.FileContentResponse;
import com.vcs.customvcs.dto.FileEntryResponse;
import com.vcs.customvcs.dto.WriteFileRequest;
import com.vcs.customvcs.service.WorkingFileService;

import jakarta.validation.Valid;

// Query parameters let nested file paths travel without conflicting with controller routes.
@RestController
@RequestMapping("/api/repositories/{id}/files")
public class WorkingFileController {
    private final WorkingFileService files;

    public WorkingFileController(WorkingFileService files) {
        this.files = files;
    }

    @GetMapping
    public List<FileEntryResponse> list(@PathVariable("id") UUID id,
            @RequestParam(name = "path", defaultValue = "") String path) throws IOException {
        return files.list(id, path);
    }

    @GetMapping("/content")
    public FileContentResponse read(@PathVariable("id") UUID id,
            @RequestParam("path") String path) throws IOException {
        return files.read(id, path);
    }

    @PutMapping("/content")
    public FileContentResponse write(@PathVariable("id") UUID id,
            @Valid @RequestBody WriteFileRequest request) throws IOException {
        return files.write(id, request);
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id,
            @RequestParam("path") String path) throws IOException {
        files.delete(id, path);
        return ResponseEntity.noContent().build();
    }
}
