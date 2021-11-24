package jenkins.telemetry.impl;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SlaveToMasterFileCallableUsageTest {
    @Test
    public void ignoreObjectIdentity() {
        assertGeneralization("Command UserRequest:hudson.FilePath$CopyTo@… created at", "Command UserRequest:hudson.FilePath$CopyTo@abcdef12 created at");

        assertUnmodified("FilePath$CopyTo-abcdef12 created at");
        assertUnmodified("abcdef12 created at");
        assertUnmodified("\tat hudson.FilePath.readToString(FilePath.java:2289)");
    }

    @Test
    public void ignoreRPCRequestOid() {
        assertGeneralization("Command UserRequest:UserRPCRequest:hudson.maven.MavenBuildProxy2.end[](…) created at", "Command UserRequest:UserRPCRequest:hudson.maven.MavenBuildProxy2.end[](16) created at");
        assertGeneralization("Command UserRequest:UserRPCRequest:hudson.maven.MavenBuildProxy2.end[org.acme.FakeType](…) created at", "Command UserRequest:UserRPCRequest:hudson.maven.MavenBuildProxy2.end[org.acme.FakeType](16) created at");
    }

    @Test
    public void ignoreProxyIndex() {
        assertGeneralization("at com.sun.proxy.$Proxy….end(Unknown Source)", "at com.sun.proxy.$Proxy6.end(Unknown Source)");
        assertGeneralization("at com.sun.proxy.$Proxy….end(Unknown Source)", "at com.sun.proxy.$Proxy66.end(Unknown Source)");
        assertGeneralization("at com.sun.proxy.$Proxy….begin(Unknown Source)", "at com.sun.proxy.$Proxy66.begin(Unknown Source)");
    }

    private static void assertGeneralization(String generalized, String actual) {
        assertEquals(generalized, SlaveToMasterFileCallableUsage.generalize(actual));
    }

    private static void assertUnmodified(String actual) {
        assertEquals(actual, SlaveToMasterFileCallableUsage.generalize(actual));
    }
}
