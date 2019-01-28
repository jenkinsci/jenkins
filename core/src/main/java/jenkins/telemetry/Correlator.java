/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
package jenkins.telemetry;

import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.UUID;

/**
 * This class stores a UUID identifying this instance for telemetry reporting to allow deduplication or merging of submitted records.
 *
 * We're not using anything derived from instance identity so we cannot connect an instance's public appearance with its submissions.
 *
 * This really only uses Descriptor/Describable to get a Saveable implementation for free.
 */
@Extension
@Restricted(NoExternalUse.class)
public class Correlator extends Descriptor<Correlator> implements Describable<Correlator> {
    private String correlationId;

    public Correlator() {
        super(Correlator.class);
        load();
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
            save();
        }
    }

    public String getCorrelationId() {
        return correlationId;
    }

    @Restricted(NoExternalUse.class)
    @VisibleForTesting
    void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    @Override
    public Descriptor<Correlator> getDescriptor() {
        return this;
    }
}