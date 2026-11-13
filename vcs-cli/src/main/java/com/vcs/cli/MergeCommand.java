package com.vcs.cli;

import com.vcs.core.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "merge", mixinStandardHelpOptions = true,
        description = "Merge a branch into the current branch, or abort a pending merge.")
public final class MergeCommand implements Callable<Integer> {
    @Parameters(index = "0", arity = "0..1", description = "Source branch.")
    private String branch;
    @Option(names = "--author", description = "Author for a new merge commit.")
    private String author;
    @Option(names = {"-m", "--message"}, description = "Merge commit message.")
    private String message;
    @Option(names = "--abort", description = "Restore the pre-merge snapshot and index.")
    private boolean abort;

    @Override
    public Integer call() {
        MergeService service = new MergeService(new Repository(Path.of(".")));
        try {
            if (abort) {
                if (branch != null || author != null || message != null) {
                    throw new IOException("Use merge --abort without other arguments.");
                }
                service.abort();
                System.out.println("Merge aborted; restored the starting snapshot.");
                return 0;
            }
            if (branch == null) throw new IOException("Specify a branch or --abort.");
            MergeResult result = service.merge(branch, author, message);
            switch (result.kind()) {
                case UP_TO_DATE -> System.out.println("Already up to date.");
                case FAST_FORWARD -> System.out.println("Fast-forward to " + result.commitId());
                case MERGED -> System.out.println("Merge commit " + result.commitId());
                case CONFLICTS -> {
                    System.out.println("Merge paused. Resolve each conflict, then add and commit:");
                    result.conflicts().forEach(path -> System.out.println("  conflict: " + path));
                    System.out.println("Use merge --abort to restore the starting snapshot.");
                    return 1;
                }
            }
            return 0;
        } catch (IOException | IllegalArgumentException exception) {
            System.err.println("Cannot merge: " + exception.getMessage());
            return 1;
        }
    }
}
