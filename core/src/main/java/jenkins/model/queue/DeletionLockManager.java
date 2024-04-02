package jenkins.model.queue;

import hudson.model.Item;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import net.jcip.annotations.GuardedBy;
import org.springframework.lang.NonNull;

public class DeletionLockManager {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    @GuardedBy("lock")
    private final Set<Item> registrations = new HashSet<>();

    public boolean register(@NonNull Item item) {
        lock.writeLock().lock();
        try {
            return registrations.add(item);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deregister(@NonNull Item item) {
        lock.writeLock().lock();
        try {
            registrations.remove(item);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isRegistered(@NonNull Item item) {
        lock.readLock().lock();
        try {
            return registrations.contains(item);
        } finally {
            lock.readLock().unlock();
        }
    }
}
