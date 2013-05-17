package hudson;

import static org.junit.Assert.*;
import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

import org.kohsuke.args4j.*;

/**
 * Example of using Args4j for parsing Ant command line options
 */
public class AntOptsArgs4j {

	@Argument(metaVar = "[target [target2 [target3] ...]]", usage = "targets")
	private List<String> targets = new ArrayList<String>();

	@Option(name = "-h", aliases = "-help", usage = "print this message")
	private boolean help = false;

	@Option(name = "-lib", metaVar = "<path>", usage = "specifies a path to search for jars and classes")
	private String lib;

	@Option(name = "-f", aliases = { "-file", "-buildfile" }, metaVar = "<file>", usage = "use given buildfile")
	private File buildFile;

	@Option(name = "-nice", metaVar = "number", usage = "A niceness value for the main thread:\n"
			+ "1 (lowest) to 10 (highest); 5 is the default")
	private int nice = 5;

	//@Option(name = "-D", metaVar = "<property>=<value>", usage = "use value for given property")
	private Map<String, String> properties = new HashMap<String, String>();

	@Option(name = "-D", metaVar = "<property>=<value>", usage = "use value for given property")
	protected void setProperty(final String property) throws CmdLineException {
		
		int eqIndex =  property.indexOf("=");
	
		if (eqIndex == -1) {
			throw new CmdLineException(
					"Properties must be specified in the form:"
							+ "<property>=<value>");
		}
		properties.put(property.substring(0,eqIndex), property.substring(eqIndex+1));
	}

	public static void main(String[] args) throws CmdLineException {
		final String[] argv = { "-D", "key=value=1", "-f", "build.xml", "-D",
				"key2=value2", "clean", "install" };
		final AntOptsArgs4j options = new AntOptsArgs4j();
		final CmdLineParser parser = new CmdLineParser(options);
		parser.parseArgument(argv);

		System.out.println("options:"+options.properties);
		// print usage
		parser.setUsageWidth(Integer.MAX_VALUE);
		parser.printUsage(System.err);

		// check the options have been set correctly
		assertEquals("build.xml", options.buildFile.getName());
		assertEquals(2, options.targets.size());
		assertEquals(2, options.properties.size());
	}
}