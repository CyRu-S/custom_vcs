package com.vcs.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import com.vcs.core.Repository;
import com.vcs.core.StagedStatus;
import com.vcs.core.StatusService;
import com.vcs.core.WorkingTreeStatus;
import com.vcs.core.MergeState;
import com.vcs.core.MergeStateStore;

import picocli.CommandLine.Command;

@Command(name="status", description="Show staged, unstaged, and untracked changes.")
public class StatusCommand implements Callable<Integer>{

    @Override 
    public Integer call() {
        StatusService service = new StatusService(
            new Repository(Path.of("."))
        );

        try {
            // The core checks files; this command only displays the result.
            WorkingTreeStatus working = service.inspect();
            StagedStatus staged = service.inspectStaged();
            MergeState merge = new MergeStateStore(new Repository(Path.of("."))).load();
            if (merge != null) {
                System.out.println("Merge in progress from " + merge.sourceBranch() + ":");
                if (merge.conflicts().isEmpty()) {
                    System.out.println("All conflicts resolved; commit to finish the merge.");
                } else {
                    printPaths("unresolved", merge.conflicts());
                    System.out.println("Resolve files, then add each path (including chosen deletions).");
                }
            }

            boolean hasStaged = !staged.added().isEmpty()
                    || !staged.modified().isEmpty() || !staged.deleted().isEmpty();
            boolean hasUnstaged = !working.modified().isEmpty()
                    || !working.deleted().isEmpty();
            boolean hasUntracked = !working.untracked().isEmpty();

            if (hasStaged) {
                System.out.println("Changes staged for commit:");
                printPaths("new file", staged.added());
                printPaths("modified", staged.modified());
                printPaths("deleted", staged.deleted());
            }

            if (hasUnstaged) {
                System.out.println("Changes not staged:");
                printPaths("modified", working.modified());
                printPaths("deleted", working.deleted());
            }

            if (hasUntracked) {
                System.out.println("Untracked files:");
                printPaths("untracked", working.untracked());
            }

            if (merge == null && !hasStaged && !hasUnstaged && !hasUntracked) {
                System.out.println("Working tree clean.");
            }

            return 0;
            
        } catch (IOException exception) {
            System.err.println("Cannot inspect repository: " + exception.getMessage());

            return 1;
        }
    }
    private void printPaths(String label, List<String> paths) {
        for (String path : paths) {
            System.out.println("  " + label + ": " + path);
        }
    }
}
