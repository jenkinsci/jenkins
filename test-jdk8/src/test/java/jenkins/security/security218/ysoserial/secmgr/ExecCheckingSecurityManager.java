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

package jenkins.security.security218.ysoserial.secmgr;

import java.security.Permission;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

public class ExecCheckingSecurityManager extends SecurityManager {
	public ExecCheckingSecurityManager() {
		this(true);
	}

	public ExecCheckingSecurityManager(boolean throwException) {
		this.throwException = throwException;
	}

	private final boolean throwException;

	private final List<String> cmds = new LinkedList<String>();

	public List<String> getCmds() {
		return Collections.unmodifiableList(cmds);
	}

	@Override
	public void checkPermission(final Permission perm) { }

	@Override
	public void checkPermission(final Permission perm, final Object context) { }

	@Override
	public void checkExec(final String cmd) {
		super.checkExec(cmd);

		cmds.add(cmd);

		if (throwException) {
			// throw a special exception to ensure we can detect exec() in the test
			throw new ExecException(cmd);
		}
	};


	@SuppressWarnings("serial")
	public static class ExecException extends RuntimeException {
		private final String threadName = Thread.currentThread().getName();
		private final String cmd;
		public ExecException(String cmd) { this.cmd = cmd; }
		public String getCmd() { return cmd; }
		public String getThreadName() { return threadName; }
		@
		Override
		public String getMessage() {
			return "executed `" + getCmd() + "` in [" + getThreadName() + "]";
		}
	}

	public void wrap(final Runnable runnable) throws Exception {
		wrap(new Callable<Void>(){
			public Void call() throws Exception {
				runnable.run();
				return null;
			}
		});
	}

	public <T> T wrap(final Callable<T> callable) throws Exception {
		SecurityManager sm = System.getSecurityManager(); // save sm
		System.setSecurityManager(this);
		try {
			T result = callable.call();
			if (throwException && ! getCmds().isEmpty()) {
				throw new ExecException(getCmds().get(0));
			}
			return result;
		} catch (Exception e) {
			if (! (e instanceof ExecException) && throwException && ! getCmds().isEmpty()) {
				throw new ExecException(getCmds().get(0));
			} else {
				throw e;
			}
		} finally {
			System.setSecurityManager(sm); // restore sm
		}
	}
}