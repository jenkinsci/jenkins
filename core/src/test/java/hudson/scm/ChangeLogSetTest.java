/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc.
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

package hudson.scm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.User;
import java.util.Collection;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

class ChangeLogSetTest {

    @Test
    @Issue("JENKINS-49202")
    void getCommentDefaultsToMsg() {
        ChangeLogSet.Entry entry = new TestEntry("First line", null);
        assertEquals("First line", entry.getMsg());
        assertEquals("First line", entry.getComment());
    }

    @Test
    @Issue("JENKINS-49202")
    void getCommentCanBeOverridden() {
        ChangeLogSet.Entry entry = new TestEntry("First line", "First line\n\nFull commit details");
        assertEquals("First line", entry.getMsg());
        assertEquals("First line\n\nFull commit details", entry.getComment());
    }

    private static final class TestEntry extends ChangeLogSet.Entry {
        private final String msg;
        private final String comment;

        TestEntry(String msg, String comment) {
            this.msg = msg;
            this.comment = comment;
        }

        @Override
        public String getMsg() {
            return msg;
        }

        @Override
        public String getComment() {
            return comment != null ? comment : super.getComment();
        }

        @Override
        public User getAuthor() {
            return User.getUnknown();
        }

        @Override
        public Collection<String> getAffectedPaths() {
            return Collections.emptyList();
        }
    }
}
