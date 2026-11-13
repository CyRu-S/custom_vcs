package com.vcs.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class StageFourCliTest {
    @TempDir Path workingDirectory;

    @Test
    void realCliProcessesCreateBranchesCheckoutAndPrintBothDiffModes() throws Exception {
        ok("init");
        Files.writeString(workingDirectory.resolve("hello.txt"), "main line\n");
        ok("add", "hello.txt");
        ok("commit", "--author", "CLI Author", "-m", "Initial commit");
        ok("branch", "feature");
        assertEquals(1, run("branch", "feature").code());
        assertTrue(ok("branch").contains("* main"));
        ok("checkout", "feature");

        Files.writeString(workingDirectory.resolve("hello.txt"), "feature line");
        String workingDiff = ok("diff");
        assertTrue(workingDiff.contains("-main line"));
        assertTrue(workingDiff.contains("+feature line"));
        assertTrue(workingDiff.contains("\\ No newline at end of file"));
        assertTrue(ok("diff", "--staged").isEmpty());
        assertEquals(1, run("checkout", "main").code());

        ok("add", "hello.txt");
        assertTrue(ok("diff", "--staged").contains("+feature line"));
        assertTrue(ok("diff").isEmpty());
        ok("commit", "--author", "CLI Author", "-m", "Feature commit");
        ok("checkout", "main");
        assertEquals("main line\n", Files.readString(workingDirectory.resolve("hello.txt")));
        assertFalse(ok("log").contains("Feature commit"));
        ok("checkout", "feature");
        assertEquals("feature line", Files.readString(workingDirectory.resolve("hello.txt")));
        assertTrue(ok("log").contains("Feature commit"));
        assertTrue(ok("status").contains("Working tree clean."));
    }

    private String ok(String... arguments) throws Exception {
        Result result = run(arguments);
        assertEquals(0, result.code(), String.join(" ", arguments) + "\n" + result.output());
        return result.output();
    }

    private Result run(String... arguments) throws Exception {
        // A separate JVM with its own cwd exercises the actual command wiring.
        String classpath = System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path"));
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, VcsCommand.class.getName()));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(workingDirectory.toFile())
                .redirectErrorStream(true).start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("CLI process timed out: " + String.join(" ", arguments));
        }
        return new Result(process.exitValue(),
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    private record Result(int code, String output) { }
}
