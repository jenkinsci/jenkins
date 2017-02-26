package jenkins.security.security218.ysoserial.payloads;

import jenkins.security.security218.ysoserial.util.PayloadRunner;

import java.lang.reflect.Constructor;

/**
 * @author Kohsuke Kawaguchi
 */
public class Ldap extends PayloadRunner implements ObjectPayload<Object> {

	public Object getObject(final String command) throws Exception {
	    // this is not a fully exploit, so we cannot honor the command,
        // but we want to check that we are blocking LdapAttribute
        Class<?> c = Class.forName("com.sun.jndi.ldap.LdapAttribute");
        Constructor<?> ctr = c.getDeclaredConstructor(String.class);
        ctr.setAccessible(true);
        return ctr.newInstance("foo");
	}

	public static void main(final String[] args) throws Exception {
		PayloadRunner.run(Ldap.class, args);
	}
}
