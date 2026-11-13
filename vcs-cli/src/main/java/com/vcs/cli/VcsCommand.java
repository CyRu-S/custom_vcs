package com.vcs.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "vcs", 
        mixinStandardHelpOptions = true,
        version = "vcs 0.0.1-SNAPSHOT",
        description = "A version control system built from scratch.",
        // Register commands here so Picocli can find them.
        subcommands= {
            InitCommand.class, 
            StoreCommand.class,
            CatObjectCommand.class,
            AddCommand.class,
            ListIndexCommand.class,
            StatusCommand.class,
            CommitCommand.class,
            LogCommand.class,
            BranchCommand.class,
            CheckoutCommand.class,
            DiffCommand.class,
            MergeCommand.class
        }
)
public class VcsCommand implements Runnable {
    @Spec
    // Picocli supplies the command details.
    private CommandSpec spec;

    @Override
    public void run() {
        // Show help when no subcommand is given.
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    public static void main(String[] args) {
        // Run the selected command and return its exit code.
        System.exit(new CommandLine(new VcsCommand()).execute(args));
    }
}
