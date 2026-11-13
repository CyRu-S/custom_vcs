package com.vcs.cli;

import com.vcs.core.Commit;
import com.vcs.core.CommitEntry;
import com.vcs.core.CommitService;
import com.vcs.core.Repository;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "log", description = "Show the current branch's commit history.")
public class LogCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        CommitService service = new CommitService(new Repository(Path.of(".")));

        try {
            List<CommitEntry> history = service.history();
            if (history.isEmpty()) {
                System.out.println("No commits yet.");
                return 0;
            }

            // Traversal stays in the engine; the CLI only formats each entry.
            for (CommitEntry entry : history) {
                Commit commit = entry.commit();
                if (commit.secondParentId() != null) {
                    System.out.println("Merge parents: " + commit.parentId() + " " + commit.secondParentId());
                }
                System.out.printf(
                        "commit %s%nAuthor: %s%nDate: %s%n%n    %s%n%n",
                        entry.id(), commit.author(), commit.timestamp(), commit.message());
            }

            return 0;
        } catch (IOException exception) {
            System.err.println("Cannot read history: " + exception.getMessage());
            return 1;
        }
    }
}
