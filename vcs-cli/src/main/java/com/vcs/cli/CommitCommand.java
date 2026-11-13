package com.vcs.cli;

import com.vcs.core.CommitService;
import com.vcs.core.Repository;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "commit", description = "Commit the saved staging snapshot.")
public class CommitCommand implements Callable<Integer> {
    @Option(names = {"-m", "--message"}, required = true,
            description = "Commit message.")
    private String message;

    @Option(names = "--author", required = true,
            description = "Commit author.")
    private String author;

    @Override
    public Integer call() {
        CommitService service = new CommitService(
                new Repository(Path.of(".")));

        try {
            // The engine creates the objects and advances the current branch.
            String commitId = service.createCommit(author, message);
            System.out.println("Created commit " + commitId);
            return 0;
        } catch (IOException | IllegalArgumentException exception) {
            System.err.println("Cannot create commit: " + exception.getMessage());
            return 1;
        }
    }
}
