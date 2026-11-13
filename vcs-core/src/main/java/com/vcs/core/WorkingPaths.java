package com.vcs.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;

// Stored paths are repository-relative names, never arbitrary filesystem destinations.
final class WorkingPaths {
    private WorkingPaths() {
    }

    static Path resolve(Path root, String name) throws IOException {
        try {
            Path relative = Path.of(name);
            Path target = root.resolve(relative).normalize();
            if (relative.getRoot() != null || target.equals(root)
                    || !target.startsWith(root)
                    || target.startsWith(root.resolve(".customvcs"))) {
                throw new IOException("Invalid snapshot path: " + name);
            }
            if (!root.relativize(target).toString().replace('\\', '/').equals(name)) {
                throw new IOException("Noncanonical snapshot path: " + name);
            }

            // Reject redirects before checkout writes or diff reads working content.
            for (Path cursor = target; !cursor.equals(root); cursor = cursor.getParent()) {
                if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)
                        && (Files.isSymbolicLink(cursor) || !cursor.toRealPath().equals(cursor))) {
                    throw new IOException("Redirected working path: " + cursor);
                }
            }
            return target;
        } catch (InvalidPathException exception) {
            throw new IOException("Invalid snapshot path: " + name, exception);
        }
    }
}
