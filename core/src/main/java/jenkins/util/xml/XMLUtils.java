package jenkins.util.xml;

import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.xml.XMLConstants;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;

/**
 * Utilities useful when working with various XML types.
 */
@Restricted(NoExternalUse.class)
public final class XMLUtils {

    private final static Logger LOGGER = LogManager.getLogManager().getLogger(XMLUtils.class.getName());
    private final static String DISABLED_PROPERTY_NAME = XMLUtils.class.getName() + ".disableXXEPrevention";

    /**
     * Transform the source to the output in a manner that is protected against XXE attacks.
     * If the transform can not be completed safely then an IOException is thrown.
     * Note - to turn off safety set the system property <code>disableXXEPrevention</code> to <code>true</code>.
     * @param source The XML input to transform. - This should be a <code>StreamSource</code> or a
     *               <code>SAXSource</code> in order to be able to prevent XXE attacks.
     * @param out The Result of transforming the <code>source</code>.
     */
    public static void safeTransform(@Nonnull Source source, @Nonnull Result out) throws TransformerException,
            SAXException {

        InputSource src = SAXSource.sourceToInputSource(source);
        if (src != null) {
            SAXTransformerFactory stFactory = (SAXTransformerFactory) TransformerFactory.newInstance();
            stFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            XMLReader xmlReader = XMLReaderFactory.createXMLReader();
            try {
                xmlReader.setFeature("http://xml.org/sax/features/external-general-entities", false);
            }
            catch (SAXException ignored) { /* ignored */ }
            try {
                xmlReader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            }
            catch (SAXException ignored) { /* ignored */ }
            // defend against XXE
            // the above features should strip out entities - however the feature may not be supported depending
            // on the xml implementation used and this is out of our control.
            // So add a fallback plan if all else fails.
            xmlReader.setEntityResolver(RestrictiveEntityResolver.INSTANCE);
            SAXSource saxSource = new SAXSource(xmlReader, src);
            _transform(saxSource, out);
        }
        else {
            // for some reason we could not convert source
            // this applies to DOMSource and StAXSource - and possibly 3rd party implementations...
            // a DOMSource can already be compromised as it is parsed by the time it gets to us.
            if (SystemProperties.getBoolean(DISABLED_PROPERTY_NAME)) {
                LOGGER.log(Level.WARNING,  "XML external entity (XXE) prevention has been disabled by the system " +
                        "property {0}=true Your system may be vulnerable to XXE attacks.", DISABLED_PROPERTY_NAME);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "Caller stack trace: ", new Exception("XXE Prevention caller history"));
                }
                _transform(source, out);
            }
            else {
                throw new TransformerException("Could not convert source of type " + source.getClass() + " and " +
                        "XXEPrevention is enabled.");
            }
        }
    }

    /**
     * potentially unsafe XML transformation.
     * @param source The XML input to transform.
     * @param out The Result of transforming the <code>source</code>.
     */
    private static void _transform(Source source, Result out) throws TransformerException {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

        // this allows us to use UTF-8 for storing data,
        // plus it checks any well-formedness issue in the submitted data.
        Transformer t = factory.newTransformer();
        t.transform(source, out);
    }

}
