package jenkins.security.s2m;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import hudson.Functions;
import hudson.model.Failure;
import jenkins.model.Jenkins;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static hudson.Functions.isWindows;

/**
 * Config file that lists {@link FilePathRule} rules.
 *
 * @author Kohsuke Kawaguchi
 */
class FilePathRuleConfig extends ConfigDirectory<FilePathRule,List<FilePathRule>> {
    FilePathRuleConfig(File file) {
        super(file);
    }

    @Override
    protected List<FilePathRule> create() {
        return new ArrayList<FilePathRule>();
    }

    @Override
    protected List<FilePathRule> readOnly(List<FilePathRule> base) {
        return ImmutableList.copyOf(base);
    }

    @Override
    protected FilePathRule parse(String line) {
        line = line.trim();
        if (line.isEmpty())     return null;

        line = line.replace("<BUILDDIR>","<JOBDIR>/builds/<BUILDID>");
        line = line.replace("<BUILDID>","(?:[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]_[0-9][0-9]-[0-9][0-9]-[0-9][0-9]|[0-9]+)");
        line = line.replace("<JOBDIR>","<JENKINS_HOME>/jobs/.+");
        line = line.replace("<JENKINS_HOME>","\\Q"+Jenkins.getInstance().getRootDir().getPath()+"\\E");

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
        } catch (Exception e) {
            throw new Failure("Invalid filter rule line: "+line+"\n"+ Functions.printThrowable(e));
        }
    }

    private OpMatcher createOpMatcher(String token) {
        if (token.equals("all"))
            return OpMatcher.ALL;

        final ImmutableSet ops = ImmutableSet.copyOf(token.split(","));
        return new OpMatcher() {
            @Override
            public boolean matches(String op) {
                return ops.contains(op);
            }
        };
    }

    public boolean checkFileAccess(String op, File path) throws SecurityException {
        String pathStr = null;

        for (FilePathRule rule : get()) {
            if (rule.op.matches(op)) {
                if (pathStr==null) {
                    // do not canonicalize, so that JENKINS_HOME that spans across
                    // multiple volumes via symlinks can look logically like one unit.
                    pathStr = path.getPath();
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
