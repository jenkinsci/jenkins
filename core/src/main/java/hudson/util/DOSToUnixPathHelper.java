package hudson.util;

import java.io.File;
import java.util.function.Function;

class DOSToUnixPathHelper {
    static <T> T validate(String delimiter, StringBuilder tokenizedPathBuilder, String _dir, String exe,
                         Function<File, T> validator) {
        if (delimiter == null) {
            delimiter = ", ";
        }
        else {
            tokenizedPathBuilder.append(delimiter);
        }

        tokenizedPathBuilder.append(_dir.replace('\\', '/'));

        File dir = new File(_dir);

        File f = new File(dir,exe);
        File fexe = new File(dir,exe+".exe");

        if(f.exists()) {
            return validator.apply(f);
        }

        if(fexe.exists()) {
            return validator.apply(fexe);
        }
        return null;
    }
}
