package hudson.maven.util;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;

/**
 * Marks the log line that reports that Maven3 is executing a mojo.
 * It'll look something like this:
 *
 * <pre>[INFO] --- maven-clean-plugin:2.4.1:clean (default-clean) @ jobConfigHistory ---</pre>
 *
 * or
 *
 * <pre>[INFO] --- gmaven-plugin:1.0-rc-5:generateTestStubs (test-in-groovy) @ jobConfigHistory ---</pre>
 *
 * or
 *
 * <pre>[INFO] --- cobertura-maven-plugin:2.4:instrument (report:cobertura) @ sardine ---</pre>
 *
 * @author Mirko Friedenhagen
 */
public class Maven3PluginMojoNote extends ConsoleNote {

	@Override
	public ConsoleAnnotator annotate(Object context, MarkupText text,
			int charPos) {
		text.addMarkup(7, text.length(), "<b class=maven-mojo>", "</b>");
		return null;
	}

	@Extension
	public static final class DescriptorImpl extends
			ConsoleAnnotationDescriptor {
		public String getDisplayName() {
			return "Maven-Plugin Maven 3 Mojos";
		}
	}

}