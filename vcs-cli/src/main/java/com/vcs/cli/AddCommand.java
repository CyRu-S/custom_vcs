package com.vcs.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.vcs.core.Repository;
import com.vcs.core.StagingArea;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name="add", description="Stage a file for the next commit.")
public class AddCommand implements Callable<Integer>{

    @Parameters(index="0", description="File to stage.")
    private Path file;

    @Override 
    public Integer call() {
        StagingArea stagingArea = new StagingArea(
            new Repository(Path.of("."))
        );

        try {
            // Keep earlier entries, stage this file, then save the updated index.
            stagingArea.load();
            stagingArea.stagePath(file);
            stagingArea.save();
            System.out.println("Staged "  + file);
            return 0;
        } catch (IOException exception) {
            System.err.println("Cannot stage file: " + exception.getMessage());
            return 1;
        }
    }
}
