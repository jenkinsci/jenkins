package jenkins.plugins.optional_depender;

import jenkins.plugins.dependee.Dependee;
import org.jenkinsci.plugins.variant.OptionalExtension;

@OptionalExtension(requirePlugins = "dependee")
public class OptionalDepender {
    private static void foo(Dependee d) {}
}
