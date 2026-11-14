package hudson.node_monitors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import hudson.node_monitors.ArchitectureMonitor.OsDetails;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArchitectureMonitorTest {

    @Test
    void windows11ReportedAsWindows10InRegistry() {
        assertEquals("Windows 11 Pro 25H2 · Build 26200.6883",
                OsDetails.describeWindows("Windows 11", "Windows 10 Pro", "25H2", "26200", "0x1ae3"));
    }

    @Test
    void windows10() {
        assertEquals("Windows 10 Enterprise 22H2 · Build 19045.5011",
                OsDetails.describeWindows("Windows 10", "Windows 10 Enterprise", "22H2", "19045", "0x1393"));
    }

    @Test
    void windowsServer() {
        assertEquals("Windows Server 2022 Datacenter 21H2 · Build 20348.2762",
                OsDetails.describeWindows("Windows Server 2022", "Windows Server 2022 Datacenter", "21H2", "20348", "0xaca"));
    }

    @Test
    void windowsWithoutRegistry() {
        assertEquals("Windows 11", OsDetails.describeWindows("Windows 11", null, null, null, null));
        assertEquals("Windows 11 · Build 26200", OsDetails.describeWindows("Windows 11", null, null, "26200", "bogus"));
        assertEquals("Windows 11", OsDetails.describeWindows("Windows 11", null, null, "bogus", null));
    }

    @Test
    void parseRegistryValue() {
        assertEquals("Windows 10 Pro", OsDetails.parseRegistryValue("    ProductName    REG_SZ    Windows 10 Pro"));
        assertEquals("0x1ae3", OsDetails.parseRegistryValue("    UBR    REG_DWORD    0x1ae3  "));
        assertNull(OsDetails.parseRegistryValue("ERROR: The system was unable to find the specified registry key or value."));
        assertNull(OsDetails.parseRegistryValue(null));
    }

    @Test
    void parsePrettyName() {
        assertEquals("Ubuntu 24.04.1 LTS", OsDetails.parsePrettyName(List.of(
                "NAME=\"Ubuntu\"",
                "VERSION=\"24.04.1 LTS (Noble Numbat)\"",
                "PRETTY_NAME=\"Ubuntu 24.04.1 LTS\"",
                "ID=ubuntu")));
        assertEquals("Alpine Linux v3.20", OsDetails.parsePrettyName(List.of("NAME=\"Alpine Linux\"", "PRETTY_NAME='Alpine Linux v3.20'")));
        assertEquals("Fedora Linux 41", OsDetails.parsePrettyName(List.of("PRETTY_NAME=Fedora\\ Linux\\ 41")));
        assertEquals("Say \"hi\"", OsDetails.parsePrettyName(List.of("PRETTY_NAME=\"Say \\\"hi\\\"\"")));
    }

    @Test
    void parsePrettyNameFallsBackToNameAndVersion() {
        assertEquals("Example OS 1.0", OsDetails.parsePrettyName(List.of("# comment", "NAME=Example OS", "VERSION=\"1.0\"", "PRETTY_NAME=")));
        assertEquals("Example OS", OsDetails.parsePrettyName(List.of("NAME=Example OS")));
        assertNull(OsDetails.parsePrettyName(List.of("ID=example")));
    }
}
