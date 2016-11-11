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
