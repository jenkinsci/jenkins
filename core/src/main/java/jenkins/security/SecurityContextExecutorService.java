package jenkins.security;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;

public class SecurityContextExecutorService implements ExecutorService {
	private final ExecutorService service;
	private final SecurityContext initialContext = SecurityContextHolder.getContext(); 

	public SecurityContextExecutorService(ExecutorService service){
		this.service = service;
	}
	public static SecurityContextExecutorService wrapExecutorWithSecurityContext(ExecutorService service){
		return new SecurityContextExecutorService(service);		
	}
	public void execute(Runnable arg0) {
		 SecurityContext executorContext = SecurityContextHolder.getContext(); 
         SecurityContextHolder.setContext(initialContext); 
         try { 
        	 service.execute(arg0);
         } finally { 
             SecurityContextHolder.setContext(executorContext); 
         } 		
	}
	public boolean awaitTermination(long timeout, TimeUnit unit)
			throws InterruptedException {
       	 return service.awaitTermination(timeout, unit);
	}
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
			throws InterruptedException {
		SecurityContext executorContext = SecurityContextHolder.getContext(); 
        SecurityContextHolder.setContext(initialContext); 
        try { 
       	 return service.invokeAll(tasks);
        } finally { 
            SecurityContextHolder.setContext(executorContext); 
        }
	}
	public <T> List<Future<T>> invokeAll(
			Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException {
		SecurityContext executorContext = SecurityContextHolder.getContext(); 
        SecurityContextHolder.setContext(initialContext); 
        try { 
       	 return service.invokeAll(tasks, timeout, unit);
        } finally { 
            SecurityContextHolder.setContext(executorContext); 
        }
	}
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
			throws InterruptedException, ExecutionException {
		SecurityContext executorContext = SecurityContextHolder.getContext(); 
        SecurityContextHolder.setContext(initialContext); 
        try { 
       	 return service.invokeAny(tasks);
        } finally { 
            SecurityContextHolder.setContext(executorContext); 
        }
	}
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
			long timeout, TimeUnit unit) throws InterruptedException,
			ExecutionException, TimeoutException {
		SecurityContext executorContext = SecurityContextHolder.getContext(); 
        SecurityContextHolder.setContext(initialContext); 
        try { 
       	 return service.invokeAny(tasks, timeout, unit);
        } finally { 
            SecurityContextHolder.setContext(executorContext); 
        }
	}
	public boolean isShutdown() {
		return service.isShutdown();
	}
	public boolean isTerminated() {
		return service.isTerminated();
	}
	public void shutdown() {
		service.shutdown();
	}
	public List<Runnable> shutdownNow() {
		return service.shutdownNow();
	}
	public <T> Future<T> submit(Callable<T> task) {
		SecurityContext executorContext = SecurityContextHolder.getContext(); 
        SecurityContextHolder.setContext(initialContext); 
        try { 
       	 return service.submit(task);
        } finally { 
            SecurityContextHolder.setContext(executorContext); 
        }
	}
	public Future<?> submit(Runnable task) {
		SecurityContext executorContext = SecurityContextHolder.getContext(); 
        SecurityContextHolder.setContext(initialContext); 
        try { 
       	 return service.submit(task);
        } finally { 
            SecurityContextHolder.setContext(executorContext); 
        }
	}
	public <T> Future<T> submit(Runnable task, T result) {
		SecurityContext executorContext = SecurityContextHolder.getContext(); 
        SecurityContextHolder.setContext(initialContext); 
        try { 
       	 return service.submit(task, result);
        } finally { 
            SecurityContextHolder.setContext(executorContext); 
        }
	}

}
