/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/* Copied from commons-validator:commons-validator:1.6, with [PATCH] modifications */
package jenkins.org.apache.commons.validator.routines;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p><b>URL Validation</b> routines.</p>
 * Behavior of validation is modified by passing in options:
 * <ul>
 * <li>ALLOW_2_SLASHES - [FALSE]  Allows double '/' characters in the path
 * component.</li>
 * <li>NO_FRAGMENT- [FALSE]  By default fragments are allowed, if this option is
 * included then fragments are flagged as illegal.</li>
 * <li>ALLOW_ALL_SCHEMES - [FALSE] By default only http, https, and ftp are
 * considered valid schemes.  Enabling this option will let any scheme pass validation.</li>
 * </ul>
 *
 * <p>Originally based in on php script by Debbie Dyer, validation.php v1.2b, Date: 03/07/02,
 * http://javascript.internet.com. However, this validation now bears little resemblance
 * to the php original.</p>
 * <pre>
 *   Example of usage:
 *   Construct a UrlValidator with valid schemes of "http", and "https".
 *
 *    String[] schemes = {"http","https"}.
 *    UrlValidator urlValidator = new UrlValidator(schemes);
 *    if (urlValidator.isValidRootUrl("ftp://foo.bar.com/")) {
 *       System.out.println("url is valid");
 *    } else {
 *       System.out.println("url is invalid");
 *    }
 *
 *    prints "url is invalid"
 *   If instead the default constructor is used.
 *
 *    UrlValidator urlValidator = new UrlValidator();
 *    if (urlValidator.isValidRootUrl("ftp://foo.bar.com/")) {
 *       System.out.println("url is valid");
 *    } else {
 *       System.out.println("url is invalid");
 *    }
 *
 *   prints out "url is valid"
 *  </pre>
 *
 * @see
 * <a href="http://www.ietf.org/rfc/rfc2396.txt">
 *  Uniform Resource Identifiers (URI): Generic Syntax
 * </a>
 *
 * @version $Revision: 1783203 $
 * @since Validator 1.4
 */
//[PATCH]
@Restricted(NoExternalUse.class)
// end of [PATCH]
public class UrlValidator implements Serializable {
    
    private static final long serialVersionUID = 7557161713937335013L;
    
    private static final int MAX_UNSIGNED_16_BIT_INT = 0xFFFF; // port max
    
    /**
     * Allows all validly formatted schemes to pass validation instead of
     * supplying a set of valid schemes.
     */
    public static final long ALLOW_ALL_SCHEMES = 1 << 0;
    
    /**
     * Allow two slashes in the path component of the URL.
     */
    public static final long ALLOW_2_SLASHES = 1 << 1;
    
    /**
     * Enabling this options disallows any URL fragments.
     */
    public static final long NO_FRAGMENTS = 1 << 2;
    
    /**
     * Allow local URLs, such as http://localhost/ or http://machine/ .
     * This enables a broad-brush check, for complex local machine name
     *  validation requirements you should create your validator with
     *  a {@link RegexValidator} instead ({@link #UrlValidator(RegexValidator, long)})
     */
    public static final long ALLOW_LOCAL_URLS = 1 << 3; // CHECKSTYLE IGNORE MagicNumber
    
    /**
     * This expression derived/taken from the BNF for URI (RFC2396).
     */
    private static final String URL_REGEX =
            "^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?";
    //        12            3  4          5       6   7        8 9
    private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX);
    
    /**
     * Schema/Protocol (ie. http:, ftp:, file:, etc).
     */
    private static final int PARSE_URL_SCHEME = 2;
    
    /**
     * Includes hostname/ip and port number.
     */
    private static final int PARSE_URL_AUTHORITY = 4;
    
    private static final int PARSE_URL_PATH = 5;
    
    private static final int PARSE_URL_QUERY = 7;
    
    private static final int PARSE_URL_FRAGMENT = 9;
    
    /**
     * Protocol scheme (e.g. http, ftp, https).
     */
    private static final String SCHEME_REGEX = "^\\p{Alpha}[\\p{Alnum}\\+\\-\\.]*";
    private static final Pattern SCHEME_PATTERN = Pattern.compile(SCHEME_REGEX);
    
    // Drop numeric, and  "+-." for now
    // TODO does not allow for optional userinfo. 
    // Validation of character set is done by isValidAuthority
    private static final String AUTHORITY_CHARS_REGEX = "\\p{Alnum}\\-\\."; // allows for IPV4 but not IPV6
    private static final String IPV6_REGEX = "[0-9a-fA-F:]+"; // do this as separate match because : could cause ambiguity with port prefix
    
    // userinfo    = *( unreserved / pct-encoded / sub-delims / ":" )
    // unreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~"
    // sub-delims    = "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="
    // We assume that password has the same valid chars as user info
    private static final String USERINFO_CHARS_REGEX = "[a-zA-Z0-9%-._~!$&'()*+,;=]";
    // since neither ':' nor '@' are allowed chars, we don't need to use non-greedy matching
    private static final String USERINFO_FIELD_REGEX =
            USERINFO_CHARS_REGEX + "+" + // At least one character for the name
                    "(?::" + USERINFO_CHARS_REGEX + "*)?@"; // colon and password may be absent
    private static final String AUTHORITY_REGEX =
            "(?:\\[("+IPV6_REGEX+")\\]|(?:(?:"+USERINFO_FIELD_REGEX+")?([" + AUTHORITY_CHARS_REGEX + "]*)))(?::(\\d*))?(.*)?";
    //             1                          e.g. user:pass@          2                                         3       4
    private static final Pattern AUTHORITY_PATTERN = Pattern.compile(AUTHORITY_REGEX);
    
    private static final int PARSE_AUTHORITY_IPV6 = 1;
    
    private static final int PARSE_AUTHORITY_HOST_IP = 2; // excludes userinfo, if present
    
    private static final int PARSE_AUTHORITY_PORT = 3; // excludes leading colon
    
    /**
     * Should always be empty. The code currently allows spaces.
     */
    private static final int PARSE_AUTHORITY_EXTRA = 4;
    
    private static final String PATH_REGEX = "^(/[-\\w:@&?=+,.!/~*'%$_;\\(\\)]*)?$";
    private static final Pattern PATH_PATTERN = Pattern.compile(PATH_REGEX);
    
    private static final String QUERY_REGEX = "^(\\S*)$";
    private static final Pattern QUERY_PATTERN = Pattern.compile(QUERY_REGEX);
    
    /**
     * Holds the set of current validation options.
     */
    private final long options;
    
    /**
     * The set of schemes that are allowed to be in a URL.
     */
    private final Set<String> allowedSchemes; // Must be lower-case
    
    /**
     * Regular expressions used to manually validate authorities if IANA
     * domain name validation isn't desired.
     */
    private final RegexValidator authorityValidator;
    
    /**
     * If no schemes are provided, default to this set.
     */
    private static final String[] DEFAULT_SCHEMES = {"http", "https", "ftp"}; // Must be lower-case
    
    /**
     * Singleton instance of this class with default schemes and options.
     */
    private static final UrlValidator DEFAULT_URL_VALIDATOR = new UrlValidator();
    
    /**
     * Returns the singleton instance of this class with default schemes and options.
     * @return singleton instance with default schemes and options
     */
    public static UrlValidator getInstance() {
        return DEFAULT_URL_VALIDATOR;
    }
    
    /**
     * Create a UrlValidator with default properties.
     */
    public UrlValidator() {
        this(null);
    }
    
    /**
     * Behavior of validation is modified by passing in several strings options:
     * @param schemes Pass in one or more url schemes to consider valid, passing in
     *        a null will default to "http,https,ftp" being valid.
     *        If a non-null schemes is specified then all valid schemes must
     *        be specified. Setting the ALLOW_ALL_SCHEMES option will
     *        ignore the contents of schemes.
     */
    public UrlValidator(String[] schemes) {
        this(schemes, 0L);
    }
    
    /**
     * Initialize a UrlValidator with the given validation options.
     * @param options The options should be set using the public constants declared in
     * this class.  To set multiple options you simply add them together.  For example,
     * ALLOW_2_SLASHES + NO_FRAGMENTS enables both of those options.
     */
    public UrlValidator(long options) {
        this(null, null, options);
    }
    
    /**
     * Behavior of validation is modified by passing in options:
     * @param schemes The set of valid schemes. Ignored if the ALLOW_ALL_SCHEMES option is set.
     * @param options The options should be set using the public constants declared in
     * this class.  To set multiple options you simply add them together.  For example,
     * ALLOW_2_SLASHES + NO_FRAGMENTS enables both of those options.
     */
    public UrlValidator(String[] schemes, long options) {
        this(schemes, null, options);
    }
    
    /**
     * Initialize a UrlValidator with the given validation options.
     * @param authorityValidator Regular expression validator used to validate the authority part
     * This allows the user to override the standard set of domains.
     * @param options Validation options. Set using the public constants of this class.
     * To set multiple options, simply add them together:
     * <p>{@code ALLOW_2_SLASHES + NO_FRAGMENTS}</p>
     * enables both of those options.
     */
    public UrlValidator(RegexValidator authorityValidator, long options) {
        this(null, authorityValidator, options);
    }
    
    /**
     * Customizable constructor. Validation behavior is modified by passing in options.
     * @param schemes the set of valid schemes. Ignored if the ALLOW_ALL_SCHEMES option is set.
     * @param authorityValidator Regular expression validator used to validate the authority part
     * @param options Validation options. Set using the public constants of this class.
     * To set multiple options, simply add them together:
     * <p>{@code ALLOW_2_SLASHES + NO_FRAGMENTS}</p>
     * enables both of those options.
     */
    public UrlValidator(String[] schemes, RegexValidator authorityValidator, long options) {
        this.options = options;
        
        if (isOn(ALLOW_ALL_SCHEMES)) {
            allowedSchemes = Collections.emptySet();
        } else {
            if (schemes == null) {
                schemes = DEFAULT_SCHEMES;
            }
            allowedSchemes = new HashSet<>(schemes.length);
            for (String scheme : schemes) {
                allowedSchemes.add(scheme.toLowerCase(Locale.ENGLISH));
            }
        }
        
        this.authorityValidator = authorityValidator;
    }
    
    /**
     * <p>Checks if a field has a valid url address.</p>
     *
     * Note that the method calls #isValidAuthority()
     * which checks that the domain is valid.
     *
     * @param value The value validation is being performed on.  A {@code null}
     * value is considered invalid.
     * @return true if the url is valid.
     */
    public boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        
        // Check the whole url address structure
        Matcher urlMatcher = URL_PATTERN.matcher(value);
        if (!urlMatcher.matches()) {
            return false;
        }
        
        String scheme = urlMatcher.group(PARSE_URL_SCHEME);
        if (!isValidScheme(scheme)) {
            return false;
        }
        
        String authority = urlMatcher.group(PARSE_URL_AUTHORITY);
        if ("file".equals(scheme)) {// Special case - file: allows an empty authority
            if (authority != null) {
                if (authority.contains(":")) { // but cannot allow trailing :
                    return false;
                }
            }
            // drop through to continue validation
        } else { // not file:
            // Validate the authority
            if (!isValidAuthority(authority)) {
                return false;
            }
        }
        
        if (!isValidPath(urlMatcher.group(PARSE_URL_PATH))) {
            return false;
        }
        
        if (!isValidQuery(urlMatcher.group(PARSE_URL_QUERY))) {
            return false;
        }
        
        if (!isValidFragment(urlMatcher.group(PARSE_URL_FRAGMENT))) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate scheme. If schemes[] was initialized to a non null,
     * then only those schemes are allowed.
     * Otherwise the default schemes are "http", "https", "ftp".
     * Matching is case-blind.
     * @param scheme The scheme to validate.  A {@code null} value is considered
     * invalid.
     * @return true if valid.
     */
    protected boolean isValidScheme(String scheme) {
        if (scheme == null) {
            return false;
        }
        
        // TODO could be removed if external schemes were checked in the ctor before being stored
        if (!SCHEME_PATTERN.matcher(scheme).matches()) {
            return false;
        }
        
        if (isOff(ALLOW_ALL_SCHEMES) && !allowedSchemes.contains(scheme.toLowerCase(Locale.ENGLISH))) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Returns true if the authority is properly formatted.  An authority is the combination
     * of hostname and port.  A {@code null} authority value is considered invalid.
     * Note: this implementation validates the domain unless a RegexValidator was provided.
     * If a RegexValidator was supplied and it matches, then the authority is regarded
     * as valid with no further checks, otherwise the method checks against the
     * AUTHORITY_PATTERN and the DomainValidator (ALLOW_LOCAL_URLS)
     * @param authority Authority value to validate, allows IDN
     * @return true if authority (hostname and port) is valid.
     */
    protected boolean isValidAuthority(String authority) {
        if (authority == null) {
            return false;
        }
        
        // check manual authority validation if specified
        if (authorityValidator != null && authorityValidator.isValid(authority)) {
            return true;
        }
        // convert to ASCII if possible
        final String authorityASCII = DomainValidator.unicodeToASCII(authority);
        
        Matcher authorityMatcher = AUTHORITY_PATTERN.matcher(authorityASCII);
        if (!authorityMatcher.matches()) {
            return false;
        }
        
        // We have to process IPV6 separately because that is parsed in a different group
        String ipv6 = authorityMatcher.group(PARSE_AUTHORITY_IPV6);
        if (ipv6 != null) {
            InetAddressValidator inetAddressValidator = InetAddressValidator.getInstance();
            if (!inetAddressValidator.isValidInet6Address(ipv6)) {
                return false;
            }
        } else {
            String hostLocation = authorityMatcher.group(PARSE_AUTHORITY_HOST_IP);
            // check if authority is hostname or IP address:
            // try a hostname first since that's much more likely
            DomainValidator domainValidator = DomainValidator.getInstance(isOn(ALLOW_LOCAL_URLS));
            if (!domainValidator.isValid(hostLocation)) {
                // try an IPv4 address
                InetAddressValidator inetAddressValidator = InetAddressValidator.getInstance();
                if (!inetAddressValidator.isValidInet4Address(hostLocation)) {
                    // isn't IPv4, so the URL is invalid
                    return false;
                }
            }
            String port = authorityMatcher.group(PARSE_AUTHORITY_PORT);
            if (port != null && port.length() > 0) {
                try {
                    int iPort = Integer.parseInt(port);
                    if (iPort < 0 || iPort > MAX_UNSIGNED_16_BIT_INT) {
                        return false;
                    }
                } catch (NumberFormatException nfe) {
                    return false; // this can happen for big numbers
                }
            }
        }
        
        String extra = authorityMatcher.group(PARSE_AUTHORITY_EXTRA);
        if (extra != null && extra.trim().length() > 0){
            return false;
        }
        
        return true;
    }
    
    /**
     * Returns true if the path is valid.  A {@code null} value is considered invalid.
     * @param path Path value to validate.
     * @return true if path is valid.
     */
    protected boolean isValidPath(String path) {
        if (path == null) {
            return false;
        }
        
        if (!PATH_PATTERN.matcher(path).matches()) {
            return false;
        }
        
        try {
            URI uri = new URI(null,null,path,null);
            String norm = uri.normalize().getPath();
            if (norm.startsWith("/../") // Trying to go via the parent dir 
                    || norm.equals("/..")) {   // Trying to go to the parent dir
                return false;
            }
        } catch (URISyntaxException e) {
            return false;
        }
        
        int slash2Count = countToken("//", path);
        if (isOff(ALLOW_2_SLASHES) && (slash2Count > 0)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Returns true if the query is null or it's a properly formatted query string.
     * @param query Query value to validate.
     * @return true if query is valid.
     */
    protected boolean isValidQuery(String query) {
        if (query == null) {
            return true;
        }
        
        return QUERY_PATTERN.matcher(query).matches();
    }
    
    /**
     * Returns true if the given fragment is null or fragments are allowed.
     * @param fragment Fragment value to validate.
     * @return true if fragment is valid.
     */
    protected boolean isValidFragment(String fragment) {
        if (fragment == null) {
            return true;
        }
        
        return isOff(NO_FRAGMENTS);
    }
    
    /**
     * Returns the number of times the token appears in the target.
     * @param token Token value to be counted.
     * @param target Target value to count tokens in.
     * @return the number of tokens.
     */
    protected int countToken(String token, String target) {
        int tokenIndex = 0;
        int count = 0;
        while (tokenIndex != -1) {
            tokenIndex = target.indexOf(token, tokenIndex);
            if (tokenIndex > -1) {
                tokenIndex++;
                count++;
            }
        }
        return count;
    }
    
    /**
     * Tests whether the given flag is on.  If the flag is not a power of 2
     * (ie. 3) this tests whether the combination of flags is on.
     *
     * @param flag Flag value to check.
     *
     * @return whether the specified flag value is on.
     */
    private boolean isOn(long flag) {
        return (options & flag) > 0;
    }
    
    /**
     * Tests whether the given flag is off.  If the flag is not a power of 2
     * (ie. 3) this tests whether the combination of flags is off.
     *
     * @param flag Flag value to check.
     *
     * @return whether the specified flag value is off.
     */
    private boolean isOff(long flag) {
        return (options & flag) == 0;
    }
    
    // Unit test access to pattern matcher
    Matcher matchURL(String value) {
        return URL_PATTERN.matcher(value);
    }
}
