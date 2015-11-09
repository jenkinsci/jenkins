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
package jenkins.security.security218.ysoserial;

import java.security.Permission;
import java.util.concurrent.Callable;

public class ExecBlockingSecurityManager extends SecurityManager {
	@Override
	public void checkPermission(final Permission perm) { }
	
	@Override
	public void checkPermission(final Permission perm, final Object context) { }			
	
	public void checkExec(final String cmd) {
		super.checkExec(cmd);
		// throw a special exception to ensure we can detect exec() in the test
		throw new ExecException(cmd);
	};
	
	@SuppressWarnings("serial")
	public static class ExecException extends RuntimeException {
		private final String cmd;
		public ExecException(String cmd) { this.cmd = cmd; }
		public String getCmd() { return cmd; }		
	}		
	
	public static void wrap(final Runnable runnable) throws Exception {
		wrap(new Callable<Void>(){
			public Void call() throws Exception {
				runnable.run();
				return null;
			}			
		});		
	}
	
	public static <T> T wrap(final Callable<T> callable) throws Exception {
		SecurityManager sm = System.getSecurityManager();
		System.setSecurityManager(new ExecBlockingSecurityManager());
		try {
			return callable.call();
		} finally {
			System.setSecurityManager(sm);
		}		
	}
}