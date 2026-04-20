/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

package jenkins.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.cli.FullDuplexHttpStream;
import hudson.model.RootAction;
import hudson.security.csrf.CrumbExclusion;
import hudson.util.ChunkedInputStream;
import hudson.util.ChunkedOutputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * Server-side counterpart to {@link FullDuplexHttpStream}.
 * <p>
 * To use, bind this to an endpoint with {@link RootAction} (you will also need a {@link CrumbExclusion}).
 * @since 2.54
 */
public abstract class FullDuplexHttpService {

    private static final Logger LOGGER = Logger.getLogger(FullDuplexHttpService.class.getName());

    /**
     * Set to true if the servlet container doesn't support chunked encoding.
     */
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* Script Console modifiable */ boolean DIY_CHUNKING = SystemProperties.getBoolean("hudson.diyChunking");

    /**
     * Controls the time out of waiting for the 2nd HTTP request to arrive.
     */
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* Script Console modifiable */ long CONNECTION_TIMEOUT = TimeUnit.SECONDS.toMillis(15);

    protected final UUID uuid;

    private InputStream upload;

    private boolean completed;

    protected FullDuplexHttpService(UUID uuid) {
        this.uuid = uuid;
    }

    /**
     * This is where we send the data to the client.
     *
     * <p>
     * If this connection is lost, we'll abort the channel.
     */
    public synchronized void download(StaplerRequest2 req, StaplerResponse2 rsp) throws InterruptedException, IOException {
        rsp.setStatus(HttpServletResponse.SC_OK);

        // server->client channel.
        // this is created first, and this controls the lifespan of the channel
        rsp.addHeader("Transfer-Encoding", "chunked");
        OutputStream out = rsp.getOutputStream();
        if (DIY_CHUNKING) {
            out = new ChunkedOutputStream(out);
        }

        // send something out so that the client will see the HTTP headers
        out.write(0);
        out.flush();

        { // wait until we have the other channel
            long end = System.currentTimeMillis() + CONNECTION_TIMEOUT;
            while (upload == null && System.currentTimeMillis() < end) {
                LOGGER.log(Level.FINE, "Waiting for upload stream for {0}: {1}", new Object[] {uuid, this});
                wait(1000);
            }

            if (upload == null) {
                throw new IOException("HTTP full-duplex channel timeout: " + uuid);
            }

            LOGGER.log(Level.FINE, "Received upload stream {0} for {1}: {2}", new Object[] {upload, uuid, this});
        }

        try {
            run(upload, out);
        } finally {
            // publish that we are done
            completed = true;
            notify();
        }
    }

    protected abstract void run(InputStream upload, OutputStream download) throws IOException, InterruptedException;

    /**
     * This is where we receive inputs from the client.
     */
    public synchronized void upload(StaplerRequest2 req, StaplerResponse2 rsp) throws InterruptedException, IOException {
        rsp.setStatus(HttpServletResponse.SC_OK);
        InputStream in = req.getInputStream();
        if (DIY_CHUNKING) {
            in = new ChunkedInputStream(in);
        }

        // publish the upload channel
        upload = in;
        LOGGER.log(Level.FINE, "Recording upload stream {0} for {1}: {2}", new Object[] {upload, uuid, this});
        notify();

        // wait until we are done
        long end = System.currentTimeMillis() + CONNECTION_TIMEOUT;
        while (!completed && System.currentTimeMillis() < end) {
            LOGGER.log(Level.FINE, "Waiting for upload stream {0} for {1}: {2}", new Object[] {upload, uuid, this});
            wait(1000);
        }

        if (!completed) {
            LOGGER.log(Level.FINE, "Timeout reached for {0} for {1}: {2}", new Object[] {upload, uuid, this});
            throw new IOException("CLI timeout");
        }
    }

    /**
     * HTTP response that allows a client to use this service.
     */
    public abstract static class Response extends HttpResponses.HttpResponseException {

        private final Map<UUID, FullDuplexHttpService> services;

        /**
         * @param services a cross-request cache of services, to correlate the
         * upload and download connections
         */
        protected Response(Map<UUID, FullDuplexHttpService> services) {
            this.services = services;
        }

        @Override
        public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException, ServletException {
            try {
                // do not require any permission to establish a CLI connection
                // the actual authentication for the connecting Channel is done by CLICommand

                UUID uuid = UUID.fromString(req.getHeader("Session"));
                rsp.setHeader("Hudson-Duplex", "true"); // set the header so that the client would know

                if (req.getHeader("Side").equals("download")) {
                    FullDuplexHttpService service = createService(req, uuid);
                    LOGGER.log(Level.FINE, "Processing download side for {0}: {1}", new Object[] {uuid, service});
                    services.put(uuid, service);
                    try {
                        service.download(req, rsp);
                    } finally {
                        LOGGER.log(Level.FINE, "Finished download side for {0}: {1}", new Object[] {uuid, service});
                        services.remove(uuid);
                    }
                } else {
                    FullDuplexHttpService service = services.get(uuid);
                    if (service == null) {
                        throw new IOException("No download side found for " + uuid);
                    }
                    LOGGER.log(Level.FINE, "Processing upload side for {0}: {1}", new Object[] {uuid, service});
                    try {
                        service.upload(req, rsp);
                    } finally {
                        LOGGER.log(Level.FINE, "Finished upload side for {0}: {1}", new Object[] {uuid, service});
                    }
                }
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }

        protected abstract FullDuplexHttpService createService(StaplerRequest2 req, UUID uuid) throws IOException, InterruptedException;

    }

}
