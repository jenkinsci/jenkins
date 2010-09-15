package hudson.remoting;

/**
 * @author Kohsuke Kawaguchi
 */
abstract class PipeWindow {
    abstract void increase(int delta);

    abstract int peek();

    /**
     * Blocks until some space becomes available.
     */
    abstract int get() throws InterruptedException;

    abstract void decrease(int delta);

    /**
     * Fake implementation used when the receiver side doesn't support throttling.
     */
    PipeWindow FAKE = new PipeWindow() {
        void increase(int delta) {
        }

        int peek() {
            return Integer.MAX_VALUE;
        }

        int get() throws InterruptedException {
            return Integer.MAX_VALUE;
        }

        void decrease(int delta) {
        }
    };


    static class Real extends PipeWindow {
        private int available;

        public synchronized void increase(int delta) {
            available += delta;
            notifyAll();
        }

        public synchronized int peek() {
            return available;
        }

        /**
         * Blocks until some space becomes available.
         */
        public synchronized int get() throws InterruptedException {
            while (available==0)
                wait();
            return available;
        }

        public synchronized void decrease(int delta) {
            available -= delta;
            if (available<0)
                throw new AssertionError();
        }
    }
}
