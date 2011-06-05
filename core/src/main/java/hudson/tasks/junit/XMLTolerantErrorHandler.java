/*
 * The MIT License
 * 
 * Copyright 2011 JogAmp Community, Sven Gothel
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.tasks.junit;

import org.dom4j.io.SAXReader;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

/**
 * Tolerant XML ErrorHandler.
 * <p>
 * The following XML error cases are currently tolerated (ignored):
 * <ul>
 *   <li> XERCES - FatalError - Content is not allowed in trailing section.</li>
 * </ul>
 * </p>
 * 
 */
public class XMLTolerantErrorHandler implements ErrorHandler {
    public static final String XERCES_FEATURE_PREFIX = "http://apache.org/xml/features/";
    public static final String CONTINUE_AFTER_FATAL_ERROR_FEATURE = "continue-after-fatal-error";
    public static final String msgContentIllegalInTrailingMisc = "Content is not allowed in trailing section.";

    public static final String XERCES_CONTINUE_AFTER_FATAL_ERROR_FEATURE =
        XERCES_FEATURE_PREFIX + CONTINUE_AFTER_FATAL_ERROR_FEATURE;

    /**
     * Attach an instance of this class as this reader's error handler.
     * This will also set the feature {@link #CONTINUE_AFTER_FATAL_ERROR_FEATURE CONTINUE_AFTER_FATAL_ERROR_FEATURE},
     * assuming the SAX parser is XERCES.
     * @param reader
     */
    public static void attach(SAXReader reader, boolean ignoreSetFeatureException) 
            throws SAXException
    {
        try {
            reader.setFeature(XERCES_CONTINUE_AFTER_FATAL_ERROR_FEATURE, true);
        } catch (SAXException ex) {
            if(!ignoreSetFeatureException) {
                throw ex;
            }
            ex.printStackTrace();
        }
        reader.setErrorHandler(new XMLTolerantErrorHandler());
    }

    /**
     * Attach an instance of this class as this reader's error handler.
     * This will also set the feature {@link #CONTINUE_AFTER_FATAL_ERROR_FEATURE CONTINUE_AFTER_FATAL_ERROR_FEATURE},
     * assuming the SAX parser is XERCES.
     * @param reader
     * @return true if successful, otherwise false
     */
    public static void attach(XMLReader reader, boolean ignoreSetFeatureException) 
            throws SAXException
    {
        try {
            reader.setFeature(XERCES_CONTINUE_AFTER_FATAL_ERROR_FEATURE, true);
        } catch (SAXException ex) {
            if(!ignoreSetFeatureException) {
                throw ex;
            }
            ex.printStackTrace();
        }
        reader.setErrorHandler(new XMLTolerantErrorHandler());
    }

    public static boolean exceptionMessageEquals(Throwable cause, final Class exceptionType, final String exceptionMessage) {
        while ( null != cause ) {
            if( exceptionType.isInstance(cause) ) {
                if( exceptionMessage.equals(cause.getMessage()) ) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    public void warning(SAXParseException exception) throws SAXException {
        System.err.println("Warning: " + exception.getMessage());
        exception.printStackTrace();
    }

    public void error(SAXParseException exception) throws SAXException {
        System.err.println("Error: " + exception.getMessage());
        exception.printStackTrace();
    }

    public void fatalError(SAXParseException exception) throws SAXException {
        if(exceptionMessageEquals(exception, SAXException.class, msgContentIllegalInTrailingMisc)) {
            printCatched("fatalError(SAXException "+msgContentIllegalInTrailingMisc+")", exception);
            return; // keep going ..
        }
        System.err.println("FatalError: " + exception.getMessage());
        throw exception;
    }

    static void printCatched(String header, Exception ex) {
        System.err.println("************************************************");
        System.err.println("Catched: " + header);
        System.err.println("------------------------------------------------");
        ex.printStackTrace();
        System.err.println("************************************************");
    }
}
