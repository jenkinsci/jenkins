package jenkins.security.security218.ysoserial.payloads.util;

import java.util.concurrent.Callable;

import jenkins.security.security218.ysoserial.Deserializer;
import jenkins.security.security218.ysoserial.Serializer;
import static jenkins.security.security218.ysoserial.Deserializer.deserialize;
import static jenkins.security.security218.ysoserial.Serializer.serialize;
import jenkins.security.security218.ysoserial.payloads.ObjectPayload;
import jenkins.security.security218.ysoserial.payloads.ObjectPayload.Utils;
import jenkins.security.security218.ysoserial.secmgr.ExecCheckingSecurityManager;

/*
 * utility class for running exploits locally from command line
 */
@SuppressWarnings("unused")
public class PayloadRunner {
	public static void run(final Class<? extends ObjectPayload<?>> clazz, final String[] args) throws Exception {
		// ensure payload generation doesn't throw an exception
		byte[] serialized = new ExecCheckingSecurityManager().wrap(new Callable<byte[]>(){
			public byte[] call() throws Exception {
				final String command = args.length > 0 && args[0] != null ? args[0] : "calc.exe";

				System.out.println("generating payload object(s) for command: '" + command + "'");

				ObjectPayload<?> payload = clazz.newInstance();
                final Object objBefore = payload.getObject(command);

				System.out.println("serializing payload");
				byte[] ser = Serializer.serialize(objBefore);
				Utils.releasePayload(payload, objBefore);
                return ser;
		}});

		try {
			System.out.println("deserializing payload");
			final Object objAfter = Deserializer.deserialize(serialized);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
