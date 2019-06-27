package hudson.util;

import hudson.EnvVars;
import hudson.Util;

import java.io.File;

import static hudson.Util.fixEmpty;

class DOSToUnixPathHelper {
    interface Helper {
        void ok();
        void checkExecutable(File fexe);
        void error(String string);
        void validate(File fexe);
    }
    static void iteratePath(String exe, Helper helper) {
        exe = fixEmpty(exe);
        if(exe==null) {
            helper.ok(); // nothing entered yet
            return;
        }

        if(exe.indexOf(File.separatorChar)>=0) {
            // this is full path
            File f = new File(exe);
            if(f.exists()) {
                helper.checkExecutable(f);
                return;
            }

            File fexe = new File(exe+".exe");
            if(fexe.exists()) {
                helper.checkExecutable(fexe);
                return;
            }

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

                    File dir = new File(_dir);

                    File f = new File(dir, exe);
                    if (f.exists()) {
                        helper.validate(f);
                        return;
                    }
                    File fexe = new File(dir, exe+".exe");
                    if (fexe.exists()) {
                        helper.validate(fexe);
                        return;
                    }
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
