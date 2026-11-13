package com.vcs.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import com.vcs.core.Repository;
import com.vcs.core.StagingArea;

import picocli.CommandLine.Command;

@Command(name="ls-index", description="List saved staging entries.")
public class ListIndexCommand implements Callable<Integer>{

    @Override
    public Integer call() {
        StagingArea stagingArea = new StagingArea(
            new Repository(Path.of("."))
        );

        try {
            // Read the saved index, then show each blob ID and file path.
            stagingArea.load();

            for (Map.Entry<String, String> entry : stagingArea.entries().entrySet()) {
                System.out.println(entry.getValue() + " " + entry.getKey());
            }

            return 0;
        } catch (IOException exception) {
            System.err.println("Cannot read index: " + exception.getMessage());
            return 1;
        }
    }
}
