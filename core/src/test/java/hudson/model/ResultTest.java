/*
 * The MIT License
 *
 * Copyright (c) 2026, Jenkins contributors
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

package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

class ResultTest {

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "UNSTABLE", "FAILURE", "NOT_BUILT", "ABORTED"})
    void optionHandlerAcceptsValidResults(String status) throws CmdLineException {
        List<Result> values = new ArrayList<>();
        parse(status, values::add);

        assertEquals(List.of(Result.fromString(status)), values);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "FAIL-URE"})
    void optionHandlerRejectsInvalidResults(String status) {
        List<Result> values = new ArrayList<>();

        CmdLineException exception = assertThrows(CmdLineException.class, () -> parse(status, values::add));

        assertTrue(exception.getMessage().startsWith("No such status '" + status + "'. Did you mean "));
        assertEquals(List.of(), values);
    }

    private static void parse(String status, Setter<Result> setter) throws CmdLineException {
        Result.OptionHandlerImpl handler = new Result.OptionHandlerImpl(null, null, setter);
        handler.parseArguments(new Parameters() {
            @Override
            public String getParameter(int idx) {
                return status;
            }

            @Override
            public int size() {
                return 1;
            }
        });
    }
}
