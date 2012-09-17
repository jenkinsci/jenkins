/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.util;

import org.xml.sax.helpers.XMLFilterImpl;
import org.xml.sax.XMLFilter;
import org.xml.sax.ContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.apache.commons.jelly.XMLOutput;

import java.util.Locale;
import java.util.Stack;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/**
 * {@link XMLFilter} that checks the proper nesting of table related tags.
 *
 * <p>
 * Browser often "fixes" HTML by moving tables into the right place,
 * so failure to generate proper tables can result in a hard-to-track bugs.
 *
 * <p>
 * TODO: where to apply this in stapler?
 * JellyClassTearOff creates XMLOutput. Perhaps we define a decorator?
 * We can also wrap Script. would that work better?
 *
 * @author Kohsuke Kawaguchi
 */
public class TableNestChecker extends XMLFilterImpl {

    private final Stack<Checker> elements = new Stack<Checker>();
    private final Stack<String> tagNames = new Stack<String>();

    public static void applyTo(XMLOutput xo) {
        xo.setContentHandler(new TableNestChecker(xo.getContentHandler()));
    }

    public TableNestChecker() {
        elements.push(ALL_ALLOWED);
    }

    public TableNestChecker(ContentHandler target) {
        this();
        setContentHandler(target);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        String tagName = localName.toUpperCase(Locale.ENGLISH);

        // make sure that this tag occurs in the proper context
        if(!elements.peek().isAllowed(tagName))
            throw new SAXException(tagName+" is not allowed inside "+tagNames.peek());

        Checker next = CHECKERS.get(tagName);
        if(next==null)  next = ALL_ALLOWED;
        elements.push(next);
        tagNames.push(tagName);

        super.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        elements.pop();
        tagNames.pop();
        super.endElement(uri, localName, qName);
    }


    private interface Checker {
        boolean isAllowed(String childTag);
    }

    private static final Checker ALL_ALLOWED = new Checker() {
        public boolean isAllowed(String childTag) {
            return true;
        }
    };

    private static final class InList implements Checker {
        private final Set<String> tags;

        private InList(String... tags) {
            this.tags = new HashSet<String>(Arrays.asList(tags));
        }

        public boolean isAllowed(String childTag) {
            return tags.contains(childTag);
        }
    }

    private static final Map<String,Checker> CHECKERS = new HashMap<String, Checker>();

    static {
        CHECKERS.put("TABLE",new InList("TR","THEAD","TBODY"));
        InList rows = new InList("TR");
        CHECKERS.put("THEAD",rows);
        CHECKERS.put("THEAD",rows);
        CHECKERS.put("TR",   new InList("TD","TH"));
    }
}
