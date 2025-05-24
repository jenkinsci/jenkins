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

package jenkins.org.apache.commons.validator.routines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.IDN;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.org.apache.commons.validator.routines.DomainValidator.ArrayType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the DomainValidator.
 *
 * @version $Revision$
 */
class DomainValidatorTest {

    private DomainValidator validator;

    @BeforeEach
    void setUp() {
        validator = DomainValidator.getInstance();
    }

    @Test
    void testValidDomains() {
        assertTrue(validator.isValid("apache.org"), "apache.org should validate");
        assertTrue(validator.isValid("www.google.com"), "www.google.com should validate");

        assertTrue(validator.isValid("test-domain.com"), "test-domain.com should validate");
        assertTrue(validator.isValid("test---domain.com"), "test---domain.com should validate");
        assertTrue(validator.isValid("test-d-o-m-ain.com"), "test-d-o-m-ain.com should validate");
        assertTrue(validator.isValid("as.uk"), "two-letter domain label should validate");

        assertTrue(validator.isValid("ApAchE.Org"), "case-insensitive ApAchE.Org should validate");

        assertTrue(validator.isValid("z.com"), "single-character domain label should validate");

        assertTrue(validator.isValid("i.have.an-example.domain.name"), "i.have.an-example.domain.name should validate");
    }

    @Test
    void testInvalidDomains() {
        assertFalse(validator.isValid(".org"), "bare TLD .org shouldn't validate");
        assertFalse(validator.isValid(" apache.org "), "domain name with spaces shouldn't validate");
        assertFalse(validator.isValid("apa che.org"), "domain name containing spaces shouldn't validate");
        assertFalse(validator.isValid("-testdomain.name"), "domain name starting with dash shouldn't validate");
        assertFalse(validator.isValid("testdomain-.name"), "domain name ending with dash shouldn't validate");
        assertFalse(validator.isValid("---c.com"), "domain name starting with multiple dashes shouldn't validate");
        assertFalse(validator.isValid("c--.com"), "domain name ending with multiple dashes shouldn't validate");
        assertFalse(validator.isValid("apache.rog"), "domain name with invalid TLD shouldn't validate");

        assertFalse(validator.isValid("http://www.apache.org"), "URL shouldn't validate");
        assertFalse(validator.isValid(" "), "Empty string shouldn't validate as domain name");
        assertFalse(validator.isValid(null), "Null shouldn't validate as domain name");
    }

    @Test
    void testTopLevelDomains() {
        // infrastructure TLDs
        assertTrue(validator.isValidInfrastructureTld(".arpa"), ".arpa should validate as iTLD");
        assertFalse(validator.isValidInfrastructureTld(".com"), ".com shouldn't validate as iTLD");

        // generic TLDs
        assertTrue(validator.isValidGenericTld(".name"), ".name should validate as gTLD");
        assertFalse(validator.isValidGenericTld(".us"), ".us shouldn't validate as gTLD");

        // country code TLDs
        assertTrue(validator.isValidCountryCodeTld(".uk"), ".uk should validate as ccTLD");
        assertFalse(validator.isValidCountryCodeTld(".org"), ".org shouldn't validate as ccTLD");

        // case-insensitive
        assertTrue(validator.isValidTld(".COM"), ".COM should validate as TLD");
        assertTrue(validator.isValidTld(".BiZ"), ".BiZ should validate as TLD");

        // corner cases
        assertFalse(validator.isValid(".nope"), "invalid TLD shouldn't validate"); // TODO this is not guaranteed invalid forever
        assertFalse(validator.isValid(""), "empty string shouldn't validate as TLD");
        assertFalse(validator.isValid(null), "null shouldn't validate as TLD");
    }

    @Test
    void testAllowLocal() {
       DomainValidator noLocal = DomainValidator.getInstance(false);
       DomainValidator allowLocal = DomainValidator.getInstance(true);

       // Default is false, and should use singletons
       assertEquals(noLocal, validator);

       // Default won't allow local
       assertFalse(noLocal.isValid("localhost.localdomain"), "localhost.localdomain should validate");
       assertFalse(noLocal.isValid("localhost"), "localhost should validate");

       // But it may be requested
       assertTrue(allowLocal.isValid("localhost.localdomain"), "localhost.localdomain should validate");
       assertTrue(allowLocal.isValid("localhost"), "localhost should validate");
       assertTrue(allowLocal.isValid("hostname"), "hostname should validate");
       assertTrue(allowLocal.isValid("machinename"), "machinename should validate");

       // Check the localhost one with a few others
       assertTrue(allowLocal.isValid("apache.org"), "apache.org should validate");
       assertFalse(allowLocal.isValid(" apache.org "), "domain name with spaces shouldn't validate");
    }

    @Test
    void testIDN() {
       assertTrue(validator.isValid("www.xn--bcher-kva.ch"), "b\u00fccher.ch in IDN should validate");
    }

    @Test
    void testIDNJava6OrLater() {
        String version = System.getProperty("java.version");
        if (version.compareTo("1.6") < 0) {
            System.out.println("Cannot run Unicode IDN tests");
            return; // Cannot run the test
        } // xn--d1abbgf6aiiy.xn--p1ai http://президент.рф
       assertTrue(validator.isValid("www.b\u00fccher.ch"), "b\u00fccher.ch should validate");
       assertTrue(validator.isValid("xn--d1abbgf6aiiy.xn--p1ai"), "xn--d1abbgf6aiiy.xn--p1ai should validate");
       assertTrue(validator.isValid("президент.рф"), "президент.рф should validate");
       assertFalse(validator.isValid("www.\uFFFD.ch"), "www.\uFFFD.ch FFFD should fail");
    }

    // RFC2396: domainlabel   = alphanum | alphanum *( alphanum | "-" ) alphanum
    @Test
    void testRFC2396domainlabel() { // use fixed valid TLD
        assertTrue(validator.isValid("a.ch"), "a.ch should validate");
        assertTrue(validator.isValid("9.ch"), "9.ch should validate");
        assertTrue(validator.isValid("az.ch"), "az.ch should validate");
        assertTrue(validator.isValid("09.ch"), "09.ch should validate");
        assertTrue(validator.isValid("9-1.ch"), "9-1.ch should validate");
        assertFalse(validator.isValid("91-.ch"), "91-.ch should not validate");
        assertFalse(validator.isValid("-.ch"), "-.ch should not validate");
    }

    // RFC2396 toplabel = alpha | alpha *( alphanum | "-" ) alphanum
    @Test
    void testRFC2396toplabel() {
        // These tests use non-existent TLDs so currently need to use a package protected method
        assertTrue(validator.isValidDomainSyntax("a.c"), "a.c (alpha) should validate");
        assertTrue(validator.isValidDomainSyntax("a.cc"), "a.cc (alpha alpha) should validate");
        assertTrue(validator.isValidDomainSyntax("a.c9"), "a.c9 (alpha alphanum) should validate");
        assertTrue(validator.isValidDomainSyntax("a.c-9"), "a.c-9 (alpha - alphanum) should validate");
        assertTrue(validator.isValidDomainSyntax("a.c-z"), "a.c-z (alpha - alpha) should validate");

        assertFalse(validator.isValidDomainSyntax("a.9c"), "a.9c (alphanum alpha) should fail");
        assertFalse(validator.isValidDomainSyntax("a.c-"), "a.c- (alpha -) should fail");
        assertFalse(validator.isValidDomainSyntax("a.-"), "a.- (-) should fail");
        assertFalse(validator.isValidDomainSyntax("a.-9"), "a.-9 (- alphanum) should fail");
    }

    @Test
    void testDomainNoDots() { // rfc1123
        assertTrue(validator.isValidDomainSyntax("a"), "a (alpha) should validate");
        assertTrue(validator.isValidDomainSyntax("9"), "9 (alphanum) should validate");
        assertTrue(validator.isValidDomainSyntax("c-z"), "c-z (alpha - alpha) should validate");

        assertFalse(validator.isValidDomainSyntax("c-"), "c- (alpha -) should fail");
        assertFalse(validator.isValidDomainSyntax("-c"), "-c (- alpha) should fail");
        assertFalse(validator.isValidDomainSyntax("-"), "- (-) should fail");
    }

    @Test
    void testValidator297() {
        assertTrue(validator.isValid("xn--d1abbgf6aiiy.xn--p1ai"), "xn--d1abbgf6aiiy.xn--p1ai should validate"); // This uses a valid TLD
     }

    // labels are a max of 63 chars and domains 253
    @Test
    void testValidator306() {
        final String longString = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz0123456789A";
        assertEquals(63, longString.length()); // 26 * 2 + 11

        assertTrue(validator.isValidDomainSyntax(longString + ".com"), "63 chars label should validate");
        assertFalse(validator.isValidDomainSyntax(longString + "x.com"), "64 chars label should fail");

        assertTrue(validator.isValidDomainSyntax("test." + longString), "63 chars TLD should validate");
        assertFalse(validator.isValidDomainSyntax("test.x" + longString), "64 chars TLD should fail");

        final String longDomain =
                longString
                + "." + longString
                + "." + longString
                + "." + longString.substring(0, 61)
                ;
        assertEquals(253, longDomain.length());
        assertTrue(validator.isValidDomainSyntax(longDomain), "253 chars domain should validate");
        assertFalse(validator.isValidDomainSyntax(longDomain + "x"), "254 chars domain should fail");
    }

    // Check that IDN.toASCII behaves as it should (when wrapped by DomainValidator.unicodeToASCII)
    // Tests show that method incorrectly trims a trailing "." character
    @Test
    void testUnicodeToASCII() {
        String[] asciidots = {
                "",
                ",",
                ".", // fails IDN.toASCII, but should pass wrapped version
                "a.", // ditto
                "a.b",
                "a..b",
                "a...b",
                ".a",
                "..a",
        };
        for (String s : asciidots) {
            assertEquals(s, DomainValidator.unicodeToASCII(s));
        }
        // RFC3490 3.1. 1)
//      Whenever dots are used as label separators, the following
//      characters MUST be recognized as dots: U+002E (full stop), U+3002
//      (ideographic full stop), U+FF0E (fullwidth full stop), U+FF61
//      (halfwidth ideographic full stop).
        final String[][] otherDots = {
                {"b\u3002", "b.", },
                {"b\uFF0E", "b.", },
                {"b\uFF61", "b.", },
                {"\u3002", ".", },
                {"\uFF0E", ".", },
                {"\uFF61", ".", },
        };
        for (String[] s : otherDots) {
            assertEquals(s[1], DomainValidator.unicodeToASCII(s[0]));
        }
    }

    // Check if IDN.toASCII is broken or not
    @Test
    void testIsIDNtoASCIIBroken() {
        System.out.println(">>DomainValidatorTest.testIsIDNtoASCIIBroken()");
        final String input = ".";
        final boolean ok = input.equals(IDN.toASCII(input));
        System.out.println("IDN.toASCII is " + (ok ? "OK" : "BROKEN"));
        String[] props = {
        "java.version", //    Java Runtime Environment version
        "java.vendor", // Java Runtime Environment vendor
        "java.vm.specification.version", //   Java Virtual Machine specification version
        "java.vm.specification.vendor", //    Java Virtual Machine specification vendor
        "java.vm.specification.name", //  Java Virtual Machine specification name
        "java.vm.version", // Java Virtual Machine implementation version
        "java.vm.vendor", //  Java Virtual Machine implementation vendor
        "java.vm.name", //    Java Virtual Machine implementation name
        "java.specification.version", //  Java Runtime Environment specification version
        "java.specification.vendor", //   Java Runtime Environment specification vendor
        "java.specification.name", // Java Runtime Environment specification name
        "java.class.version", //  Java class format version number
        };
        for (String t : props) {
            System.out.println(t + "=" + System.getProperty(t));
        }
        System.out.println("<<DomainValidatorTest.testIsIDNtoASCIIBroken()");
        assertTrue(true); // dummy assertion to satisfy lint
    }

    // Check array is sorted and is lower-case
    @Test
    void test_INFRASTRUCTURE_TLDS_sortedAndLowerCase() throws Exception {
        final boolean sorted = isSortedLowerCase("INFRASTRUCTURE_TLDS");
        assertTrue(sorted);
    }

    // Check array is sorted and is lower-case
    @Test
    void test_COUNTRY_CODE_TLDS_sortedAndLowerCase() throws Exception {
        final boolean sorted = isSortedLowerCase("COUNTRY_CODE_TLDS");
        assertTrue(sorted);
    }

    // Check array is sorted and is lower-case
    @Test
    void test_GENERIC_TLDS_sortedAndLowerCase() throws Exception {
        final boolean sorted = isSortedLowerCase("GENERIC_TLDS");
        assertTrue(sorted);
    }

    // Check array is sorted and is lower-case
    @Test
    void test_LOCAL_TLDS_sortedAndLowerCase() throws Exception {
        final boolean sorted = isSortedLowerCase("LOCAL_TLDS");
        assertTrue(sorted);
    }

    @Test
    void testEnumIsPublic() {
        assertTrue(Modifier.isPublic(DomainValidator.ArrayType.class.getModifiers()));
    }

    @Test
    void testGetArray() {
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.COUNTRY_CODE_MINUS));
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.COUNTRY_CODE_PLUS));
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.GENERIC_MINUS));
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.GENERIC_PLUS));
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.LOCAL_MINUS));
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.LOCAL_PLUS));
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.COUNTRY_CODE_RO));
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.GENERIC_RO));
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.INFRASTRUCTURE_RO));
        assertNotNull(DomainValidator.getTLDEntries(ArrayType.LOCAL_RO));
    }

    // Download and process local copy of http://data.iana.org/TLD/tlds-alpha-by-domain.txt
    // Check if the internal TLD table is up to date
    // Check if the internal TLD tables have any spurious entries
    public static void main(String[] a) throws Exception {
        // Check the arrays first as this affects later checks
        // Doing this here makes it easier when updating the lists
        boolean OK = true;
        for (String list : new String[]{"INFRASTRUCTURE_TLDS", "COUNTRY_CODE_TLDS", "GENERIC_TLDS", "LOCAL_TLDS"}) {
            OK &= isSortedLowerCase(list);
        }
        if (!OK) {
            System.out.println("Fix arrays before retrying; cannot continue");
            return;
        }
        Set<String> ianaTlds = new HashSet<>(); // keep for comparison with array contents
        DomainValidator dv = DomainValidator.getInstance();
        File txtFile = new File("target/tlds-alpha-by-domain.txt");
        long timestamp = download(txtFile, "https://data.iana.org/TLD/tlds-alpha-by-domain.txt", 0L);
        final File htmlFile = new File("target/tlds-alpha-by-domain.html");
        // N.B. sometimes the html file may be updated a day or so after the txt file
        // if the txt file contains entries not found in the html file, try again in a day or two
        download(htmlFile, "https://www.iana.org/domains/root/db", timestamp);

        BufferedReader br = Files.newBufferedReader(txtFile.toPath(), StandardCharsets.UTF_8);
        String line;
        final String header;
        line = br.readLine(); // header
        if (line.startsWith("# Version ")) {
            header = line.substring(2);
        } else {
            br.close();
            throw new IOException("File does not have expected Version header");
        }
        final boolean generateUnicodeTlds = false; // Change this to generate Unicode TLDs as well

        // Parse html page to get entries
        Map<String, String[]> htmlInfo = getHtmlInfo(htmlFile);
        Map<String, String> missingTLD = new TreeMap<>(); // stores entry and comments as String[]
        Map<String, String> missingCC = new TreeMap<>();
        while ((line = br.readLine()) != null) {
            if (!line.startsWith("#")) {
                final String unicodeTld; // only different from asciiTld if that was punycode
                final String asciiTld = line.toLowerCase(Locale.ENGLISH);
                if (line.startsWith("XN--")) {
                    unicodeTld = IDN.toUnicode(line);
                } else {
                    unicodeTld = asciiTld;
                }
                if (!dv.isValidTld(asciiTld)) {
                    String [] info = htmlInfo.get(asciiTld);
                    if (info != null) {
                        String type = info[0];
                        String comment = info[1];
                        if ("country-code".equals(type)) { // Which list to use?
                            missingCC.put(asciiTld, unicodeTld + " " + comment);
                            if (generateUnicodeTlds) {
                                missingCC.put(unicodeTld, asciiTld + " " + comment);
                            }
                        } else {
                            missingTLD.put(asciiTld, unicodeTld + " " + comment);
                            if (generateUnicodeTlds) {
                                missingTLD.put(unicodeTld, asciiTld + " " + comment);
                            }
                        }
                    } else {
                        System.err.println("Expected to find HTML info for " + asciiTld);
                    }
                }
                ianaTlds.add(asciiTld);
                // Don't merge these conditions; generateUnicodeTlds is final so needs to be separate to avoid a warning
                if (generateUnicodeTlds) {
                    if (!unicodeTld.equals(asciiTld)) {
                        ianaTlds.add(unicodeTld);
                    }
                }
            }
        }
        br.close();
        // List html entries not in TLD text list
        for (String key : new TreeMap<>(htmlInfo).keySet()) {
            if (!ianaTlds.contains(key)) {
                if (isNotInRootZone(key)) {
                    System.out.println("INFO: HTML entry not yet in root zone: " + key);
                } else {
                    System.err.println("WARN: Expected to find text entry for html: " + key);
                }
            }
        }
        if (!missingTLD.isEmpty()) {
            printMap(header, missingTLD, "TLD");
        }
        if (!missingCC.isEmpty()) {
            printMap(header, missingCC, "CC");
        }
        // Check if internal tables contain any additional entries
        isInIanaList("INFRASTRUCTURE_TLDS", ianaTlds);
        isInIanaList("COUNTRY_CODE_TLDS", ianaTlds);
        isInIanaList("GENERIC_TLDS", ianaTlds);
        // Don't check local TLDS isInIanaList("LOCAL_TLDS", ianaTlds);
        System.out.println("Finished checks");
    }

    private static void printMap(final String header, Map<String, String> map, String string) {
        System.out.println("Entries missing from " + string + " List\n");
        if (header != null) {
            System.out.println("        // Taken from " + header);
        }
        for (Map.Entry<String, String> me : map.entrySet()) {
            System.out.println("        \"" + me.getKey() + "\", // " + me.getValue());
        }
        System.out.println("\nDone");
    }

    private static Map<String, String[]> getHtmlInfo(final File f) throws IOException {
        final Map<String, String[]> info = new HashMap<>();

//        <td><span class="domain tld"><a href="/domains/root/db/ax.html">.ax</a></span></td>
        final Pattern domain = Pattern.compile(".*<a href=\"/domains/root/db/([^.]+)\\.html");
//        <td>country-code</td>
        final Pattern type = Pattern.compile("\\s+<td>([^<]+)</td>");
//        <!-- <td>Åland Islands<br/><span class="tld-table-so">Ålands landskapsregering</span></td> </td> -->
//        <td>Ålands landskapsregering</td>
        final Pattern comment = Pattern.compile("\\s+<td>([^<]+)</td>");

        final BufferedReader br = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8);
        String line;
        while ((line = br.readLine()) != null) {
            Matcher m = domain.matcher(line);
            if (m.lookingAt()) {
                String dom = m.group(1);
                String typ = "??";
                String com = "??";
                line = br.readLine();
                while (line.matches("^\\s*$")) { // extra blank lines introduced
                    line = br.readLine();
                }
                Matcher t = type.matcher(line);
                if (t.lookingAt()) {
                    typ = t.group(1);
                    line = br.readLine();
                    if (line.matches("\\s+<!--.*")) {
                        while (!line.matches(".*-->.*")) {
                            line = br.readLine();
                        }
                        line = br.readLine();
                    }
                    // Should have comment; is it wrapped?
                    while (!line.matches(".*</td>.*")) {
                        line += " " + br.readLine();
                    }
                    Matcher n = comment.matcher(line);
                    if (n.lookingAt()) {
                        com = n.group(1);
                    }
                    // Don't save unused entries
                    if (com.contains("Not assigned") || com.contains("Retired") || typ.equals("test")) {
//                        System.out.println("Ignored: " + typ + " " + dom + " " +com);
                    } else {
                        info.put(dom.toLowerCase(Locale.ENGLISH), new String[]{typ, com});
//                        System.out.println("Storing: " + typ + " " + dom + " " +com);
                    }
                } else {
                    System.err.println("Unexpected type: " + line);
                }
            }
        }
        br.close();
        return info;
    }

    /*
     * Download a file if it is more recent than our cached copy.
     * Unfortunately the server does not seem to honour If-Modified-Since for the
     * Html page, so we check if it is newer than the txt file and skip download if so
     */
    private static long download(File f, String tldurl, long timestamp) throws IOException {
        final int HOUR = 60 * 60 * 1000; // an hour in ms
        final long modTime;
        // For testing purposes, don't download files more than once an hour
        if (f.canRead()) {
            modTime = f.lastModified();
            if (modTime > System.currentTimeMillis() - HOUR) {
                System.out.println("Skipping download - found recent " + f);
                return modTime;
            }
        } else {
            modTime = 0;
        }
        HttpURLConnection hc = (HttpURLConnection) new URL(tldurl).openConnection();
        if (modTime > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z"); //Sun, 06 Nov 1994 08:49:37 GMT
            String since = sdf.format(new Date(modTime));
            hc.addRequestProperty("If-Modified-Since", since);
            System.out.println("Found " + f + " with date " + since);
        }
        if (hc.getResponseCode() == 304) {
            System.out.println("Already have most recent " + tldurl);
        } else {
            System.out.println("Downloading " + tldurl);
            byte[] buff = new byte[1024];
            InputStream is = hc.getInputStream();

            FileOutputStream fos = new FileOutputStream(f);
            int len;
            while ((len = is.read(buff)) != -1) {
                fos.write(buff, 0, len);
            }
            fos.close();
            is.close();
            System.out.println("Done");
        }
        return f.lastModified();
    }

    /**
     * Check whether the domain is in the root zone currently.
     * Reads the URL http://www.iana.org/domains/root/db/*domain*.html
     * (using a local disk cache)
     * and checks for the string "This domain is not present in the root zone at this time."
     * @param domain the domain to check
     * @return true if the string is found
     */
    private static boolean isNotInRootZone(String domain) {
        String tldurl = "http://www.iana.org/domains/root/db/" + domain + ".html";
        File rootCheck = new File("target", "tld_" + domain + ".html");
        BufferedReader in = null;
        try {
            download(rootCheck, tldurl, 0L);
            in = Files.newBufferedReader(rootCheck.toPath(), StandardCharsets.UTF_8);
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if (inputLine.contains("This domain is not present in the root zone at this time.")) {
                    return true;
                }
            }
            in.close();
        } catch (IOException e) {
        } finally {
            closeQuietly(in);
        }
        return false;
    }

    private static void closeQuietly(Closeable in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
            }
        }
    }

    // isInIanaList and isSorted are split into two methods.
    // If/when access to the arrays is possible without reflection, the intermediate
    // methods can be dropped
    private static boolean isInIanaList(String arrayName, Set<String> ianaTlds) throws Exception {
        Field f = DomainValidator.class.getDeclaredField(arrayName);
        final boolean isPrivate = Modifier.isPrivate(f.getModifiers());
        if (isPrivate) {
            f.setAccessible(true);
        }
        String[] array = (String[]) f.get(null);
        try {
            return isInIanaList(arrayName, array, ianaTlds);
        } finally {
            if (isPrivate) {
                f.setAccessible(false);
            }
        }
    }

    private static boolean isInIanaList(String name, String [] array, Set<String> ianaTlds) {
        for (String s : array) {
            if (!ianaTlds.contains(s)) {
                System.out.println(name + " contains unexpected value: " + s);
            }
        }
        return true;
    }

    private static boolean isSortedLowerCase(String arrayName) throws Exception {
        Field f = DomainValidator.class.getDeclaredField(arrayName);
        final boolean isPrivate = Modifier.isPrivate(f.getModifiers());
        if (isPrivate) {
            f.setAccessible(true);
        }
        String[] array = (String[]) f.get(null);
        try {
            return isSortedLowerCase(arrayName, array);
        } finally {
            if (isPrivate) {
                f.setAccessible(false);
            }
        }
    }

    private static boolean isLowerCase(String string) {
        return string.equals(string.toLowerCase(Locale.ENGLISH));
    }

    // Check if an array is strictly sorted - and lowerCase
    private static boolean isSortedLowerCase(String name, String [] array) {
        boolean sorted = true;
        boolean strictlySorted = true;
        final int length = array.length;
        boolean lowerCase = isLowerCase(array[length - 1]); // Check the last entry
        for (int i = 0; i < length - 1; i++) { // compare all but last entry with next
            final String entry = array[i];
            final String nextEntry = array[i + 1];
            final int cmp = entry.compareTo(nextEntry);
            if (cmp > 0) { // out of order
                System.out.println("Out of order entry: " + entry + " < " + nextEntry + " in " + name);
                sorted = false;
            } else if (cmp == 0) {
                strictlySorted = false;
                System.out.println("Duplicated entry: " + entry + " in " + name);
            }
            if (!isLowerCase(entry)) {
                System.out.println("Non lowerCase entry: " + entry + " in " + name);
                lowerCase = false;
            }
        }
        return sorted && strictlySorted && lowerCase;
    }
}
