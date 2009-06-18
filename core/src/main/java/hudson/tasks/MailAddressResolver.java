/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Luca Domenico Milanesio
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
package hudson.tasks;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionListView;
import hudson.ExtensionPoint;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.User;
import hudson.scm.CVSSCM;
import hudson.scm.SCM;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Infers e-mail addresses for the user when none is specified.
 *
 * <p>
 * This is an extension point of Hudson. Plugins tha contribute new implementation
 * of this class should put {@link Extension} on your implementation class, like this:
 *
 * <pre>
 * &#64;Extension
 * class MyMailAddressResolver extends {@link MailAddressResolver} {
 *   ...
 * }
 * </pre>
 *
 * @author Kohsuke Kawaguchi
 * @since 1.192
 */
public abstract class MailAddressResolver implements ExtensionPoint {
    /**
     * Infers e-mail address of the given user.
     *
     * <p>
     * This method is called when a {@link User} without explicitly configured e-mail
     * address is used, as an attempt to infer e-mail address.
     *
     * <p>
     * The normal strategy is to look at {@link User#getProjects() the projects that the user
     * is participating}, then use the repository information to infer the e-mail address.
     *
     * <p>
     * When multiple resolvers are installed, they are consulted in order and
     * the search will be over when an address is inferred by someone.
     *
     * <p>
     * Since {@link MailAddressResolver} is singleton, this method can be invoked concurrently
     * from multiple threads.
     *
     * @return
     *      null if the inference failed.
     */
    public abstract String findMailAddressFor(User u);
    
    public static String resolve(User u) {
        for (MailAddressResolver r : all()) {
            String email = r.findMailAddressFor(u);
            if(email!=null) return email;
        }

        // fall back logic
        String extractedAddress = extractAddressFromId(u.getId());
        if (extractedAddress != null)
                return extractedAddress;

        if(u.getId().contains("@"))
            // this already looks like an e-mail ID
            return u.getId();

        String ds = Mailer.descriptor().getDefaultSuffix();
        if(ds!=null)
            return u.getId()+ds;
        else
            return null;
    }

    /**
     * Tries to extract an email address from the user id, or returns null
     */
    private static String extractAddressFromId(String id) {
        Matcher m = EMAIL_ADDRESS_REGEXP.matcher(id);
        if(m.matches())
    		return m.group(1);
    	return null;
    }

    /**
     * Matches strings like "Kohsuke Kawaguchi &lt;kohsuke.kawaguchi@sun.com>"
     * @see #extractAddressFromId(String)
     */
    private static final Pattern EMAIL_ADDRESS_REGEXP = Pattern.compile("^.*<([^>]+)>.*$");


    /**
     * All registered {@link MailAddressResolver} implementations.
     *
     * @deprecated as of 1.286
     *      Use {@link #all()} for read access and {@link Extension} for registration.
     */
    public static final List<MailAddressResolver> LIST = ExtensionListView.createList(MailAddressResolver.class);

    /**
     * Returns all the registered {@link MailAddressResolver} descriptors.
     */
    public static ExtensionList<MailAddressResolver> all() {
        return Hudson.getInstance().getExtensionList(MailAddressResolver.class);
    }

    /**
     * {@link MailAddressResolver} implemenations that cover well-known major public sites.
     *
     * <p>
     * Since this has low UI visibility, we are open to having a large number of rules here.
     * If you'd like to add one, please contribute more rules.
     */
    @Extension
    public static class DefaultAddressResolver extends MailAddressResolver {
        public String findMailAddressFor(User u) {
            for (AbstractProject<?,?> p : u.getProjects()) {
                SCM scm = p.getScm();
                if (scm instanceof CVSSCM) {
                    CVSSCM cvsscm = (CVSSCM) scm;
                    
                    String s = findMailAddressFor(u,cvsscm.getCvsRoot());
                    if(s!=null) return s;
                }
            }

            // didn't hit any known rules
            return null;
        }

        /**
         *
         * @param scm
         *      String that represents SCM connectivity.
         */
        protected String findMailAddressFor(User u, String scm) {
            for (Map.Entry<Pattern, String> e : RULE_TABLE.entrySet())
                if(e.getKey().matcher(scm).matches())
                    return u.getId()+e.getValue();
            return null;
        }

        private static final Map<Pattern,String/*suffix*/> RULE_TABLE = new HashMap<Pattern, String>();

        static {
            {// java.net
                String username = "([A-Za-z0-9_\\-])+";
                String host = "(.*.dev.java.net|kohsuke.sfbay.*)";
                Pattern cvsUrl = Pattern.compile(":pserver:"+username+"@"+host+":/cvs");

                RULE_TABLE.put(cvsUrl,"@dev.java.net");
            }

            {// source forge
                Pattern cvsUrl = Pattern.compile(":(pserver|ext):([^@]+)@([^.]+).cvs.(sourceforge|sf).net:.+");

                RULE_TABLE.put(cvsUrl,"@users.sourceforge.net");
            }

            // TODO: read some file under $HUDSON_HOME?
        }

    }
}
