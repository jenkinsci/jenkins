package jenkins.security.s2m;

import static hudson.Functions.isWindows;

import hudson.Functions;
import hudson.model.Failure;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;

/**
 * Config file that lists {@link FilePathRule} rules.
 *
 * @author Kohsuke Kawaguchi
 */
class FilePathRuleConfig extends ConfigDirectory<FilePathRule,List<FilePathRule>> {

    private static final Logger LOGGER = Logger.getLogger(FilePathRuleConfig.class.getName());

    FilePathRuleConfig(File file) {
        super(file);
    }

    @Override
    protected List<FilePathRule> create() {
        return new ArrayList<>();
    }

    @Override
    protected List<FilePathRule> readOnly(List<FilePathRule> base) {
        return Collections.unmodifiableList(new ArrayList<>(base));
    }

    @Override
    protected FilePathRule parse(String line) {
        line = line.trim();
        if (line.isEmpty())     return null;

        // TODO This does not support custom build dir configuration (Jenkins#getRawBuildsDir() etc.)
        line = line.replace("<BUILDDIR>","<JOBDIR>/builds/[0-9]+");

        // Kept only for compatibility with custom user-provided rules:
        line = line.replace("<BUILDID>","(?:[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]_[0-9][0-9]-[0-9][0-9]-[0-9][0-9]|[0-9]+)");
        line = line.replace("<JOBDIR>","<JENKINS_HOME>/jobs/.+");
        final File jenkinsHome = Jenkins.get().getRootDir();
        try {
            line = line.replace("<JENKINS_HOME>","\\Q" + jenkinsHome.getCanonicalPath() + "\\E");
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e, () -> "Failed to determine canonical path to Jenkins home directory, falling back to configured value: " + jenkinsHome.getPath());
            line = line.replace("<JENKINS_HOME>","\\Q" + jenkinsHome.getPath() + "\\E");
        }

        // config file is always /-separated even on Windows, so bring it back to \-separation.
        // This is done in the context of regex, so it has to be \\, which means in the source code it is \\\\
        if (isWindows())  line = line.replace("/","\\\\");

        Matcher m = PARSER.matcher(line);
        if (!m.matches())
            throw new Failure("Invalid filter rule line: "+line);

        try {
            return new FilePathRule(
                    Pattern.compile(m.group(3)),
                    createOpMatcher(m.group(2)),
                    m.group(1).equals("allow"));
        } catch (RuntimeException e) {
            throw new Failure("Invalid filter rule line: "+line+"\n"+ Functions.printThrowable(e));
        }
    }

    private OpMatcher createOpMatcher(String token) {
        if (token.equals("all"))
            return OpMatcher.ALL;

        final Set<String> ops = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(token.split(","))));
        return ops::contains;
    }

    public boolean checkFileAccess(String op, File path) throws SecurityException {
        String pathStr = null;

        for (FilePathRule rule : get()) {
            if (rule.op.matches(op)) {
                if (pathStr==null) {
                    try {
                        pathStr = path.getCanonicalPath();
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                    if (isWindows())    // Windows accepts '/' as separator, but for rule matching we want to normalize for consistent comparison
                        pathStr = pathStr.replace('/','\\');
                }

                if (rule.path.matcher(pathStr).matches()) {
                    // exclusion rule is only to bypass later path rules within #filePathRules,
                    // and we still want other FilePathFilters to whitelist/blacklist access.
                    // therefore I'm not throwing a SecurityException here
                    return rule.allow;
                }
            }
        }

        return false;
    }

    /**
     *
     */
    private static final Pattern PARSER = Pattern.compile("(allow|deny)\\s+([a-z,]+)\\s+(.*)");
}
