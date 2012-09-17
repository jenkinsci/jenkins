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
package hudson.search;

/**
 * One item of a search result.
 *
 * @author Kohsuke Kawaguchi
 */
public class SuggestedItem {
    private final SuggestedItem parent;
    public final SearchItem item;
    private String path;

    public SuggestedItem(SearchItem top) {
        this(null,top);
    }

    public SuggestedItem(SuggestedItem parent, SearchItem item) {
        this.parent = parent;
        this.item = item;
    }

    public String getPath() {
        if(path!=null)  return path;
        if(parent==null)
            return path=item.getSearchName();
        else {
            StringBuilder buf = new StringBuilder();
            getPath(buf);
            return path=buf.toString();
        }
    }

    private void getPath(StringBuilder buf) {
        if(parent==null)
            buf.append(item.getSearchName());
        else {
            parent.getPath(buf);
            buf.append(' ').append(item.getSearchName());
        }
    }

    /**
     * Gets the URL to this item.
     * @return
     *      URL that starts with '/' but doesn't end with '/'.
     *      The path is the combined path from the {@link SearchIndex} where the search started
     *      to the final item found. Thus to convert to the actual URL, the caller would need
     *      to prepend the URL of the object where the search started. 
     */
    public String getUrl() {
        StringBuilder buf = new StringBuilder();
        getUrl(buf);
        return buf.toString();
    }

    private void getUrl(StringBuilder buf) {
        if(parent!=null) {
            parent.getUrl(buf);
        }
        String f = item.getSearchUrl();
        if(f.startsWith("/")) {
            buf.setLength(0);
            buf.append(f);
        } else {
            if(buf.length()==0 || buf.charAt(buf.length()-1)!='/')
                buf.append('/');
            buf.append(f);
        }
    }
}
