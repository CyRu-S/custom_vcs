package com.vcs.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class StageFiveCliTest {
    @TempDir Path root;

    @Test void conflictStatusResolutionHistoryAndAbortWorkAcrossProcesses() throws Exception {
        ok("init");
        write("base\n"); ok("add", "file"); commit("Base"); ok("branch", "feature");
        write("ours\n"); ok("add", "file"); commit("Ours");
        ok("checkout", "feature");
        write("theirs\n"); ok("add", "file"); commit("Theirs");
        ok("checkout", "main");
        assertEquals(1, run("merge", "feature", "--author", "Author").code());
        assertTrue(ok("status").contains("unresolved: file"));
        assertEquals(1, run("commit", "--author", "Author", "-m", "Too early").code());
        assertEquals(1, run("checkout", "feature").code());
        assertEquals(1, run("merge", "--abort", "feature").code());
        ok("merge", "--abort");
        assertEquals("ours\n", Files.readString(root.resolve("file")));
        assertTrue(ok("status").contains("Working tree clean."));
        assertEquals(1, run("merge", "feature", "--author", "Author").code());
        write("resolved\n"); ok("add", "file");
        assertTrue(ok("status").contains("All conflicts resolved"));
        commit("Resolved merge");
        String log = ok("log");
        assertTrue(log.contains("Merge parents:"));
        assertTrue(log.contains("Theirs"));
        assertTrue(log.contains("Ours"));
        assertTrue(ok("merge", "feature").contains("Already up to date"));
        assertTrue(ok("status").contains("Working tree clean."));
    }

    @Test void fastForwardAndAutomaticMergeUseCliOptions() throws Exception {
        ok("init");
        write("a\nb\nc\n"); ok("add", "file"); commit("Base"); ok("branch", "feature");
        ok("checkout", "feature");
        Files.writeString(root.resolve("extra"), "incoming"); ok("add", "extra"); commit("Feature addition");
        ok("checkout", "main");
        assertTrue(ok("merge", "feature").contains("Fast-forward"));
        assertTrue(ok("branch").contains("* main"));
        write("ours\nb\nc\n"); ok("add", "file"); commit("Main update");
        ok("checkout", "feature");
        write("a\nb\ntheirs\n"); ok("add", "file"); commit("Feature update");
        ok("checkout", "main");
        assertTrue(ok("merge", "feature", "--author", "Author", "-m", "Automatic join")
                .contains("Merge commit"));
        assertEquals("ours\nb\ntheirs\n", Files.readString(root.resolve("file")));
        assertTrue(ok("log").contains("Automatic join"));
    }

    private void write(String text) throws Exception { Files.writeString(root.resolve("file"), text); }
    private void commit(String message) throws Exception { ok("commit", "--author", "Author", "-m", message); }
    private String ok(String... args) throws Exception {
        Result result = run(args);
        assertEquals(0, result.code(), String.join(" ", args) + "\n" + result.output());
        return result.output();
    }
    private Result run(String... args) throws Exception {
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, VcsCommand.class.getName()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("CLI timed out"); }
        return new Result(process.exitValue(), new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }
    private record Result(int code, String output) { }
}
