/*
 * The MIT License
 *
 * Copyright (c) 2013 Chris Frohoff
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
package jenkins.security.ysoserial.util;


import java.util.concurrent.Callable;
import jenkins.security.ysoserial.ExecBlockingSecurityManager;
import jenkins.security.ysoserial.payloads.ObjectPayload;
import static jenkins.security.ysoserial.util.Serializables.deserialize;
import static jenkins.security.ysoserial.util.Serializables.serialize;

/*
 * utility class for running exploits locally from command line
 */
@SuppressWarnings("unused")
public class PayloadRunner {
	public static void run(final Class<? extends ObjectPayload<?>> clazz, final String[] args) throws Exception {		
		// ensure payload generation doesn't throw an exception
		byte[] serialized = ExecBlockingSecurityManager.wrap(new Callable<byte[]>(){
			public byte[] call() throws Exception {
				final String command = args.length > 0 && args[0] != null ? args[0] : "calc.exe";
				
				System.out.println("generating payload object(s) for command: '" + command + "'");
				
				final Object objBefore = clazz.newInstance().getObject(command);
				
				System.out.println("serializing payload");
				
				return serialize(objBefore);
		}});			
			
		try {	
			System.out.println("deserializing payload");			
			final Object objAfter = deserialize(serialized);			
		} catch (Exception e) {
			e.printStackTrace();
		}

	}	
	
}
