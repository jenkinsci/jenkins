package jenkins.security;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * There are cases where a plugin need to provide a {@link QueueItemAuthenticator} that cannot be controlled or
 * configured by the user. This extension point provides the mechanism whereby the a {@link QueueItemAuthenticator}
 * can be provided either before or after those provided by {@link QueueItemAuthenticatorConfiguration} which
 * will use {@link Extension#ordinal()} of {@code 100}
 * @since 1.592
 */
public abstract class QueueItemAuthenticatorProvider implements ExtensionPoint {

    @Nonnull
    public abstract List<QueueItemAuthenticator> getAuthenticators();

    public static Iterable<QueueItemAuthenticator> authenticators() {
        return new IterableImpl();
    }

    private static class IteratorImpl implements Iterator<QueueItemAuthenticator> {
        private final Iterator<QueueItemAuthenticatorProvider> providers;
        private Iterator<QueueItemAuthenticator> delegate = null;

        private IteratorImpl() {
            providers = ExtensionList.lookup(QueueItemAuthenticatorProvider.class).iterator();
        }

        @Override
        public boolean hasNext() {
            while ((delegate == null || !delegate.hasNext()) && (providers.hasNext())) {
                final QueueItemAuthenticatorProvider provider = providers.next();
                if (provider == null) {
                    continue;
                }
                delegate = new ArrayList<QueueItemAuthenticator>(provider.getAuthenticators()).iterator();
            }
            return delegate != null && delegate.hasNext();
        }

        @Override
        public QueueItemAuthenticator next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return delegate.next();
        }

        @Override
        public void remove() {
                throw new UnsupportedOperationException("remove");
            }
    }

    private static class IterableImpl implements Iterable<QueueItemAuthenticator> {
        @Override
        public Iterator<QueueItemAuthenticator> iterator() {
            return new IteratorImpl();
        }
    }
}
