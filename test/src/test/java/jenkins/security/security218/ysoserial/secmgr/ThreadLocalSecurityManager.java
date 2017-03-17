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

import java.util.concurrent.Callable;

public class ThreadLocalSecurityManager extends DelegateSecurityManager {

	private final ThreadLocal<SecurityManager> threadDelegates
		= new ThreadLocal<SecurityManager>();

	public void install() {
		System.setSecurityManager(this);
	}

	@Override
	public void setSecurityManager(SecurityManager threadManager) {
		threadDelegates.set(threadManager);
	}

	@Override
	public SecurityManager getSecurityManager() {
		return threadDelegates.get();
	}

	public <V> V wrap(SecurityManager sm, Callable<V> callable) throws Exception {
		SecurityManager old = getSecurityManager();
		setSecurityManager(sm);
		try {
			return callable.call();
		} finally {
			setSecurityManager(old);
		}
	}
}
