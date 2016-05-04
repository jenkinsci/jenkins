package jenkins.util.xml;

import org.apache.commons.io.IOUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * Utilities useful when working with various XML types.
 */
@Restricted(NoExternalUse.class)
public final class XMLUtils {

    private final static Logger LOGGER = LogManager.getLogManager().getLogger(XMLUtils.class.getName());
    private final static String DISABLED_PROPERTY_NAME = XMLUtils.class.getName() + ".disableXXEPrevention";

    private static final String FEATURE_HTTP_XML_ORG_SAX_FEATURES_EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    private static final String FEATURE_HTTP_XML_ORG_SAX_FEATURES_EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";

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
                xmlReader.setFeature(FEATURE_HTTP_XML_ORG_SAX_FEATURES_EXTERNAL_GENERAL_ENTITIES, false);
            }
            catch (SAXException ignored) { /* ignored */ }
            try {
                xmlReader.setFeature(FEATURE_HTTP_XML_ORG_SAX_FEATURES_EXTERNAL_PARAMETER_ENTITIES, false);
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
            if (Boolean.getBoolean(DISABLED_PROPERTY_NAME)) {
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
     * Parse the supplied XML stream data to a {@link Document}.
     * <p>
     * This function does not close the stream.
     *
     * @param stream The XML stream.
     * @return The XML {@link Document}.
     * @throws SAXException Error parsing the XML stream data e.g. badly formed XML.
     * @throws IOException Error reading from the steam.
     * @since 2.0
     */
    public static @Nonnull Document parse(@Nonnull Reader stream) throws SAXException, IOException {
        DocumentBuilder docBuilder;

        try {
            docBuilder = newDocumentBuilderFactory().newDocumentBuilder();
            docBuilder.setEntityResolver(RestrictiveEntityResolver.INSTANCE);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Unexpected error creating DocumentBuilder.", e);
        }

        return docBuilder.parse(new InputSource(stream));
    }

    /**
     * Parse the supplied XML file data to a {@link Document}.
     * @param file The file to parse.
     * @param encoding The encoding of the XML in the file.
     * @return The parsed document.
     * @throws SAXException Error parsing the XML file data e.g. badly formed XML.
     * @throws IOException Error reading from the file.
     * @since 2.0
     */
    public static @Nonnull Document parse(@Nonnull File file, @Nonnull String encoding) throws SAXException, IOException {
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException(String.format("File %s does not exist or is not a 'normal' file.", file.getAbsolutePath()));
        }

        FileInputStream fileInputStream = new FileInputStream(file);
        try {
            InputStreamReader fileReader = new InputStreamReader(fileInputStream, encoding);
            try {
                return parse(fileReader);
            } finally {
                IOUtils.closeQuietly(fileReader);
            }
        } finally {
            IOUtils.closeQuietly(fileInputStream);
        }
    }

    /**
     * The a "value" from an XML file using XPath.
     * <p>
     * Uses the system encoding for reading the file.
     *
     * @param xpath The XPath expression to select the value.
     * @param file The file to read.
     * @return The data value. An empty {@link String} is returned when the expression does not evaluate
     * to anything in the document.
     * @throws IOException Error reading from the file.
     * @throws SAXException Error parsing the XML file data e.g. badly formed XML.
     * @throws XPathExpressionException Invalid XPath expression.
     * @since 2.0
     */
    public static @Nonnull String getValue(@Nonnull String xpath, @Nonnull File file) throws IOException, SAXException, XPathExpressionException {
        return getValue(xpath, file, Charset.defaultCharset().toString());
    }

    /**
     * The a "value" from an XML file using XPath.
     * @param xpath The XPath expression to select the value.
     * @param file The file to read.
     * @param fileDataEncoding The file data format.
     * @return The data value. An empty {@link String} is returned when the expression does not evaluate
     * to anything in the document.
     * @throws IOException Error reading from the file.
     * @throws SAXException Error parsing the XML file data e.g. badly formed XML.
     * @throws XPathExpressionException Invalid XPath expression.
     * @since 2.0
     */
    public static @Nonnull String getValue(@Nonnull String xpath, @Nonnull File file, @Nonnull String fileDataEncoding) throws IOException, SAXException, XPathExpressionException {
        Document document = parse(file, fileDataEncoding);
        return getValue(xpath, document);
    }

    /**
     * The a "value" from an XML file using XPath.
     * @param xpath The XPath expression to select the value.
     * @param document The document from which the value is to be extracted.
     * @return The data value. An empty {@link String} is returned when the expression does not evaluate
     * to anything in the document.
     * @throws XPathExpressionException Invalid XPath expression.
     * @since 2.0
     */
    public static String getValue(String xpath, Document document) throws XPathExpressionException {
        XPath xPathProcessor = XPathFactory.newInstance().newXPath();
        return xPathProcessor.compile(xpath).evaluate(document);
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

    private static DocumentBuilderFactory newDocumentBuilderFactory() {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        // Set parser features to prevent against XXE etc.
        // Note: setting only the external entity features on DocumentBuilderFactory instance
        // (ala how safeTransform does it for SAXTransformerFactory) does seem to work (was still
        // processing the entities - tried Oracle JDK 7 and 8 on OSX). Setting seems a bit extreme,
        // but looks like there's no other choice.
        documentBuilderFactory.setXIncludeAware(false);
        documentBuilderFactory.setExpandEntityReferences(false);
        setDocumentBuilderFactoryFeature(documentBuilderFactory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setDocumentBuilderFactoryFeature(documentBuilderFactory, FEATURE_HTTP_XML_ORG_SAX_FEATURES_EXTERNAL_GENERAL_ENTITIES, false);
        setDocumentBuilderFactoryFeature(documentBuilderFactory, FEATURE_HTTP_XML_ORG_SAX_FEATURES_EXTERNAL_PARAMETER_ENTITIES, false);
        setDocumentBuilderFactoryFeature(documentBuilderFactory, "http://apache.org/xml/features/disallow-doctype-decl", true);

        return documentBuilderFactory;
    }
    private static void setDocumentBuilderFactoryFeature(DocumentBuilderFactory documentBuilderFactory, String feature, boolean state) {
        try {
            documentBuilderFactory.setFeature(feature, state);
        } catch (Exception e) {}
    }
}
