package com.vcs.cli;

import com.vcs.core.DiffLine;
import com.vcs.core.DiffService;
import com.vcs.core.FileDiff;
import com.vcs.core.Repository;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "diff", mixinStandardHelpOptions = true,
        description = "Show working changes, or staged changes with --staged.")
public class DiffCommand implements Callable<Integer> {
    @Option(names = {"--staged", "--cached"}, description = "Compare the index with the current commit.")
    private boolean staged;

    @Override
    public Integer call() {
        DiffService service = new DiffService(new Repository(Path.of(".")));
        try {
            List<FileDiff> changes = staged ? service.staged() : service.workingTree();
            for (FileDiff file : changes) {
                print(file);
            }
            return 0;
        } catch (IOException | IllegalArgumentException exception) {
            System.err.println("Cannot diff repository: " + exception.getMessage());
            return 1;
        }
    }

    private void print(FileDiff file) {
        System.out.println(file.change().name().toLowerCase(java.util.Locale.ROOT) + ": " + file.path());
        if (file.binary()) {
            System.out.println("Binary content differs.");
            return;
        }
        System.out.println("--- " + (file.change() == FileDiff.Change.ADDED ? "/dev/null" : "a/" + file.path()));
        System.out.println("+++ " + (file.change() == FileDiff.Change.DELETED ? "/dev/null" : "b/" + file.path()));
        if (file.lines().isEmpty()) {
            return;
        }
        // One full-file hunk keeps this initial formatter simple and explicit.
        System.out.printf("@@ -%d,%d +%d,%d @@%n",
                file.oldLineCount() == 0 ? 0 : 1, file.oldLineCount(),
                file.newLineCount() == 0 ? 0 : 1, file.newLineCount());
        for (DiffLine line : file.lines()) {
            char marker = switch (line.kind()) {
                case CONTEXT -> ' ';
                case ADDED -> '+';
                case REMOVED -> '-';
            };
            System.out.print(marker + line.text());
            if (!line.text().endsWith("\n")) {
                System.out.println();
                System.out.println("\\ No newline at end of file");
            }
        }
    }
}
