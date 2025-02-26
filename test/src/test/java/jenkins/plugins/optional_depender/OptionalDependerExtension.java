package jenkins.plugins.optional_depender;

import jenkins.plugins.dependee.DependeeExtensionPoint;
import org.jenkinsci.plugins.variant.OptionalExtension;

@OptionalExtension(requirePlugins = "dependee")
public class OptionalDependerExtension extends DependeeExtensionPoint {

}
