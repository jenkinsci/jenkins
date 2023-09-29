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

import junit.framework.TestCase;

/**
 * Test cases for InetAddressValidator.
 *
 * @version $Revision$
 */
public class InetAddressValidatorTest extends TestCase {

    private InetAddressValidator validator;

    /**
     * Constructor.
     */
    public InetAddressValidatorTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() {
        validator = new InetAddressValidator();
    }

    /**
     * Test IPs that point to real, well-known hosts (without actually looking them up).
     */
    public void testInetAddressesFromTheWild() {
        assertTrue("www.apache.org IP should be valid",       validator.isValid("140.211.11.130"));
        assertTrue("www.l.google.com IP should be valid",     validator.isValid("72.14.253.103"));
        assertTrue("fsf.org IP should be valid",              validator.isValid("199.232.41.5"));
        assertTrue("appscs.ign.com IP should be valid",       validator.isValid("216.35.123.87"));
    }

    public void testVALIDATOR_335() {
        assertTrue("2001:0438:FFFE:0000:0000:0000:0000:0A35 should be valid",       validator.isValid("2001:0438:FFFE:0000:0000:0000:0000:0A35"));
    }

    public void testVALIDATOR_419() {
        String addr;
        addr = "0:0:0:0:0:0:13.1.68.3";
        assertTrue(addr, validator.isValid(addr));
        addr = "0:0:0:0:0:FFFF:129.144.52.38";
        assertTrue(addr, validator.isValid(addr));
        addr = "::13.1.68.3";
        assertTrue(addr, validator.isValid(addr));
        addr = "::FFFF:129.144.52.38";
        assertTrue(addr, validator.isValid(addr));

        addr = "::ffff:192.168.1.1:192.168.1.1";
        assertFalse(addr, validator.isValid(addr));
        addr = "::192.168.1.1:192.168.1.1";
        assertFalse(addr, validator.isValid(addr));
    }

    /**
     * Inet6Address may also contain a scope id
     */
    public void testVALIDATOR_445() {
        String [] valid = {
            "2001:0000:1234:0000:0000:C1C0:ABCD:0876",
            "2001:0000:1234:0000:0000:C1C0:ABCD:0876/123",
            "2001:0000:1234:0000:0000:C1C0:ABCD:0876/0",
            "2001:0000:1234:0000:0000:C1C0:ABCD:0876%0",
            "2001:0000:1234:0000:0000:C1C0:ABCD:0876%abcdefgh",
            };
        String [] invalid = {
            "2001:0000:1234:0000:0000:C1C0:ABCD:0876/129", // too big
            "2001:0000:1234:0000:0000:C1C0:ABCD:0876/-0", // sign not allowed
            "2001:0000:1234:0000:0000:C1C0:ABCD:0876/+0", // sign not allowed
            "2001:0000:1234:0000:0000:C1C0:ABCD:0876/10O", // non-digit
            "2001:0000:1234:0000:0000:C1C0:ABCD:0876/0%0", // /bits before %node-id
            "2001:0000:1234:0000:0000:C1C0:ABCD:0876%abc defgh", // space in node id
            "2001:0000:1234:0000:0000:C1C0:ABCD:0876%abc%defgh", // '%' in node id
            };
        for (String item : valid) {
            assertTrue(String.format("%s should be valid", item), validator.isValid(item));
        }
        for (String item : invalid) {
            assertFalse(String.format("%s should be invalid", item), validator.isValid(item));
        }
    }

    /**
     * Test valid and invalid IPs from each address class.
     */
    public void testInetAddressesByClass() {
        assertTrue("class A IP should be valid",              validator.isValid("24.25.231.12"));
        assertFalse("illegal class A IP should be invalid",   validator.isValid("2.41.32.324"));

        assertTrue("class B IP should be valid",              validator.isValid("135.14.44.12"));
        assertFalse("illegal class B IP should be invalid",   validator.isValid("154.123.441.123"));

        assertTrue("class C IP should be valid",              validator.isValid("213.25.224.32"));
        assertFalse("illegal class C IP should be invalid",   validator.isValid("201.543.23.11"));

        assertTrue("class D IP should be valid",              validator.isValid("229.35.159.6"));
        assertFalse("illegal class D IP should be invalid",   validator.isValid("231.54.11.987"));

        assertTrue("class E IP should be valid",              validator.isValid("248.85.24.92"));
        assertFalse("illegal class E IP should be invalid",   validator.isValid("250.21.323.48"));
    }

    /**
     * Test reserved IPs.
     */
    public void testReservedInetAddresses() {
        assertTrue("localhost IP should be valid",            validator.isValid("127.0.0.1"));
        assertTrue("broadcast IP should be valid",            validator.isValid("255.255.255.255"));
    }

    /**
     * Test obviously broken IPs.
     */
    public void testBrokenInetAddresses() {
        assertFalse("IP with characters should be invalid",     validator.isValid("124.14.32.abc"));
        // TODO: there is some debate as to whether leading zeros should be allowed
        // They are ambiguous: does the leading 0 mean octal?
        assertFalse("IP with leading zeroes should be invalid", validator.isValid("124.14.32.01"));
        assertFalse("IP with three groups should be invalid",   validator.isValid("23.64.12"));
        assertFalse("IP with five groups should be invalid",    validator.isValid("26.34.23.77.234"));
        assertFalse("IP empty string should be invalid", validator.isValidInet6Address("")); // empty string
    }

    /**
     * Test IPv6 addresses.
     * <p>These tests were ported from a
     * <a href="http://download.dartware.com/thirdparty/test-ipv6-regex.pl">Perl script</a>.</p>
     *
     */
    public void testIPv6() {
        // The original Perl script contained a lot of duplicate tests.
        // I removed the duplicates I noticed, but there may be more.
        assertFalse("IPV6 empty string should be invalid", validator.isValidInet6Address("")); // empty string
        assertTrue("IPV6 ::1 should be valid", validator.isValidInet6Address("::1")); // loopback, compressed, non-routable
        assertTrue("IPV6 :: should be valid", validator.isValidInet6Address("::")); // unspecified, compressed, non-routable
        assertTrue("IPV6 0:0:0:0:0:0:0:1 should be valid", validator.isValidInet6Address("0:0:0:0:0:0:0:1")); // loopback, full
        assertTrue("IPV6 0:0:0:0:0:0:0:0 should be valid", validator.isValidInet6Address("0:0:0:0:0:0:0:0")); // unspecified, full
        assertTrue("IPV6 2001:DB8:0:0:8:800:200C:417A should be valid", validator.isValidInet6Address("2001:DB8:0:0:8:800:200C:417A")); // unicast, full
        assertTrue("IPV6 FF01:0:0:0:0:0:0:101 should be valid", validator.isValidInet6Address("FF01:0:0:0:0:0:0:101")); // multicast, full
        assertTrue("IPV6 2001:DB8::8:800:200C:417A should be valid", validator.isValidInet6Address("2001:DB8::8:800:200C:417A")); // unicast, compressed
        assertTrue("IPV6 FF01::101 should be valid", validator.isValidInet6Address("FF01::101")); // multicast, compressed
        assertFalse("IPV6 2001:DB8:0:0:8:800:200C:417A:221 should be invalid", validator.isValidInet6Address("2001:DB8:0:0:8:800:200C:417A:221")); // unicast, full
        assertFalse("IPV6 FF01::101::2 should be invalid", validator.isValidInet6Address("FF01::101::2")); // multicast, compressed
        assertTrue("IPV6 fe80::217:f2ff:fe07:ed62 should be valid", validator.isValidInet6Address("fe80::217:f2ff:fe07:ed62"));
        assertTrue("IPV6 2001:0000:1234:0000:0000:C1C0:ABCD:0876 should be valid", validator.isValidInet6Address("2001:0000:1234:0000:0000:C1C0:ABCD:0876"));
        assertTrue("IPV6 3ffe:0b00:0000:0000:0001:0000:0000:000a should be valid", validator.isValidInet6Address("3ffe:0b00:0000:0000:0001:0000:0000:000a"));
        assertTrue("IPV6 FF02:0000:0000:0000:0000:0000:0000:0001 should be valid", validator.isValidInet6Address("FF02:0000:0000:0000:0000:0000:0000:0001"));
        assertTrue("IPV6 0000:0000:0000:0000:0000:0000:0000:0001 should be valid", validator.isValidInet6Address("0000:0000:0000:0000:0000:0000:0000:0001"));
        assertTrue("IPV6 0000:0000:0000:0000:0000:0000:0000:0000 should be valid", validator.isValidInet6Address("0000:0000:0000:0000:0000:0000:0000:0000"));
        assertFalse("IPV6 02001:0000:1234:0000:0000:C1C0:ABCD:0876 should be invalid", validator.isValidInet6Address("02001:0000:1234:0000:0000:C1C0:ABCD:0876")); // extra 0 not allowed!
        assertFalse("IPV6 2001:0000:1234:0000:00001:C1C0:ABCD:0876 should be invalid", validator.isValidInet6Address("2001:0000:1234:0000:00001:C1C0:ABCD:0876")); // extra 0 not allowed!
        assertFalse("IPV6 2001:0000:1234:0000:0000:C1C0:ABCD:0876 0 should be invalid", validator.isValidInet6Address("2001:0000:1234:0000:0000:C1C0:ABCD:0876 0")); // junk after valid address
        assertFalse("IPV6 2001:0000:1234: 0000:0000:C1C0:ABCD:0876 should be invalid", validator.isValidInet6Address("2001:0000:1234: 0000:0000:C1C0:ABCD:0876")); // internal space
        assertFalse("IPV6 3ffe:0b00:0000:0001:0000:0000:000a should be invalid", validator.isValidInet6Address("3ffe:0b00:0000:0001:0000:0000:000a")); // seven segments
        assertFalse("IPV6 FF02:0000:0000:0000:0000:0000:0000:0000:0001 should be invalid", validator.isValidInet6Address("FF02:0000:0000:0000:0000:0000:0000:0000:0001")); // nine segments
        assertFalse("IPV6 3ffe:b00::1::a should be invalid", validator.isValidInet6Address("3ffe:b00::1::a")); // double "::"
        assertFalse("IPV6 ::1111:2222:3333:4444:5555:6666:: should be invalid", validator.isValidInet6Address("::1111:2222:3333:4444:5555:6666::")); // double "::"
        assertTrue("IPV6 2::10 should be valid", validator.isValidInet6Address("2::10"));
        assertTrue("IPV6 ff02::1 should be valid", validator.isValidInet6Address("ff02::1"));
        assertTrue("IPV6 fe80:: should be valid", validator.isValidInet6Address("fe80::"));
        assertTrue("IPV6 2002:: should be valid", validator.isValidInet6Address("2002::"));
        assertTrue("IPV6 2001:db8:: should be valid", validator.isValidInet6Address("2001:db8::"));
        assertTrue("IPV6 2001:0db8:1234:: should be valid", validator.isValidInet6Address("2001:0db8:1234::"));
        assertTrue("IPV6 ::ffff:0:0 should be valid", validator.isValidInet6Address("::ffff:0:0"));
        assertTrue("IPV6 1:2:3:4:5:6:7:8 should be valid", validator.isValidInet6Address("1:2:3:4:5:6:7:8"));
        assertTrue("IPV6 1:2:3:4:5:6::8 should be valid", validator.isValidInet6Address("1:2:3:4:5:6::8"));
        assertTrue("IPV6 1:2:3:4:5::8 should be valid", validator.isValidInet6Address("1:2:3:4:5::8"));
        assertTrue("IPV6 1:2:3:4::8 should be valid", validator.isValidInet6Address("1:2:3:4::8"));
        assertTrue("IPV6 1:2:3::8 should be valid", validator.isValidInet6Address("1:2:3::8"));
        assertTrue("IPV6 1:2::8 should be valid", validator.isValidInet6Address("1:2::8"));
        assertTrue("IPV6 1::8 should be valid", validator.isValidInet6Address("1::8"));
        assertTrue("IPV6 1::2:3:4:5:6:7 should be valid", validator.isValidInet6Address("1::2:3:4:5:6:7"));
        assertTrue("IPV6 1::2:3:4:5:6 should be valid", validator.isValidInet6Address("1::2:3:4:5:6"));
        assertTrue("IPV6 1::2:3:4:5 should be valid", validator.isValidInet6Address("1::2:3:4:5"));
        assertTrue("IPV6 1::2:3:4 should be valid", validator.isValidInet6Address("1::2:3:4"));
        assertTrue("IPV6 1::2:3 should be valid", validator.isValidInet6Address("1::2:3"));
        assertTrue("IPV6 ::2:3:4:5:6:7:8 should be valid", validator.isValidInet6Address("::2:3:4:5:6:7:8"));
        assertTrue("IPV6 ::2:3:4:5:6:7 should be valid", validator.isValidInet6Address("::2:3:4:5:6:7"));
        assertTrue("IPV6 ::2:3:4:5:6 should be valid", validator.isValidInet6Address("::2:3:4:5:6"));
        assertTrue("IPV6 ::2:3:4:5 should be valid", validator.isValidInet6Address("::2:3:4:5"));
        assertTrue("IPV6 ::2:3:4 should be valid", validator.isValidInet6Address("::2:3:4"));
        assertTrue("IPV6 ::2:3 should be valid", validator.isValidInet6Address("::2:3"));
        assertTrue("IPV6 ::8 should be valid", validator.isValidInet6Address("::8"));
        assertTrue("IPV6 1:2:3:4:5:6:: should be valid", validator.isValidInet6Address("1:2:3:4:5:6::"));
        assertTrue("IPV6 1:2:3:4:5:: should be valid", validator.isValidInet6Address("1:2:3:4:5::"));
        assertTrue("IPV6 1:2:3:4:: should be valid", validator.isValidInet6Address("1:2:3:4::"));
        assertTrue("IPV6 1:2:3:: should be valid", validator.isValidInet6Address("1:2:3::"));
        assertTrue("IPV6 1:2:: should be valid", validator.isValidInet6Address("1:2::"));
        assertTrue("IPV6 1:: should be valid", validator.isValidInet6Address("1::"));
        assertTrue("IPV6 1:2:3:4:5::7:8 should be valid", validator.isValidInet6Address("1:2:3:4:5::7:8"));
        assertFalse("IPV6 1:2:3::4:5::7:8 should be invalid", validator.isValidInet6Address("1:2:3::4:5::7:8")); // Double "::"
        assertFalse("IPV6 12345::6:7:8 should be invalid", validator.isValidInet6Address("12345::6:7:8"));
        assertTrue("IPV6 1:2:3:4::7:8 should be valid", validator.isValidInet6Address("1:2:3:4::7:8"));
        assertTrue("IPV6 1:2:3::7:8 should be valid", validator.isValidInet6Address("1:2:3::7:8"));
        assertTrue("IPV6 1:2::7:8 should be valid", validator.isValidInet6Address("1:2::7:8"));
        assertTrue("IPV6 1::7:8 should be valid", validator.isValidInet6Address("1::7:8"));
        // IPv4 addresses as dotted-quads
        assertTrue("IPV6 1:2:3:4:5:6:1.2.3.4 should be valid", validator.isValidInet6Address("1:2:3:4:5:6:1.2.3.4"));
        assertTrue("IPV6 1:2:3:4:5::1.2.3.4 should be valid", validator.isValidInet6Address("1:2:3:4:5::1.2.3.4"));
        assertTrue("IPV6 1:2:3:4::1.2.3.4 should be valid", validator.isValidInet6Address("1:2:3:4::1.2.3.4"));
        assertTrue("IPV6 1:2:3::1.2.3.4 should be valid", validator.isValidInet6Address("1:2:3::1.2.3.4"));
        assertTrue("IPV6 1:2::1.2.3.4 should be valid", validator.isValidInet6Address("1:2::1.2.3.4"));
        assertTrue("IPV6 1::1.2.3.4 should be valid", validator.isValidInet6Address("1::1.2.3.4"));
        assertTrue("IPV6 1:2:3:4::5:1.2.3.4 should be valid", validator.isValidInet6Address("1:2:3:4::5:1.2.3.4"));
        assertTrue("IPV6 1:2:3::5:1.2.3.4 should be valid", validator.isValidInet6Address("1:2:3::5:1.2.3.4"));
        assertTrue("IPV6 1:2::5:1.2.3.4 should be valid", validator.isValidInet6Address("1:2::5:1.2.3.4"));
        assertTrue("IPV6 1::5:1.2.3.4 should be valid", validator.isValidInet6Address("1::5:1.2.3.4"));
        assertTrue("IPV6 1::5:11.22.33.44 should be valid", validator.isValidInet6Address("1::5:11.22.33.44"));
        assertFalse("IPV6 1::5:400.2.3.4 should be invalid", validator.isValidInet6Address("1::5:400.2.3.4"));
        assertFalse("IPV6 1::5:260.2.3.4 should be invalid", validator.isValidInet6Address("1::5:260.2.3.4"));
        assertFalse("IPV6 1::5:256.2.3.4 should be invalid", validator.isValidInet6Address("1::5:256.2.3.4"));
        assertFalse("IPV6 1::5:1.256.3.4 should be invalid", validator.isValidInet6Address("1::5:1.256.3.4"));
        assertFalse("IPV6 1::5:1.2.256.4 should be invalid", validator.isValidInet6Address("1::5:1.2.256.4"));
        assertFalse("IPV6 1::5:1.2.3.256 should be invalid", validator.isValidInet6Address("1::5:1.2.3.256"));
        assertFalse("IPV6 1::5:300.2.3.4 should be invalid", validator.isValidInet6Address("1::5:300.2.3.4"));
        assertFalse("IPV6 1::5:1.300.3.4 should be invalid", validator.isValidInet6Address("1::5:1.300.3.4"));
        assertFalse("IPV6 1::5:1.2.300.4 should be invalid", validator.isValidInet6Address("1::5:1.2.300.4"));
        assertFalse("IPV6 1::5:1.2.3.300 should be invalid", validator.isValidInet6Address("1::5:1.2.3.300"));
        assertFalse("IPV6 1::5:900.2.3.4 should be invalid", validator.isValidInet6Address("1::5:900.2.3.4"));
        assertFalse("IPV6 1::5:1.900.3.4 should be invalid", validator.isValidInet6Address("1::5:1.900.3.4"));
        assertFalse("IPV6 1::5:1.2.900.4 should be invalid", validator.isValidInet6Address("1::5:1.2.900.4"));
        assertFalse("IPV6 1::5:1.2.3.900 should be invalid", validator.isValidInet6Address("1::5:1.2.3.900"));
        assertFalse("IPV6 1::5:300.300.300.300 should be invalid", validator.isValidInet6Address("1::5:300.300.300.300"));
        assertFalse("IPV6 1::5:3000.30.30.30 should be invalid", validator.isValidInet6Address("1::5:3000.30.30.30"));
        assertFalse("IPV6 1::400.2.3.4 should be invalid", validator.isValidInet6Address("1::400.2.3.4"));
        assertFalse("IPV6 1::260.2.3.4 should be invalid", validator.isValidInet6Address("1::260.2.3.4"));
        assertFalse("IPV6 1::256.2.3.4 should be invalid", validator.isValidInet6Address("1::256.2.3.4"));
        assertFalse("IPV6 1::1.256.3.4 should be invalid", validator.isValidInet6Address("1::1.256.3.4"));
        assertFalse("IPV6 1::1.2.256.4 should be invalid", validator.isValidInet6Address("1::1.2.256.4"));
        assertFalse("IPV6 1::1.2.3.256 should be invalid", validator.isValidInet6Address("1::1.2.3.256"));
        assertFalse("IPV6 1::300.2.3.4 should be invalid", validator.isValidInet6Address("1::300.2.3.4"));
        assertFalse("IPV6 1::1.300.3.4 should be invalid", validator.isValidInet6Address("1::1.300.3.4"));
        assertFalse("IPV6 1::1.2.300.4 should be invalid", validator.isValidInet6Address("1::1.2.300.4"));
        assertFalse("IPV6 1::1.2.3.300 should be invalid", validator.isValidInet6Address("1::1.2.3.300"));
        assertFalse("IPV6 1::900.2.3.4 should be invalid", validator.isValidInet6Address("1::900.2.3.4"));
        assertFalse("IPV6 1::1.900.3.4 should be invalid", validator.isValidInet6Address("1::1.900.3.4"));
        assertFalse("IPV6 1::1.2.900.4 should be invalid", validator.isValidInet6Address("1::1.2.900.4"));
        assertFalse("IPV6 1::1.2.3.900 should be invalid", validator.isValidInet6Address("1::1.2.3.900"));
        assertFalse("IPV6 1::300.300.300.300 should be invalid", validator.isValidInet6Address("1::300.300.300.300"));
        assertFalse("IPV6 1::3000.30.30.30 should be invalid", validator.isValidInet6Address("1::3000.30.30.30"));
        assertFalse("IPV6 ::400.2.3.4 should be invalid", validator.isValidInet6Address("::400.2.3.4"));
        assertFalse("IPV6 ::260.2.3.4 should be invalid", validator.isValidInet6Address("::260.2.3.4"));
        assertFalse("IPV6 ::256.2.3.4 should be invalid", validator.isValidInet6Address("::256.2.3.4"));
        assertFalse("IPV6 ::1.256.3.4 should be invalid", validator.isValidInet6Address("::1.256.3.4"));
        assertFalse("IPV6 ::1.2.256.4 should be invalid", validator.isValidInet6Address("::1.2.256.4"));
        assertFalse("IPV6 ::1.2.3.256 should be invalid", validator.isValidInet6Address("::1.2.3.256"));
        assertFalse("IPV6 ::300.2.3.4 should be invalid", validator.isValidInet6Address("::300.2.3.4"));
        assertFalse("IPV6 ::1.300.3.4 should be invalid", validator.isValidInet6Address("::1.300.3.4"));
        assertFalse("IPV6 ::1.2.300.4 should be invalid", validator.isValidInet6Address("::1.2.300.4"));
        assertFalse("IPV6 ::1.2.3.300 should be invalid", validator.isValidInet6Address("::1.2.3.300"));
        assertFalse("IPV6 ::900.2.3.4 should be invalid", validator.isValidInet6Address("::900.2.3.4"));
        assertFalse("IPV6 ::1.900.3.4 should be invalid", validator.isValidInet6Address("::1.900.3.4"));
        assertFalse("IPV6 ::1.2.900.4 should be invalid", validator.isValidInet6Address("::1.2.900.4"));
        assertFalse("IPV6 ::1.2.3.900 should be invalid", validator.isValidInet6Address("::1.2.3.900"));
        assertFalse("IPV6 ::300.300.300.300 should be invalid", validator.isValidInet6Address("::300.300.300.300"));
        assertFalse("IPV6 ::3000.30.30.30 should be invalid", validator.isValidInet6Address("::3000.30.30.30"));
        assertTrue("IPV6 fe80::217:f2ff:254.7.237.98 should be valid", validator.isValidInet6Address("fe80::217:f2ff:254.7.237.98"));
        assertTrue("IPV6 ::ffff:192.168.1.26 should be valid", validator.isValidInet6Address("::ffff:192.168.1.26"));
        assertFalse("IPV6 2001:1:1:1:1:1:255Z255X255Y255 should be invalid", validator.isValidInet6Address("2001:1:1:1:1:1:255Z255X255Y255")); // garbage instead of "." in IPv4
        assertFalse("IPV6 ::ffff:192x168.1.26 should be invalid", validator.isValidInet6Address("::ffff:192x168.1.26")); // ditto
        assertTrue("IPV6 ::ffff:192.168.1.1 should be valid", validator.isValidInet6Address("::ffff:192.168.1.1"));
        assertTrue("IPV6 0:0:0:0:0:0:13.1.68.3 should be valid", validator.isValidInet6Address("0:0:0:0:0:0:13.1.68.3")); // IPv4-compatible IPv6 address, full, deprecated
        assertTrue("IPV6 0:0:0:0:0:FFFF:129.144.52.38 should be valid", validator.isValidInet6Address("0:0:0:0:0:FFFF:129.144.52.38")); // IPv4-mapped IPv6 address, full
        assertTrue("IPV6 ::13.1.68.3 should be valid", validator.isValidInet6Address("::13.1.68.3")); // IPv4-compatible IPv6 address, compressed, deprecated
        assertTrue("IPV6 ::FFFF:129.144.52.38 should be valid", validator.isValidInet6Address("::FFFF:129.144.52.38")); // IPv4-mapped IPv6 address, compressed
        assertTrue("IPV6 fe80:0:0:0:204:61ff:254.157.241.86 should be valid", validator.isValidInet6Address("fe80:0:0:0:204:61ff:254.157.241.86"));
        assertTrue("IPV6 fe80::204:61ff:254.157.241.86 should be valid", validator.isValidInet6Address("fe80::204:61ff:254.157.241.86"));
        assertTrue("IPV6 ::ffff:12.34.56.78 should be valid", validator.isValidInet6Address("::ffff:12.34.56.78"));
        assertFalse("IPV6 ::ffff:2.3.4 should be invalid", validator.isValidInet6Address("::ffff:2.3.4"));
        assertFalse("IPV6 ::ffff:257.1.2.3 should be invalid", validator.isValidInet6Address("::ffff:257.1.2.3"));
        assertFalse("IPV6 1.2.3.4 should be invalid", validator.isValidInet6Address("1.2.3.4"));
        assertFalse("IPV6 1.2.3.4:1111:2222:3333:4444::5555 should be invalid", validator.isValidInet6Address("1.2.3.4:1111:2222:3333:4444::5555"));
        assertFalse("IPV6 1.2.3.4:1111:2222:3333::5555 should be invalid", validator.isValidInet6Address("1.2.3.4:1111:2222:3333::5555"));
        assertFalse("IPV6 1.2.3.4:1111:2222::5555 should be invalid", validator.isValidInet6Address("1.2.3.4:1111:2222::5555"));
        assertFalse("IPV6 1.2.3.4:1111::5555 should be invalid", validator.isValidInet6Address("1.2.3.4:1111::5555"));
        assertFalse("IPV6 1.2.3.4::5555 should be invalid", validator.isValidInet6Address("1.2.3.4::5555"));
        assertFalse("IPV6 1.2.3.4:: should be invalid", validator.isValidInet6Address("1.2.3.4::"));
        // Testing IPv4 addresses represented as dotted-quads
        // Leading zeroes in IPv4 addresses not allowed: some systems treat the leading "0" in ".086" as the start of an octal number
        // Update: The BNF in RFC-3986 explicitly defines the dec-octet (for IPv4 addresses) not to have a leading zero
        assertFalse("IPV6 fe80:0000:0000:0000:0204:61ff:254.157.241.086 should be invalid", validator.isValidInet6Address("fe80:0000:0000:0000:0204:61ff:254.157.241.086"));
        assertTrue("IPV6 ::ffff:192.0.2.128 should be valid", validator.isValidInet6Address("::ffff:192.0.2.128")); // but this is OK, since there's a single digit
        assertFalse("IPV6 XXXX:XXXX:XXXX:XXXX:XXXX:XXXX:1.2.3.4 should be invalid", validator.isValidInet6Address("XXXX:XXXX:XXXX:XXXX:XXXX:XXXX:1.2.3.4"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666:00.00.00.00 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:00.00.00.00"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666:000.000.000.000 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:000.000.000.000"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666:256.256.256.256 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:256.256.256.256"));
        assertTrue("IPV6 fe80:0000:0000:0000:0204:61ff:fe9d:f156 should be valid", validator.isValidInet6Address("fe80:0000:0000:0000:0204:61ff:fe9d:f156"));
        assertTrue("IPV6 fe80:0:0:0:204:61ff:fe9d:f156 should be valid", validator.isValidInet6Address("fe80:0:0:0:204:61ff:fe9d:f156"));
        assertTrue("IPV6 fe80::204:61ff:fe9d:f156 should be valid", validator.isValidInet6Address("fe80::204:61ff:fe9d:f156"));
        assertFalse("IPV6 : should be invalid", validator.isValidInet6Address(":"));
        assertTrue("IPV6 ::ffff:c000:280 should be valid", validator.isValidInet6Address("::ffff:c000:280"));
        assertFalse("IPV6 1111:2222:3333:4444::5555: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444::5555:"));
        assertFalse("IPV6 1111:2222:3333::5555: should be invalid", validator.isValidInet6Address("1111:2222:3333::5555:"));
        assertFalse("IPV6 1111:2222::5555: should be invalid", validator.isValidInet6Address("1111:2222::5555:"));
        assertFalse("IPV6 1111::5555: should be invalid", validator.isValidInet6Address("1111::5555:"));
        assertFalse("IPV6 ::5555: should be invalid", validator.isValidInet6Address("::5555:"));
        assertFalse("IPV6 ::: should be invalid", validator.isValidInet6Address(":::"));
        assertFalse("IPV6 1111: should be invalid", validator.isValidInet6Address("1111:"));
        assertFalse("IPV6 :1111:2222:3333:4444::5555 should be invalid", validator.isValidInet6Address(":1111:2222:3333:4444::5555"));
        assertFalse("IPV6 :1111:2222:3333::5555 should be invalid", validator.isValidInet6Address(":1111:2222:3333::5555"));
        assertFalse("IPV6 :1111:2222::5555 should be invalid", validator.isValidInet6Address(":1111:2222::5555"));
        assertFalse("IPV6 :1111::5555 should be invalid", validator.isValidInet6Address(":1111::5555"));
        assertFalse("IPV6 :::5555 should be invalid", validator.isValidInet6Address(":::5555"));
        assertTrue("IPV6 2001:0db8:85a3:0000:0000:8a2e:0370:7334 should be valid", validator.isValidInet6Address("2001:0db8:85a3:0000:0000:8a2e:0370:7334"));
        assertTrue("IPV6 2001:db8:85a3:0:0:8a2e:370:7334 should be valid", validator.isValidInet6Address("2001:db8:85a3:0:0:8a2e:370:7334"));
        assertTrue("IPV6 2001:db8:85a3::8a2e:370:7334 should be valid", validator.isValidInet6Address("2001:db8:85a3::8a2e:370:7334"));
        assertTrue("IPV6 2001:0db8:0000:0000:0000:0000:1428:57ab should be valid", validator.isValidInet6Address("2001:0db8:0000:0000:0000:0000:1428:57ab"));
        assertTrue("IPV6 2001:0db8:0000:0000:0000::1428:57ab should be valid", validator.isValidInet6Address("2001:0db8:0000:0000:0000::1428:57ab"));
        assertTrue("IPV6 2001:0db8:0:0:0:0:1428:57ab should be valid", validator.isValidInet6Address("2001:0db8:0:0:0:0:1428:57ab"));
        assertTrue("IPV6 2001:0db8:0:0::1428:57ab should be valid", validator.isValidInet6Address("2001:0db8:0:0::1428:57ab"));
        assertTrue("IPV6 2001:0db8::1428:57ab should be valid", validator.isValidInet6Address("2001:0db8::1428:57ab"));
        assertTrue("IPV6 2001:db8::1428:57ab should be valid", validator.isValidInet6Address("2001:db8::1428:57ab"));
        assertTrue("IPV6 ::ffff:0c22:384e should be valid", validator.isValidInet6Address("::ffff:0c22:384e"));
        assertTrue("IPV6 2001:0db8:1234:0000:0000:0000:0000:0000 should be valid", validator.isValidInet6Address("2001:0db8:1234:0000:0000:0000:0000:0000"));
        assertTrue("IPV6 2001:0db8:1234:ffff:ffff:ffff:ffff:ffff should be valid", validator.isValidInet6Address("2001:0db8:1234:ffff:ffff:ffff:ffff:ffff"));
        assertTrue("IPV6 2001:db8:a::123 should be valid", validator.isValidInet6Address("2001:db8:a::123"));
        assertFalse("IPV6 123 should be invalid", validator.isValidInet6Address("123"));
        assertFalse("IPV6 ldkfj should be invalid", validator.isValidInet6Address("ldkfj"));
        assertFalse("IPV6 2001::FFD3::57ab should be invalid", validator.isValidInet6Address("2001::FFD3::57ab"));
        assertFalse("IPV6 2001:db8:85a3::8a2e:37023:7334 should be invalid", validator.isValidInet6Address("2001:db8:85a3::8a2e:37023:7334"));
        assertFalse("IPV6 2001:db8:85a3::8a2e:370k:7334 should be invalid", validator.isValidInet6Address("2001:db8:85a3::8a2e:370k:7334"));
        assertFalse("IPV6 1:2:3:4:5:6:7:8:9 should be invalid", validator.isValidInet6Address("1:2:3:4:5:6:7:8:9"));
        assertFalse("IPV6 1::2::3 should be invalid", validator.isValidInet6Address("1::2::3"));
        assertFalse("IPV6 1:::3:4:5 should be invalid", validator.isValidInet6Address("1:::3:4:5"));
        assertFalse("IPV6 1:2:3::4:5:6:7:8:9 should be invalid", validator.isValidInet6Address("1:2:3::4:5:6:7:8:9"));
        assertTrue("IPV6 1111:2222:3333:4444:5555:6666:7777:8888 should be valid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:7777:8888"));
        assertTrue("IPV6 1111:2222:3333:4444:5555:6666:7777:: should be valid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:7777::"));
        assertTrue("IPV6 1111:2222:3333:4444:5555:6666:: should be valid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666::"));
        assertTrue("IPV6 1111:2222:3333:4444:5555:: should be valid", validator.isValidInet6Address("1111:2222:3333:4444:5555::"));
        assertTrue("IPV6 1111:2222:3333:4444:: should be valid", validator.isValidInet6Address("1111:2222:3333:4444::"));
        assertTrue("IPV6 1111:2222:3333:: should be valid", validator.isValidInet6Address("1111:2222:3333::"));
        assertTrue("IPV6 1111:2222:: should be valid", validator.isValidInet6Address("1111:2222::"));
        assertTrue("IPV6 1111:: should be valid", validator.isValidInet6Address("1111::"));
        assertTrue("IPV6 1111:2222:3333:4444:5555:6666::8888 should be valid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666::8888"));
        assertTrue("IPV6 1111:2222:3333:4444:5555::8888 should be valid", validator.isValidInet6Address("1111:2222:3333:4444:5555::8888"));
        assertTrue("IPV6 1111:2222:3333:4444::8888 should be valid", validator.isValidInet6Address("1111:2222:3333:4444::8888"));
        assertTrue("IPV6 1111:2222:3333::8888 should be valid", validator.isValidInet6Address("1111:2222:3333::8888"));
        assertTrue("IPV6 1111:2222::8888 should be valid", validator.isValidInet6Address("1111:2222::8888"));
        assertTrue("IPV6 1111::8888 should be valid", validator.isValidInet6Address("1111::8888"));
        assertTrue("IPV6 ::8888 should be valid", validator.isValidInet6Address("::8888"));
        assertTrue("IPV6 1111:2222:3333:4444:5555::7777:8888 should be valid", validator.isValidInet6Address("1111:2222:3333:4444:5555::7777:8888"));
        assertTrue("IPV6 1111:2222:3333:4444::7777:8888 should be valid", validator.isValidInet6Address("1111:2222:3333:4444::7777:8888"));
        assertTrue("IPV6 1111:2222:3333::7777:8888 should be valid", validator.isValidInet6Address("1111:2222:3333::7777:8888"));
        assertTrue("IPV6 1111:2222::7777:8888 should be valid", validator.isValidInet6Address("1111:2222::7777:8888"));
        assertTrue("IPV6 1111::7777:8888 should be valid", validator.isValidInet6Address("1111::7777:8888"));
        assertTrue("IPV6 ::7777:8888 should be valid", validator.isValidInet6Address("::7777:8888"));
        assertTrue("IPV6 1111:2222:3333:4444::6666:7777:8888 should be valid", validator.isValidInet6Address("1111:2222:3333:4444::6666:7777:8888"));
        assertTrue("IPV6 1111:2222:3333::6666:7777:8888 should be valid", validator.isValidInet6Address("1111:2222:3333::6666:7777:8888"));
        assertTrue("IPV6 1111:2222::6666:7777:8888 should be valid", validator.isValidInet6Address("1111:2222::6666:7777:8888"));
        assertTrue("IPV6 1111::6666:7777:8888 should be valid", validator.isValidInet6Address("1111::6666:7777:8888"));
        assertTrue("IPV6 ::6666:7777:8888 should be valid", validator.isValidInet6Address("::6666:7777:8888"));
        assertTrue("IPV6 1111:2222:3333::5555:6666:7777:8888 should be valid", validator.isValidInet6Address("1111:2222:3333::5555:6666:7777:8888"));
        assertTrue("IPV6 1111:2222::5555:6666:7777:8888 should be valid", validator.isValidInet6Address("1111:2222::5555:6666:7777:8888"));
        assertTrue("IPV6 1111::5555:6666:7777:8888 should be valid", validator.isValidInet6Address("1111::5555:6666:7777:8888"));
        assertTrue("IPV6 ::5555:6666:7777:8888 should be valid", validator.isValidInet6Address("::5555:6666:7777:8888"));
        assertTrue("IPV6 1111:2222::4444:5555:6666:7777:8888 should be valid", validator.isValidInet6Address("1111:2222::4444:5555:6666:7777:8888"));
        assertTrue("IPV6 1111::4444:5555:6666:7777:8888 should be valid", validator.isValidInet6Address("1111::4444:5555:6666:7777:8888"));
        assertTrue("IPV6 ::4444:5555:6666:7777:8888 should be valid", validator.isValidInet6Address("::4444:5555:6666:7777:8888"));
        assertTrue("IPV6 1111::3333:4444:5555:6666:7777:8888 should be valid", validator.isValidInet6Address("1111::3333:4444:5555:6666:7777:8888"));
        assertTrue("IPV6 ::3333:4444:5555:6666:7777:8888 should be valid", validator.isValidInet6Address("::3333:4444:5555:6666:7777:8888"));
        assertTrue("IPV6 ::2222:3333:4444:5555:6666:7777:8888 should be valid", validator.isValidInet6Address("::2222:3333:4444:5555:6666:7777:8888"));
        assertTrue("IPV6 1111:2222:3333:4444:5555:6666:123.123.123.123 should be valid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:123.123.123.123"));
        assertTrue("IPV6 1111:2222:3333:4444:5555::123.123.123.123 should be valid", validator.isValidInet6Address("1111:2222:3333:4444:5555::123.123.123.123"));
        assertTrue("IPV6 1111:2222:3333:4444::123.123.123.123 should be valid", validator.isValidInet6Address("1111:2222:3333:4444::123.123.123.123"));
        assertTrue("IPV6 1111:2222:3333::123.123.123.123 should be valid", validator.isValidInet6Address("1111:2222:3333::123.123.123.123"));
        assertTrue("IPV6 1111:2222::123.123.123.123 should be valid", validator.isValidInet6Address("1111:2222::123.123.123.123"));
        assertTrue("IPV6 1111::123.123.123.123 should be valid", validator.isValidInet6Address("1111::123.123.123.123"));
        assertTrue("IPV6 ::123.123.123.123 should be valid", validator.isValidInet6Address("::123.123.123.123"));
        assertTrue("IPV6 1111:2222:3333:4444::6666:123.123.123.123 should be valid", validator.isValidInet6Address("1111:2222:3333:4444::6666:123.123.123.123"));
        assertTrue("IPV6 1111:2222:3333::6666:123.123.123.123 should be valid", validator.isValidInet6Address("1111:2222:3333::6666:123.123.123.123"));
        assertTrue("IPV6 1111:2222::6666:123.123.123.123 should be valid", validator.isValidInet6Address("1111:2222::6666:123.123.123.123"));
        assertTrue("IPV6 1111::6666:123.123.123.123 should be valid", validator.isValidInet6Address("1111::6666:123.123.123.123"));
        assertTrue("IPV6 ::6666:123.123.123.123 should be valid", validator.isValidInet6Address("::6666:123.123.123.123"));
        assertTrue("IPV6 1111:2222:3333::5555:6666:123.123.123.123 should be valid", validator.isValidInet6Address("1111:2222:3333::5555:6666:123.123.123.123"));
        assertTrue("IPV6 1111:2222::5555:6666:123.123.123.123 should be valid", validator.isValidInet6Address("1111:2222::5555:6666:123.123.123.123"));
        assertTrue("IPV6 1111::5555:6666:123.123.123.123 should be valid", validator.isValidInet6Address("1111::5555:6666:123.123.123.123"));
        assertTrue("IPV6 ::5555:6666:123.123.123.123 should be valid", validator.isValidInet6Address("::5555:6666:123.123.123.123"));
        assertTrue("IPV6 1111:2222::4444:5555:6666:123.123.123.123 should be valid", validator.isValidInet6Address("1111:2222::4444:5555:6666:123.123.123.123"));
        assertTrue("IPV6 1111::4444:5555:6666:123.123.123.123 should be valid", validator.isValidInet6Address("1111::4444:5555:6666:123.123.123.123"));
        assertTrue("IPV6 ::4444:5555:6666:123.123.123.123 should be valid", validator.isValidInet6Address("::4444:5555:6666:123.123.123.123"));
        assertTrue("IPV6 1111::3333:4444:5555:6666:123.123.123.123 should be valid", validator.isValidInet6Address("1111::3333:4444:5555:6666:123.123.123.123"));
        assertTrue("IPV6 ::2222:3333:4444:5555:6666:123.123.123.123 should be valid", validator.isValidInet6Address("::2222:3333:4444:5555:6666:123.123.123.123"));
        // Trying combinations of "0" and "::"
        // These are all syntactically correct, but are bad form
        // because "0" adjacent to "::" should be combined into "::"
        assertTrue("IPV6 ::0:0:0:0:0:0:0 should be valid", validator.isValidInet6Address("::0:0:0:0:0:0:0"));
        assertTrue("IPV6 ::0:0:0:0:0:0 should be valid", validator.isValidInet6Address("::0:0:0:0:0:0"));
        assertTrue("IPV6 ::0:0:0:0:0 should be valid", validator.isValidInet6Address("::0:0:0:0:0"));
        assertTrue("IPV6 ::0:0:0:0 should be valid", validator.isValidInet6Address("::0:0:0:0"));
        assertTrue("IPV6 ::0:0:0 should be valid", validator.isValidInet6Address("::0:0:0"));
        assertTrue("IPV6 ::0:0 should be valid", validator.isValidInet6Address("::0:0"));
        assertTrue("IPV6 ::0 should be valid", validator.isValidInet6Address("::0"));
        assertTrue("IPV6 0:0:0:0:0:0:0:: should be valid", validator.isValidInet6Address("0:0:0:0:0:0:0::"));
        assertTrue("IPV6 0:0:0:0:0:0:: should be valid", validator.isValidInet6Address("0:0:0:0:0:0::"));
        assertTrue("IPV6 0:0:0:0:0:: should be valid", validator.isValidInet6Address("0:0:0:0:0::"));
        assertTrue("IPV6 0:0:0:0:: should be valid", validator.isValidInet6Address("0:0:0:0::"));
        assertTrue("IPV6 0:0:0:: should be valid", validator.isValidInet6Address("0:0:0::"));
        assertTrue("IPV6 0:0:: should be valid", validator.isValidInet6Address("0:0::"));
        assertTrue("IPV6 0:: should be valid", validator.isValidInet6Address("0::"));
        // Invalid data
        assertFalse("IPV6 XXXX:XXXX:XXXX:XXXX:XXXX:XXXX:XXXX:XXXX should be invalid", validator.isValidInet6Address("XXXX:XXXX:XXXX:XXXX:XXXX:XXXX:XXXX:XXXX"));
        // Too many components
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666:7777:8888:9999 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:7777:8888:9999"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666:7777:8888:: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:7777:8888::"));
        assertFalse("IPV6 ::2222:3333:4444:5555:6666:7777:8888:9999 should be invalid", validator.isValidInet6Address("::2222:3333:4444:5555:6666:7777:8888:9999"));
        // Too few components
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666:7777 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:7777"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666"));
        assertFalse("IPV6 1111:2222:3333:4444:5555 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555"));
        assertFalse("IPV6 1111:2222:3333:4444 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444"));
        assertFalse("IPV6 1111:2222:3333 should be invalid", validator.isValidInet6Address("1111:2222:3333"));
        assertFalse("IPV6 1111:2222 should be invalid", validator.isValidInet6Address("1111:2222"));
        assertFalse("IPV6 1111 should be invalid", validator.isValidInet6Address("1111"));
        // Missing :
        assertFalse("IPV6 11112222:3333:4444:5555:6666:7777:8888 should be invalid", validator.isValidInet6Address("11112222:3333:4444:5555:6666:7777:8888"));
        assertFalse("IPV6 1111:22223333:4444:5555:6666:7777:8888 should be invalid", validator.isValidInet6Address("1111:22223333:4444:5555:6666:7777:8888"));
        assertFalse("IPV6 1111:2222:33334444:5555:6666:7777:8888 should be invalid", validator.isValidInet6Address("1111:2222:33334444:5555:6666:7777:8888"));
        assertFalse("IPV6 1111:2222:3333:44445555:6666:7777:8888 should be invalid", validator.isValidInet6Address("1111:2222:3333:44445555:6666:7777:8888"));
        assertFalse("IPV6 1111:2222:3333:4444:55556666:7777:8888 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:55556666:7777:8888"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:66667777:8888 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:66667777:8888"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666:77778888 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:77778888"));
        // Missing : intended for ::
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666:7777:8888: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:7777:8888:"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666:7777: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:7777:"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:"));
        assertFalse("IPV6 1111:2222:3333:4444:5555: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:"));
        assertFalse("IPV6 1111:2222:3333:4444: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:"));
        assertFalse("IPV6 1111:2222:3333: should be invalid", validator.isValidInet6Address("1111:2222:3333:"));
        assertFalse("IPV6 1111:2222: should be invalid", validator.isValidInet6Address("1111:2222:"));
        assertFalse("IPV6 :8888 should be invalid", validator.isValidInet6Address(":8888"));
        assertFalse("IPV6 :7777:8888 should be invalid", validator.isValidInet6Address(":7777:8888"));
        assertFalse("IPV6 :6666:7777:8888 should be invalid", validator.isValidInet6Address(":6666:7777:8888"));
        assertFalse("IPV6 :5555:6666:7777:8888 should be invalid", validator.isValidInet6Address(":5555:6666:7777:8888"));
        assertFalse("IPV6 :4444:5555:6666:7777:8888 should be invalid", validator.isValidInet6Address(":4444:5555:6666:7777:8888"));
        assertFalse("IPV6 :3333:4444:5555:6666:7777:8888 should be invalid", validator.isValidInet6Address(":3333:4444:5555:6666:7777:8888"));
        assertFalse("IPV6 :2222:3333:4444:5555:6666:7777:8888 should be invalid", validator.isValidInet6Address(":2222:3333:4444:5555:6666:7777:8888"));
        assertFalse("IPV6 :1111:2222:3333:4444:5555:6666:7777:8888 should be invalid", validator.isValidInet6Address(":1111:2222:3333:4444:5555:6666:7777:8888"));
        // :::
        assertFalse("IPV6 :::2222:3333:4444:5555:6666:7777:8888 should be invalid", validator.isValidInet6Address(":::2222:3333:4444:5555:6666:7777:8888"));
        assertFalse("IPV6 1111:::3333:4444:5555:6666:7777:8888 should be invalid", validator.isValidInet6Address("1111:::3333:4444:5555:6666:7777:8888"));
        assertFalse("IPV6 1111:2222:::4444:5555:6666:7777:8888 should be invalid", validator.isValidInet6Address("1111:2222:::4444:5555:6666:7777:8888"));
        assertFalse("IPV6 1111:2222:3333:::5555:6666:7777:8888 should be invalid", validator.isValidInet6Address("1111:2222:3333:::5555:6666:7777:8888"));
        assertFalse("IPV6 1111:2222:3333:4444:::6666:7777:8888 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:::6666:7777:8888"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:::7777:8888 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:::7777:8888"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666:::8888 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:::8888"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666:7777::: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:7777:::"));
        // Double ::
        assertFalse("IPV6 ::2222::4444:5555:6666:7777:8888 should be invalid", validator.isValidInet6Address("::2222::4444:5555:6666:7777:8888"));
        assertFalse("IPV6 ::2222:3333::5555:6666:7777:8888 should be invalid", validator.isValidInet6Address("::2222:3333::5555:6666:7777:8888"));
        assertFalse("IPV6 ::2222:3333:4444::6666:7777:8888 should be invalid", validator.isValidInet6Address("::2222:3333:4444::6666:7777:8888"));
        assertFalse("IPV6 ::2222:3333:4444:5555::7777:8888 should be invalid", validator.isValidInet6Address("::2222:3333:4444:5555::7777:8888"));
        assertFalse("IPV6 ::2222:3333:4444:5555:7777::8888 should be invalid", validator.isValidInet6Address("::2222:3333:4444:5555:7777::8888"));
        assertFalse("IPV6 ::2222:3333:4444:5555:7777:8888:: should be invalid", validator.isValidInet6Address("::2222:3333:4444:5555:7777:8888::"));
        assertFalse("IPV6 1111::3333::5555:6666:7777:8888 should be invalid", validator.isValidInet6Address("1111::3333::5555:6666:7777:8888"));
        assertFalse("IPV6 1111::3333:4444::6666:7777:8888 should be invalid", validator.isValidInet6Address("1111::3333:4444::6666:7777:8888"));
        assertFalse("IPV6 1111::3333:4444:5555::7777:8888 should be invalid", validator.isValidInet6Address("1111::3333:4444:5555::7777:8888"));
        assertFalse("IPV6 1111::3333:4444:5555:6666::8888 should be invalid", validator.isValidInet6Address("1111::3333:4444:5555:6666::8888"));
        assertFalse("IPV6 1111::3333:4444:5555:6666:7777:: should be invalid", validator.isValidInet6Address("1111::3333:4444:5555:6666:7777::"));
        assertFalse("IPV6 1111:2222::4444::6666:7777:8888 should be invalid", validator.isValidInet6Address("1111:2222::4444::6666:7777:8888"));
        assertFalse("IPV6 1111:2222::4444:5555::7777:8888 should be invalid", validator.isValidInet6Address("1111:2222::4444:5555::7777:8888"));
        assertFalse("IPV6 1111:2222::4444:5555:6666::8888 should be invalid", validator.isValidInet6Address("1111:2222::4444:5555:6666::8888"));
        assertFalse("IPV6 1111:2222::4444:5555:6666:7777:: should be invalid", validator.isValidInet6Address("1111:2222::4444:5555:6666:7777::"));
        assertFalse("IPV6 1111:2222:3333::5555::7777:8888 should be invalid", validator.isValidInet6Address("1111:2222:3333::5555::7777:8888"));
        assertFalse("IPV6 1111:2222:3333::5555:6666::8888 should be invalid", validator.isValidInet6Address("1111:2222:3333::5555:6666::8888"));
        assertFalse("IPV6 1111:2222:3333::5555:6666:7777:: should be invalid", validator.isValidInet6Address("1111:2222:3333::5555:6666:7777::"));
        assertFalse("IPV6 1111:2222:3333:4444::6666::8888 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444::6666::8888"));
        assertFalse("IPV6 1111:2222:3333:4444::6666:7777:: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444::6666:7777::"));
        assertFalse("IPV6 1111:2222:3333:4444:5555::7777:: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555::7777::"));
        // Too many components"
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666:7777:8888:1.2.3.4 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:7777:8888:1.2.3.4"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666:7777:1.2.3.4 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:7777:1.2.3.4"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666::1.2.3.4 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666::1.2.3.4"));
        assertFalse("IPV6 ::2222:3333:4444:5555:6666:7777:1.2.3.4 should be invalid", validator.isValidInet6Address("::2222:3333:4444:5555:6666:7777:1.2.3.4"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666:1.2.3.4.5 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:1.2.3.4.5"));
        // Too few components
        assertFalse("IPV6 1111:2222:3333:4444:5555:1.2.3.4 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:1.2.3.4"));
        assertFalse("IPV6 1111:2222:3333:4444:1.2.3.4 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:1.2.3.4"));
        assertFalse("IPV6 1111:2222:3333:1.2.3.4 should be invalid", validator.isValidInet6Address("1111:2222:3333:1.2.3.4"));
        assertFalse("IPV6 1111:2222:1.2.3.4 should be invalid", validator.isValidInet6Address("1111:2222:1.2.3.4"));
        assertFalse("IPV6 1111:1.2.3.4 should be invalid", validator.isValidInet6Address("1111:1.2.3.4"));
        assertFalse("IPV6 1.2.3.4 should be invalid", validator.isValidInet6Address("1.2.3.4"));
        // Missing :
        assertFalse("IPV6 11112222:3333:4444:5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address("11112222:3333:4444:5555:6666:1.2.3.4"));
        assertFalse("IPV6 1111:22223333:4444:5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address("1111:22223333:4444:5555:6666:1.2.3.4"));
        assertFalse("IPV6 1111:2222:33334444:5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address("1111:2222:33334444:5555:6666:1.2.3.4"));
        assertFalse("IPV6 1111:2222:3333:44445555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address("1111:2222:3333:44445555:6666:1.2.3.4"));
        assertFalse("IPV6 1111:2222:3333:4444:55556666:1.2.3.4 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:55556666:1.2.3.4"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:66661.2.3.4 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:66661.2.3.4"));
        // Missing .
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666:255255.255.255 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:255255.255.255"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666:255.255255.255 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:255.255255.255"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666:255.255.255255 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:255.255.255255"));
        // Missing : intended for ::
        assertFalse("IPV6 :1.2.3.4 should be invalid", validator.isValidInet6Address(":1.2.3.4"));
        assertFalse("IPV6 :6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":6666:1.2.3.4"));
        assertFalse("IPV6 :5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":5555:6666:1.2.3.4"));
        assertFalse("IPV6 :4444:5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":4444:5555:6666:1.2.3.4"));
        assertFalse("IPV6 :3333:4444:5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":3333:4444:5555:6666:1.2.3.4"));
        assertFalse("IPV6 :2222:3333:4444:5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":2222:3333:4444:5555:6666:1.2.3.4"));
        assertFalse("IPV6 :1111:2222:3333:4444:5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":1111:2222:3333:4444:5555:6666:1.2.3.4"));
        // :::
        assertFalse("IPV6 :::2222:3333:4444:5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":::2222:3333:4444:5555:6666:1.2.3.4"));
        assertFalse("IPV6 1111:::3333:4444:5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address("1111:::3333:4444:5555:6666:1.2.3.4"));
        assertFalse("IPV6 1111:2222:::4444:5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address("1111:2222:::4444:5555:6666:1.2.3.4"));
        assertFalse("IPV6 1111:2222:3333:::5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address("1111:2222:3333:::5555:6666:1.2.3.4"));
        assertFalse("IPV6 1111:2222:3333:4444:::6666:1.2.3.4 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:::6666:1.2.3.4"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:::1.2.3.4 should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:::1.2.3.4"));
        // Double ::
        assertFalse("IPV6 ::2222::4444:5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address("::2222::4444:5555:6666:1.2.3.4"));
        assertFalse("IPV6 ::2222:3333::5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address("::2222:3333::5555:6666:1.2.3.4"));
        assertFalse("IPV6 ::2222:3333:4444::6666:1.2.3.4 should be invalid", validator.isValidInet6Address("::2222:3333:4444::6666:1.2.3.4"));
        assertFalse("IPV6 ::2222:3333:4444:5555::1.2.3.4 should be invalid", validator.isValidInet6Address("::2222:3333:4444:5555::1.2.3.4"));
        assertFalse("IPV6 1111::3333::5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address("1111::3333::5555:6666:1.2.3.4"));
        assertFalse("IPV6 1111::3333:4444::6666:1.2.3.4 should be invalid", validator.isValidInet6Address("1111::3333:4444::6666:1.2.3.4"));
        assertFalse("IPV6 1111::3333:4444:5555::1.2.3.4 should be invalid", validator.isValidInet6Address("1111::3333:4444:5555::1.2.3.4"));
        assertFalse("IPV6 1111:2222::4444::6666:1.2.3.4 should be invalid", validator.isValidInet6Address("1111:2222::4444::6666:1.2.3.4"));
        assertFalse("IPV6 1111:2222::4444:5555::1.2.3.4 should be invalid", validator.isValidInet6Address("1111:2222::4444:5555::1.2.3.4"));
        assertFalse("IPV6 1111:2222:3333::5555::1.2.3.4 should be invalid", validator.isValidInet6Address("1111:2222:3333::5555::1.2.3.4"));
        // Missing parts
        assertFalse("IPV6 ::. should be invalid", validator.isValidInet6Address("::."));
        assertFalse("IPV6 ::.. should be invalid", validator.isValidInet6Address("::.."));
        assertFalse("IPV6 ::... should be invalid", validator.isValidInet6Address("::..."));
        assertFalse("IPV6 ::1... should be invalid", validator.isValidInet6Address("::1..."));
        assertFalse("IPV6 ::1.2.. should be invalid", validator.isValidInet6Address("::1.2.."));
        assertFalse("IPV6 ::1.2.3. should be invalid", validator.isValidInet6Address("::1.2.3."));
        assertFalse("IPV6 ::.2.. should be invalid", validator.isValidInet6Address("::.2.."));
        assertFalse("IPV6 ::.2.3. should be invalid", validator.isValidInet6Address("::.2.3."));
        assertFalse("IPV6 ::.2.3.4 should be invalid", validator.isValidInet6Address("::.2.3.4"));
        assertFalse("IPV6 ::..3. should be invalid", validator.isValidInet6Address("::..3."));
        assertFalse("IPV6 ::..3.4 should be invalid", validator.isValidInet6Address("::..3.4"));
        assertFalse("IPV6 ::...4 should be invalid", validator.isValidInet6Address("::...4"));
        // Extra : in front
        assertFalse("IPV6 :1111:2222:3333:4444:5555:6666:7777:: should be invalid", validator.isValidInet6Address(":1111:2222:3333:4444:5555:6666:7777::"));
        assertFalse("IPV6 :1111:2222:3333:4444:5555:6666:: should be invalid", validator.isValidInet6Address(":1111:2222:3333:4444:5555:6666::"));
        assertFalse("IPV6 :1111:2222:3333:4444:5555:: should be invalid", validator.isValidInet6Address(":1111:2222:3333:4444:5555::"));
        assertFalse("IPV6 :1111:2222:3333:4444:: should be invalid", validator.isValidInet6Address(":1111:2222:3333:4444::"));
        assertFalse("IPV6 :1111:2222:3333:: should be invalid", validator.isValidInet6Address(":1111:2222:3333::"));
        assertFalse("IPV6 :1111:2222:: should be invalid", validator.isValidInet6Address(":1111:2222::"));
        assertFalse("IPV6 :1111:: should be invalid", validator.isValidInet6Address(":1111::"));
        assertFalse("IPV6 :1111:2222:3333:4444:5555:6666::8888 should be invalid", validator.isValidInet6Address(":1111:2222:3333:4444:5555:6666::8888"));
        assertFalse("IPV6 :1111:2222:3333:4444:5555::8888 should be invalid", validator.isValidInet6Address(":1111:2222:3333:4444:5555::8888"));
        assertFalse("IPV6 :1111:2222:3333:4444::8888 should be invalid", validator.isValidInet6Address(":1111:2222:3333:4444::8888"));
        assertFalse("IPV6 :1111:2222:3333::8888 should be invalid", validator.isValidInet6Address(":1111:2222:3333::8888"));
        assertFalse("IPV6 :1111:2222::8888 should be invalid", validator.isValidInet6Address(":1111:2222::8888"));
        assertFalse("IPV6 :1111::8888 should be invalid", validator.isValidInet6Address(":1111::8888"));
        assertFalse("IPV6 :::8888 should be invalid", validator.isValidInet6Address(":::8888"));
        assertFalse("IPV6 :1111:2222:3333:4444:5555::7777:8888 should be invalid", validator.isValidInet6Address(":1111:2222:3333:4444:5555::7777:8888"));
        assertFalse("IPV6 :1111:2222:3333:4444::7777:8888 should be invalid", validator.isValidInet6Address(":1111:2222:3333:4444::7777:8888"));
        assertFalse("IPV6 :1111:2222:3333::7777:8888 should be invalid", validator.isValidInet6Address(":1111:2222:3333::7777:8888"));
        assertFalse("IPV6 :1111:2222::7777:8888 should be invalid", validator.isValidInet6Address(":1111:2222::7777:8888"));
        assertFalse("IPV6 :1111::7777:8888 should be invalid", validator.isValidInet6Address(":1111::7777:8888"));
        assertFalse("IPV6 :::7777:8888 should be invalid", validator.isValidInet6Address(":::7777:8888"));
        assertFalse("IPV6 :1111:2222:3333:4444::6666:7777:8888 should be invalid", validator.isValidInet6Address(":1111:2222:3333:4444::6666:7777:8888"));
        assertFalse("IPV6 :1111:2222:3333::6666:7777:8888 should be invalid", validator.isValidInet6Address(":1111:2222:3333::6666:7777:8888"));
        assertFalse("IPV6 :1111:2222::6666:7777:8888 should be invalid", validator.isValidInet6Address(":1111:2222::6666:7777:8888"));
        assertFalse("IPV6 :1111::6666:7777:8888 should be invalid", validator.isValidInet6Address(":1111::6666:7777:8888"));
        assertFalse("IPV6 :::6666:7777:8888 should be invalid", validator.isValidInet6Address(":::6666:7777:8888"));
        assertFalse("IPV6 :1111:2222:3333::5555:6666:7777:8888 should be invalid", validator.isValidInet6Address(":1111:2222:3333::5555:6666:7777:8888"));
        assertFalse("IPV6 :1111:2222::5555:6666:7777:8888 should be invalid", validator.isValidInet6Address(":1111:2222::5555:6666:7777:8888"));
        assertFalse("IPV6 :1111::5555:6666:7777:8888 should be invalid", validator.isValidInet6Address(":1111::5555:6666:7777:8888"));
        assertFalse("IPV6 :::5555:6666:7777:8888 should be invalid", validator.isValidInet6Address(":::5555:6666:7777:8888"));
        assertFalse("IPV6 :1111:2222::4444:5555:6666:7777:8888 should be invalid", validator.isValidInet6Address(":1111:2222::4444:5555:6666:7777:8888"));
        assertFalse("IPV6 :1111::4444:5555:6666:7777:8888 should be invalid", validator.isValidInet6Address(":1111::4444:5555:6666:7777:8888"));
        assertFalse("IPV6 :::4444:5555:6666:7777:8888 should be invalid", validator.isValidInet6Address(":::4444:5555:6666:7777:8888"));
        assertFalse("IPV6 :1111::3333:4444:5555:6666:7777:8888 should be invalid", validator.isValidInet6Address(":1111::3333:4444:5555:6666:7777:8888"));
        assertFalse("IPV6 :::3333:4444:5555:6666:7777:8888 should be invalid", validator.isValidInet6Address(":::3333:4444:5555:6666:7777:8888"));
        assertFalse("IPV6 :::2222:3333:4444:5555:6666:7777:8888 should be invalid", validator.isValidInet6Address(":::2222:3333:4444:5555:6666:7777:8888"));
        assertFalse("IPV6 :1111:2222:3333:4444:5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":1111:2222:3333:4444:5555:6666:1.2.3.4"));
        assertFalse("IPV6 :1111:2222:3333:4444:5555::1.2.3.4 should be invalid", validator.isValidInet6Address(":1111:2222:3333:4444:5555::1.2.3.4"));
        assertFalse("IPV6 :1111:2222:3333:4444::1.2.3.4 should be invalid", validator.isValidInet6Address(":1111:2222:3333:4444::1.2.3.4"));
        assertFalse("IPV6 :1111:2222:3333::1.2.3.4 should be invalid", validator.isValidInet6Address(":1111:2222:3333::1.2.3.4"));
        assertFalse("IPV6 :1111:2222::1.2.3.4 should be invalid", validator.isValidInet6Address(":1111:2222::1.2.3.4"));
        assertFalse("IPV6 :1111::1.2.3.4 should be invalid", validator.isValidInet6Address(":1111::1.2.3.4"));
        assertFalse("IPV6 :::1.2.3.4 should be invalid", validator.isValidInet6Address(":::1.2.3.4"));
        assertFalse("IPV6 :1111:2222:3333:4444::6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":1111:2222:3333:4444::6666:1.2.3.4"));
        assertFalse("IPV6 :1111:2222:3333::6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":1111:2222:3333::6666:1.2.3.4"));
        assertFalse("IPV6 :1111:2222::6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":1111:2222::6666:1.2.3.4"));
        assertFalse("IPV6 :1111::6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":1111::6666:1.2.3.4"));
        assertFalse("IPV6 :::6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":::6666:1.2.3.4"));
        assertFalse("IPV6 :1111:2222:3333::5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":1111:2222:3333::5555:6666:1.2.3.4"));
        assertFalse("IPV6 :1111:2222::5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":1111:2222::5555:6666:1.2.3.4"));
        assertFalse("IPV6 :1111::5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":1111::5555:6666:1.2.3.4"));
        assertFalse("IPV6 :::5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":::5555:6666:1.2.3.4"));
        assertFalse("IPV6 :1111:2222::4444:5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":1111:2222::4444:5555:6666:1.2.3.4"));
        assertFalse("IPV6 :1111::4444:5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":1111::4444:5555:6666:1.2.3.4"));
        assertFalse("IPV6 :::4444:5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":::4444:5555:6666:1.2.3.4"));
        assertFalse("IPV6 :1111::3333:4444:5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":1111::3333:4444:5555:6666:1.2.3.4"));
        assertFalse("IPV6 :::2222:3333:4444:5555:6666:1.2.3.4 should be invalid", validator.isValidInet6Address(":::2222:3333:4444:5555:6666:1.2.3.4"));
        // Extra : at end
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666:7777::: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:7777:::"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666::: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666:::"));
        assertFalse("IPV6 1111:2222:3333:4444:5555::: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:::"));
        assertFalse("IPV6 1111:2222:3333:4444::: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:::"));
        assertFalse("IPV6 1111:2222:3333::: should be invalid", validator.isValidInet6Address("1111:2222:3333:::"));
        assertFalse("IPV6 1111:2222::: should be invalid", validator.isValidInet6Address("1111:2222:::"));
        assertFalse("IPV6 1111::: should be invalid", validator.isValidInet6Address("1111:::"));
        assertFalse("IPV6 1111:2222:3333:4444:5555:6666::8888: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555:6666::8888:"));
        assertFalse("IPV6 1111:2222:3333:4444:5555::8888: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555::8888:"));
        assertFalse("IPV6 1111:2222:3333:4444::8888: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444::8888:"));
        assertFalse("IPV6 1111:2222:3333::8888: should be invalid", validator.isValidInet6Address("1111:2222:3333::8888:"));
        assertFalse("IPV6 1111:2222::8888: should be invalid", validator.isValidInet6Address("1111:2222::8888:"));
        assertFalse("IPV6 1111::8888: should be invalid", validator.isValidInet6Address("1111::8888:"));
        assertFalse("IPV6 ::8888: should be invalid", validator.isValidInet6Address("::8888:"));
        assertFalse("IPV6 1111:2222:3333:4444:5555::7777:8888: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444:5555::7777:8888:"));
        assertFalse("IPV6 1111:2222:3333:4444::7777:8888: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444::7777:8888:"));
        assertFalse("IPV6 1111:2222:3333::7777:8888: should be invalid", validator.isValidInet6Address("1111:2222:3333::7777:8888:"));
        assertFalse("IPV6 1111:2222::7777:8888: should be invalid", validator.isValidInet6Address("1111:2222::7777:8888:"));
        assertFalse("IPV6 1111::7777:8888: should be invalid", validator.isValidInet6Address("1111::7777:8888:"));
        assertFalse("IPV6 ::7777:8888: should be invalid", validator.isValidInet6Address("::7777:8888:"));
        assertFalse("IPV6 1111:2222:3333:4444::6666:7777:8888: should be invalid", validator.isValidInet6Address("1111:2222:3333:4444::6666:7777:8888:"));
        assertFalse("IPV6 1111:2222:3333::6666:7777:8888: should be invalid", validator.isValidInet6Address("1111:2222:3333::6666:7777:8888:"));
        assertFalse("IPV6 1111:2222::6666:7777:8888: should be invalid", validator.isValidInet6Address("1111:2222::6666:7777:8888:"));
        assertFalse("IPV6 1111::6666:7777:8888: should be invalid", validator.isValidInet6Address("1111::6666:7777:8888:"));
        assertFalse("IPV6 ::6666:7777:8888: should be invalid", validator.isValidInet6Address("::6666:7777:8888:"));
        assertFalse("IPV6 1111:2222:3333::5555:6666:7777:8888: should be invalid", validator.isValidInet6Address("1111:2222:3333::5555:6666:7777:8888:"));
        assertFalse("IPV6 1111:2222::5555:6666:7777:8888: should be invalid", validator.isValidInet6Address("1111:2222::5555:6666:7777:8888:"));
        assertFalse("IPV6 1111::5555:6666:7777:8888: should be invalid", validator.isValidInet6Address("1111::5555:6666:7777:8888:"));
        assertFalse("IPV6 ::5555:6666:7777:8888: should be invalid", validator.isValidInet6Address("::5555:6666:7777:8888:"));
        assertFalse("IPV6 1111:2222::4444:5555:6666:7777:8888: should be invalid", validator.isValidInet6Address("1111:2222::4444:5555:6666:7777:8888:"));
        assertFalse("IPV6 1111::4444:5555:6666:7777:8888: should be invalid", validator.isValidInet6Address("1111::4444:5555:6666:7777:8888:"));
        assertFalse("IPV6 ::4444:5555:6666:7777:8888: should be invalid", validator.isValidInet6Address("::4444:5555:6666:7777:8888:"));
        assertFalse("IPV6 1111::3333:4444:5555:6666:7777:8888: should be invalid", validator.isValidInet6Address("1111::3333:4444:5555:6666:7777:8888:"));
        assertFalse("IPV6 ::3333:4444:5555:6666:7777:8888: should be invalid", validator.isValidInet6Address("::3333:4444:5555:6666:7777:8888:"));
        assertFalse("IPV6 ::2222:3333:4444:5555:6666:7777:8888: should be invalid", validator.isValidInet6Address("::2222:3333:4444:5555:6666:7777:8888:"));
        assertTrue("IPV6 0:a:b:c:d:e:f:: should be valid", validator.isValidInet6Address("0:a:b:c:d:e:f::"));
        assertTrue("IPV6 ::0:a:b:c:d:e:f should be valid", validator.isValidInet6Address("::0:a:b:c:d:e:f")); // syntactically correct, but bad form (::0:... could be combined)
        assertTrue("IPV6 a:b:c:d:e:f:0:: should be valid", validator.isValidInet6Address("a:b:c:d:e:f:0::"));
        assertFalse("IPV6 ':10.0.0.1 should be invalid", validator.isValidInet6Address("':10.0.0.1"));
    }
}
