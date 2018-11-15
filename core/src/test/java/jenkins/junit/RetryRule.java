/*
 * The MIT License
 *
 * Copyright (c) 2018 CloudBees, Inc.
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

package jenkins.junit;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class RetryRule implements TestRule {

    @Override
    public Statement apply(Statement base, Description description) {
        Retry retryForAllMethods = description.getTestClass().getAnnotation(Retry.class);
        Retry retryMethod = description.getAnnotation(Retry.class);
        int retries = 0;
        if (retryMethod != null) {
            retries = retryMethod.value();
        } else if (retryForAllMethods != null) {
            retries = retryForAllMethods.value();
        }
        int maxEvaluationAttempts = retries + 1;
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Throwable caught = null;
                for (int i = 0; i < maxEvaluationAttempts; i++) {
                    try {
                        base.evaluate();
                        return;
                    } catch (AssumptionViolatedException e) {
                        return;
                    } catch (Throwable e) {
                        e.printStackTrace();
                        caught = e;
                    }
                }
                System.err.println(description.getDisplayName() + " retry count exhausted");
                throw caught;
            }
        };
    }
}
