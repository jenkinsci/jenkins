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

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 *
 * @author dty
 */
public class LabelAutoCompleteSeederTest {

    static Stream<Arguments> localParameters()
    {
        return Stream.of(
                Arguments.of("", Collections.singletonList("")),
                Arguments.of("\"", Collections.singletonList("")),
                Arguments.of("\"\"", Collections.singletonList("")),
                Arguments.of("freebsd", Collections.singletonList("freebsd")),
                Arguments.of(" freebsd", Collections.singletonList("freebsd")),
                Arguments.of("freebsd ", Collections.singletonList("")),
                Arguments.of("freebsd 6", Collections.singletonList("6")),
                Arguments.of("\"freebsd", Collections.singletonList("freebsd")),
                Arguments.of("\"freebsd ", Collections.singletonList("freebsd ")),
                Arguments.of("\"freebsd\"", Collections.singletonList("")),
                Arguments.of("\"freebsd\" ", Collections.singletonList("")),
                Arguments.of("\"freebsd 6", Collections.singletonList("freebsd 6")),
                Arguments.of("\"freebsd 6\"", Collections.singletonList(""))
        );
    }

    @ParameterizedTest( name = "{index}" )
    @MethodSource( "localParameters" )
    public void testAutoCompleteSeeds(String underTest, List<String> expected) {
        LabelAutoCompleteSeeder seeder = new LabelAutoCompleteSeeder(underTest);
        assertEquals(expected, seeder.getSeeds());

    }
}