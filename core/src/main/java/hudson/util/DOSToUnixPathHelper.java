package hudson.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Util;
import org.kohsuke.accmod.Restricted;

import java.io.File;

import static hudson.Util.fixEmpty;

@Restricted(org.kohsuke.accmod.restrictions.NoExternalUse.class)
class DOSToUnixPathHelper {
    interface Helper {
        void ok();
        void checkExecutable(File fexe);
        void error(String string);
        void validate(File fexe);
    }
    static private boolean checkPrefix(String prefix, Helper helper) {
        File f = constructFile(prefix);
        if(f.exists()) {
            helper.checkExecutable(f);
            return true;
        }

        File fexe = constructFile(prefix+".exe");
        if(fexe.exists()) {
            helper.checkExecutable(fexe);
            return true;
        }
        return false;
    }
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "Limited use for locating shell executable by administrator.")
    private static File constructFile(String prefix) {
        return new File(prefix);
    }
    static void iteratePath(String exe, Helper helper) {
        exe = fixEmpty(exe);
        if(exe==null) {
            helper.ok(); // nothing entered yet
            return;
        }

        if(exe.indexOf(File.separatorChar)>=0) {
            // this is full path
            if (checkPrefix(exe, helper))
                return;

            helper.error("There's no such file: "+exe);
        } else {
            // look in PATH
            String path = EnvVars.masterEnvVars.get("PATH");
            String tokenizedPath;
            String delimiter = null;
            if(path!=null) {
                StringBuilder tokenizedPathBuilder = new StringBuilder();
                for (String _dir : Util.tokenize(path.replace("\\", "\\\\"),File.pathSeparator)) {
                    if (delimiter == null) {
                        delimiter = ", ";
                    }
                    else {
                        tokenizedPathBuilder.append(delimiter);
                    }

                    tokenizedPathBuilder.append(_dir.replace('\\', '/'));

                    if (checkPrefix(_dir + File.separator + exe, helper))
                        return;
                }
                tokenizedPathBuilder.append('.');
                tokenizedPath = tokenizedPathBuilder.toString();
            }
            else {
                tokenizedPath = "unavailable.";
            }

            // didn't find it
            helper.error("There's no such executable "+exe+" in PATH: "+tokenizedPath);
        }
    }
}
