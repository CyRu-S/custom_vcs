package com.vcs.customvcs.controller;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import com.vcs.customvcs.service.WorkspaceService;

@RestController
@RequestMapping("/api/repositories/{id}")
public class WorkspaceController {
    private final WorkspaceService workspace;
    public WorkspaceController(WorkspaceService workspace) { this.workspace = workspace; }

    @GetMapping("/workspace")
    public WorkspaceService.View read(@PathVariable("id") UUID id) throws IOException { return workspace.read(id); }

    // Historical file content is fetched only when someone opens a commit.
    @GetMapping("/commits/{commitId}/files")
    public Map<String, String> snapshot(@PathVariable("id") UUID id,
            @PathVariable("commitId") String commitId) throws IOException {
        return workspace.snapshot(id, commitId);
    }
}
