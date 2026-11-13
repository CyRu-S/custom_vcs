package com.vcs.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.vcs.core.ObjectStore;
import com.vcs.core.Repository;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name="store", description="Store a file as a blob object.")
public class StoreCommand implements Callable<Integer>{

    @Parameters(index="0", description="File to store.")
    private Path file;

    @Override 
    public Integer call() {
        // Use the current folder as the repository.
        Repository repository = new Repository(Path.of("."));
        ObjectStore store = new ObjectStore(repository);

        try {
            // Read the file bytes without changing them.
            byte[] content = Files.readAllBytes(file);
            // Store the blob and print its hash.
            String objectId = store.writeBlob(content);
            System.out.println(objectId);
            return 0;
        } catch (IOException exception) {
            System.err.println("Cannot store file: " + exception.getMessage());
            return 1;
        }
    }
}
