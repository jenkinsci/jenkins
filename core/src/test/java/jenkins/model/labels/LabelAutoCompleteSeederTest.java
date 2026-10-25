/*
 *  The MIT License
 *
 *  Copyright 2010 Yahoo! Inc.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package jenkins.model.labels;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 *
 * @author dty
 */
class LabelAutoCompleteSeederTest {

    static Stream<Arguments> localParameters()
    {
        return Stream.of(
                Arguments.of("", List.of("")),
                Arguments.of("\"", List.of("")),
                Arguments.of("\"\"", List.of("")),
                Arguments.of("freebsd", List.of("freebsd")),
                Arguments.of(" freebsd", List.of("freebsd")),
                Arguments.of("freebsd ", List.of("")),
                Arguments.of("freebsd 6", List.of("6")),
                Arguments.of("\"freebsd", List.of("freebsd")),
                Arguments.of("\"freebsd ", List.of("freebsd ")),
                Arguments.of("\"freebsd\"", List.of("")),
                Arguments.of("\"freebsd\" ", List.of("")),
                Arguments.of("\"freebsd 6", List.of("freebsd 6")),
                Arguments.of("\"freebsd 6\"", List.of(""))
        );
    }

    @ParameterizedTest(name = "{index}")
    @MethodSource("localParameters")
    void testAutoCompleteSeeds(String underTest, List<String> expected) {
        LabelAutoCompleteSeeder seeder = new LabelAutoCompleteSeeder(underTest);
        assertEquals(expected, seeder.getSeeds());

    }
}
