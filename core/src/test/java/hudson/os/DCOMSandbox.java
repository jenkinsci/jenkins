package hudson.os;

import ndr.NdrObject;
import ndr.NetworkDataRepresentation;
import org.jinterop.dcom.transport.JIComTransportFactory;
import rpc.Endpoint;
import rpc.Stub;

import java.util.Properties;

/**
 * My attempt to see if ServerAlive calls can be used to detect an authentication failure
 * (so that I can differentiate authentication problem against authorization problem in
 * creating an instance.
 *
 * <p>
 * It turns out that the bogus credential works with ServerAlive.  The protocol specification
 * <http://download.microsoft.com/download/a/e/6/ae6e4142-aa58-45c6-8dcf-a657e5900cd3/%5BMS-DCOM%5D.pdf>
 * explicitly says this RPC must not check the credential.
 *
 * <p>
 * The feature in question of Windows is called "ForceGuest", and it's recorded in the registry at
 * HKLM\SYSTEM\CurrentControlSet\Control\LSA\forceguest (0=classic, 1=forceguest).
 * <http://support.microsoft.com/kb/290403>
 *
 * @author Kohsuke Kawaguchi
 */
public class DCOMSandbox {
    public static void main(String[] args) throws Exception {
        new JIComOxidStub("129.145.133.224", "", "bogus", "bogus").serverAlive();
    }

    static final class JIComOxidStub extends Stub {

        private static Properties defaults = new Properties();

        static {
                defaults.put("rpc.ntlm.lanManagerKey","false");
                defaults.put("rpc.ntlm.sign","false");
                defaults.put("rpc.ntlm.seal","false");
                defaults.put("rpc.ntlm.keyExchange","false");
                defaults.put("rpc.connectionContext","rpc.security.ntlm.NtlmConnectionContext");
        }

        protected String getSyntax() {
            return "99fcfec4-5260-101b-bbcb-00aa0021347a:0.0";
        }

        public JIComOxidStub(String address, String domain, String username, String password) {
            setTransportFactory(JIComTransportFactory.getSingleTon());
            setProperties(new Properties(defaults));
            getProperties().setProperty("rpc.security.username", username);
            getProperties().setProperty("rpc.security.password", password);
            getProperties().setProperty("rpc.ntlm.domain", domain);
            setAddress("ncacn_ip_tcp:" + address + "[135]");

        }

        public void serverAlive() throws Exception {
            call(Endpoint.IDEMPOTENT, new ServerAlive());
        }
    }

    static class ServerAlive extends NdrObject {
        // see http://www.hsc.fr/ressources/articles/win_net_srv/rpcss_dcom_interfaces.html

        public int getOpnum() {
            return 3;
        }

        public void write(NetworkDataRepresentation ndr) {
            // no parameter
        }

        public void read(NetworkDataRepresentation ndr) {
            System.out.println("Got " + ndr.readUnsignedLong());
        }
    }
}
