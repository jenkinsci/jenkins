package hudson.cli;

import hudson.Functions;

import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CLIReportUnexpectedExceptionHelper {
    static public void report(String name, Logger logger, PrintStream stderr, Throwable e) {
        final String errorMsg = String.format("Unexpected exception occurred while performing %s command.",
                name);
        stderr.println();
        stderr.println("ERROR: "+errorMsg);
        logger.log(Level.WARNING,errorMsg,e);
        Functions.printStackTrace(e,stderr);
    }
}
