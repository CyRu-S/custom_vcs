package com.vcs.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.vcs.core.ObjectStore;
import com.vcs.core.Repository;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name="cat-object", description="Output a stored blob's content.")
public class CatObjectCommand implements Callable<Integer>{

    @Parameters(index="0", description="Object identifier.")
    private String objectId;

    @Override 
    public Integer call() {
        // Read from the repository in the current folder.
        ObjectStore store = new ObjectStore(new Repository(Path.of(".")));

        try {
            byte[] content = store.readBlob(objectId);
            // Output the original bytes without adding a newline.
            System.out.write(content);
            System.out.flush();
            return 0;
        } catch (IOException | IllegalArgumentException exception) {
            System.err.println("Cannot read object: " + exception.getMessage());
            return 1;
        }
    }

}
