package antlr;

public class SemanticException extends ANTLRException {
    public int line = -1;
    public int column = -1;

    /**
     * RecognitionException constructor comment.
     * @param s java.lang.String
     */
    public SemanticException(String s, int line, int column) {
        super(s);
        this.line = line;
        this.column = column + 1;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getFormatString(int line, int column) {
        StringBuffer buf = new StringBuffer();

        if (line != -1) {
            buf.append("line ");
            buf.append(line);

            if (column != -1)
                buf.append(":" + column);
            buf.append(":");
        }

        buf.append(" ");
        return buf.toString();
    }

    public String toString() {
        return getFormatString(line, column) + getMessage();
    }
}
