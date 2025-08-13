package jenkins.plugins.optional_depender;

import org.jenkinsci.plugins.variant.OptionalExtension;

@OptionalExtension(requirePlugins = "dependee")
public class OptionalDepender {
}
