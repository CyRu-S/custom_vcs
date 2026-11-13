package com.vcs.cli;

import com.vcs.core.ReferenceStore;
import com.vcs.core.Repository;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "branch", description = "List branches or create one at the current commit.")
public class BranchCommand implements Callable<Integer> {
    @Parameters(index = "0", arity = "0..1", description = "Name of the branch to create.")
    private String name;

    @Override
    public Integer call() {
        ReferenceStore references = new ReferenceStore(new Repository(Path.of(".")));

        try {
            if (name == null) {
                String current = references.currentBranchName();
                for (String branch : references.listBranches()) {
                    System.out.println((branch.equals(current) ? "* " : "  ") + branch);
                }
            } else {
                references.createBranch(name);
                System.out.println("Created branch " + name);
            }
            return 0;
        } catch (FileAlreadyExistsException exception) {
            System.err.println("Branch already exists: " + name);
            return 1;
        } catch (IOException exception) {
            System.err.println("Cannot process branches: " + exception.getMessage());
            return 1;
        }
    }
}
