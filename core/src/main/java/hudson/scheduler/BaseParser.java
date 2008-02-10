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
