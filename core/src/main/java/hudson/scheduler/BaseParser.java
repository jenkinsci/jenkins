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
    // lower/uppser bounds of fields (inclusive)
    static final int[] LOWER_BOUNDS = new int[] {0,0,1,1,0};
    static final int[] UPPER_BOUNDS = new int[] {59,23,31,12,7};

    /**
     * Used to pick a value from within the range
     */
    protected Hash hash = Hash.zero();
    
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

    public void setHash(Hash hash) {
        if (hash==null)     hash = Hash.zero();
        this.hash = hash;
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

    /**
     * Uses {@link Hash} to choose a random (but stable) value from within this field.
     *
     * @param step
     *      Increments. For example, 15 if "H/15". Or {@link #NO_STEP} to indicate
     *      the special constant for "H" without the step value.
     */
    protected long doHash(int step, int field) throws ANTLRException {
        int u = UPPER_BOUNDS[field];
        if (field==2) u = 28;   // day of month can vary depending on month, so to make life simpler, just use [1,28] that's always safe
        if (field==4) u = 6;   // Both 0 and 7 of day of week are Sunday. For better distribution, limit upper bound to 6
        return doHash(LOWER_BOUNDS[field], u, step, field);
    }

    protected long doHash(int s, int e, int step, int field) throws ANTLRException {
        rangeCheck(s, field);
        rangeCheck(e, field);
        if (step > e - s + 1) {
            error(Messages.BaseParser_OutOfRange(step, 1, e - s + 1));
            throw new AssertionError();
        } else if (step > 1) {
            long bits = 0;
            for (int i = hash.next(step) + s; i <= e; i += step) {
                bits |= 1L << i;
            }
            assert bits != 0;
            return bits;
        } else if (step <=0) {
            error(Messages.BaseParser_MustBePositive(step));
            throw new AssertionError();
        } else {
            assert step==NO_STEP;
            // step=1 (i.e. omitted) in the case of hash is actually special; means pick one value, not step by 1
            return 1L << (s+hash.next(e+1-s));
        }
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
    
    protected Hash getHashForTokens() {
        return HASH_TOKENS ? hash : Hash.zero();
    }

    /**
     * This property hashes tokens in the cron tab tokens like @daily so that they spread evenly.
     */
    public static boolean HASH_TOKENS = !"false".equals(System.getProperty(BaseParser.class.getName()+".hash"));

    /**
     * Constant that indicates no step value.
     */
    public static final int NO_STEP = 1;
}
