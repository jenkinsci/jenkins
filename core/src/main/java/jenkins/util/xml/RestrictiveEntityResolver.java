package jenkins.util.xml;

import java.io.IOException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * An EntityResolver that will fail to resolve any entities.
 * Useful in preventing External XML Entity injection attacks.
 */
@Restricted(NoExternalUse.class)
public final class RestrictiveEntityResolver implements EntityResolver {

    public static final RestrictiveEntityResolver INSTANCE = new RestrictiveEntityResolver();

    private RestrictiveEntityResolver() {
        // prevent multiple instantiation.
    }

    /**
     * Throws a SAXException if this tried to resolve any entity.
     */
    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        throw new SAXException("Refusing to resolve entity with publicId(" + publicId + ") and systemId (" + systemId + ")");
    }
}
