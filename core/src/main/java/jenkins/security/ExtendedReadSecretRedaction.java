package jenkins.security;

import hudson.Extension;
import hudson.util.Secret;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
@Extension
public class ExtendedReadSecretRedaction implements ExtendedReadRedaction {

    private static final Pattern SECRET_PATTERN = Pattern.compile(">(" + Secret.ENCRYPTED_VALUE_PATTERN + ")<");

    @Override
    public String apply(String configDotXml) {
        Matcher matcher = SECRET_PATTERN.matcher(configDotXml);
        StringBuilder cleanXml = new StringBuilder();
        while (matcher.find()) {
            if (Secret.decrypt(matcher.group(1)) != null) {
                matcher.appendReplacement(cleanXml, ">********<");
            }
        }
        matcher.appendTail(cleanXml);
        return cleanXml.toString();
    }
}
