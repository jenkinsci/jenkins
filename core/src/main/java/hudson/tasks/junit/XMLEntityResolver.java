package hudson.tasks.junit;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.logging.Logger;

/**
 * As the name suggest: a resolver for XML entities.
 *
 * <p>
 * Basically, it provides the possibility to intercept online DTD lookups
 * and instead do offline lookup by redirecting to a local directory where
 * .dtd's are stored
 *
 * (useful when parsing testng-results.xml - which points to testng.org)
 *
 * @author Mikael Carneholm
 */
class XMLEntityResolver implements EntityResolver {

	private static final String TESTNG_NAMESPACE = "http://testng.org/";

	/**
	 * Intercepts the lookup of publicId, systemId
	 */
    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
		if (systemId != null) {
			LOGGER.fine("Will try to resolve systemId [" + systemId + "]");
			/*
			 TestNG system-ids
			 */
			if ( systemId.startsWith( TESTNG_NAMESPACE ) ) {
				LOGGER.fine( "It's a TestNG document, will try to lookup DTD in classpath" );
				String dtdFileName = systemId.substring( TESTNG_NAMESPACE.length() );
				//This ClassLoader will be the basis for loading the .dtd
				ClassLoader classLoader = getClass().getClassLoader();
				LOGGER.finer( "Ok, creating input stream now - if it fails the dtd is not available" );
				InputStream dtdFileStream = classLoader.getResourceAsStream( dtdFileName );
				/*
				 In the case of Tomcat, the classLoaderPath should be $TOMCAT_HOME/common/classes
				 */
				URL classLoaderPath = classLoader.getResource("");
				if ( dtdFileStream == null ) { // eg. if testng-versionXYZ is not available
					LOGGER.fine( "Unable to find '" + dtdFileName + "' in directory " + classLoaderPath
							+ ", falling back to online lookup" );
					return null; // fallback to online resolvning, better hope we have internet connectivity...
				}
				else {	// It's all good, give the XML parser something sweet to chew on
					InputSource source = new InputSource( dtdFileStream );
					source.setPublicId( publicId );
					source.setSystemId( systemId );
					return source;
				}
			}
		}
		// Default fallback
		return null;
	}

	private static final Logger LOGGER = Logger.getLogger( XMLEntityResolver.class.getName() );
}