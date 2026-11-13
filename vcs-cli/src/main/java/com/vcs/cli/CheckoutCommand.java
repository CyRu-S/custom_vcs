package com.vcs.cli;

import com.vcs.core.CheckoutService;
import com.vcs.core.Repository;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "checkout", mixinStandardHelpOptions = true,
        description = "Restore a branch's files and index, then select it.")
public class CheckoutCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Existing branch to select.")
    private String branch;

    @Override
    public Integer call() {
        try {
            new CheckoutService(new Repository(Path.of("."))).checkout(branch);
            System.out.println("Switched to branch " + branch);
            return 0;
        } catch (IOException | IllegalArgumentException exception) {
            System.err.println("Cannot checkout branch: " + exception.getMessage());
            return 1;
        }
    }
}
