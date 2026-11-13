package com.vcs.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.vcs.core.Repository;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command (name = "init", description = "Initialize a local repository.")
public class InitCommand implements Callable<Integer>{

    // No directory argument means the current folder.
    @Parameters (index = "0", arity="0..1", defaultValue=".", description = "Directory to initialize.")
    private Path directory;

    @Override
    public Integer call() {
        Repository repository = new Repository(directory);

        try {
            // Let the core create the repository.
            repository.initialize();
            System.out.println("Repository ready at " + repository.metadataDirectory());

            return 0; // Success.
        } catch (IOException exception) {
            // Show the filesystem error to the user.
            System.err.println("Cannot initialize repository: " + exception.getMessage());

            return 1; // Failure.
        }
    }
}
