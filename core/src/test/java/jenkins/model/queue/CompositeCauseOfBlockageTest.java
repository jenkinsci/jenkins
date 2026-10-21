/*
 * The MIT License
 *
 * Copyright 2025 Steve Armstrong
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

package jenkins.model.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.queue.CauseOfBlockage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositeCauseOfBlockageTest {

    @Test
    void getShortDescriptionWithFewReasons() {
        List<CauseOfBlockage> reasons = new ArrayList<>();
        reasons.add(createCause("Reason 1"));
        reasons.add(createCause("Reason 2"));
        reasons.add(createCause("Reason 3"));

        CompositeCauseOfBlockage composite = new CompositeCauseOfBlockage(reasons);
        String description = composite.getShortDescription();

        assertEquals("Reason 1; Reason 2; Reason 3", description);
    }

    @Test
    void getShortDescriptionWithExactlyMaxReasons() {
        List<CauseOfBlockage> reasons = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            reasons.add(createCause("Reason " + i));
        }

        CompositeCauseOfBlockage composite = new CompositeCauseOfBlockage(reasons);
        String description = composite.getShortDescription();

        assertEquals("Reason 1; Reason 2; Reason 3; Reason 4; Reason 5", description);
    }

    @Test
    void getShortDescriptionTruncatesLongList() {
        List<CauseOfBlockage> reasons = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            // Use zero-padded numbers to ensure correct alphabetical sorting in TreeMap
            reasons.add(createCause(String.format("Reason %02d", i)));
        }

        CompositeCauseOfBlockage composite = new CompositeCauseOfBlockage(reasons);
        String description = composite.getShortDescription();

        assertTrue(description.contains("... and 5 more"));
        assertTrue(description.startsWith("Reason 01; Reason 02; Reason 03; Reason 04; Reason 05"));
    }

    @Test
    void getShortDescriptionWithManyReasons() {
        List<CauseOfBlockage> reasons = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            // Use zero-padded numbers to ensure correct alphabetical sorting in TreeMap
            reasons.add(createCause(String.format("Node %03d doesn't have label", i)));
        }

        CompositeCauseOfBlockage composite = new CompositeCauseOfBlockage(reasons);
        String description = composite.getShortDescription();

        assertTrue(description.contains("... and 95 more"));
        // Should not be extremely long
        assertTrue(description.length() < 500);
    }

    private CauseOfBlockage createCause(String message) {
        return new CauseOfBlockage() {
            @Override
            public String getShortDescription() {
                return message;
            }
        };
    }
}
