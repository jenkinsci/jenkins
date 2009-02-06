/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.scheduler;

import antlr.ANTLRException;
import antlr.LLkParser;
import antlr.ParserSharedInputState;
import antlr.SemanticException;
import antlr.Token;
import antlr.TokenBuffer;
import antlr.TokenStream;
import antlr.TokenStreamException;

/**
 * @author Kohsuke Kawaguchi
 */
abstract class BaseParser extends LLkParser {
    private static final int[] LOWER_BOUNDS = new int[] {0,0,1,0,0};
    private static final int[] UPPER_BOUNDS = new int[] {59,23,31,12,7};

    protected BaseParser(int i) {
        super(i);
    }

    protected BaseParser(ParserSharedInputState parserSharedInputState, int i) {
        super(parserSharedInputState, i);
    }

    protected BaseParser(TokenBuffer tokenBuffer, int i) {
        super(tokenBuffer, i);
    }

    protected BaseParser(TokenStream tokenStream, int i) {
        super(tokenStream, i);
    }

    protected long doRange(int start, int end, int step, int field) throws ANTLRException {
        rangeCheck(start, field);
        rangeCheck(end, field);
        if (step <= 0)
            error(Messages.BaseParser_MustBePositive(step));
        if (start>end)
            error(Messages.BaseParser_StartEndReversed(end,start));

        long bits = 0;
        for (int i = start; i <= end; i += step) {
            bits |= 1L << i;
        }
        return bits;
    }

    protected long doRange( int step, int field ) throws ANTLRException {
        return doRange( LOWER_BOUNDS[field], UPPER_BOUNDS[field], step, field );
    }

    protected void rangeCheck(int value, int field) throws ANTLRException {
        if( value<LOWER_BOUNDS[field] || UPPER_BOUNDS[field]<value ) {
            error(Messages.BaseParser_OutOfRange(value,LOWER_BOUNDS[field],UPPER_BOUNDS[field]));
        }
    }

    private void error(String msg) throws TokenStreamException, SemanticException {
        Token token = LT(0);
        throw new SemanticException(
            msg,
            token.getFilename(),
            token.getLine(),
            token.getColumn()
        );
    }
}
