package hudson.cli;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.PrintWriter;
import java.io.StreamCorruptedException;
import java.io.StringWriter;

// TODO COPIED FROM hudson.remoting

/**
 * Signals a {@link StreamCorruptedException} with some additional diagnostic information.
 *
 * @author Kohsuke Kawaguchi
 */
class DiagnosedStreamCorruptionException extends StreamCorruptedException {
    private final Exception diagnoseFailure;
    private final byte[] readBack;
    private final byte[] readAhead;

    DiagnosedStreamCorruptionException(Exception cause, Exception diagnoseFailure, byte[] readBack, byte[] readAhead) {
        initCause(cause);
        this.diagnoseFailure = diagnoseFailure;
        this.readBack = readBack;
        this.readAhead = readAhead;
    }

    public Exception getDiagnoseFailure() {
        return diagnoseFailure;
    }

    public byte[] getReadBack() {
        return readBack;
    }

    public byte[] getReadAhead() {
        return readAhead;
    }

    @Override
    @SuppressFBWarnings(value = "INFORMATION_EXPOSURE_THROUGH_AN_ERROR_MESSAGE", justification = "Jenkins handles this issue differently or doesn't care about it")
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(super.toString()).append("\n");
        buf.append("Read back: ").append(HexDump.toHex(readBack)).append('\n');
        buf.append("Read ahead: ").append(HexDump.toHex(readAhead));
        if (diagnoseFailure != null) {
            StringWriter w = new StringWriter();
            PrintWriter p = new PrintWriter(w);
            diagnoseFailure.printStackTrace(p);
            p.flush();

            buf.append("\nDiagnosis problem:\n    ");
            buf.append(w.toString().trim().replace("\n", "\n    "));
        }
        return buf.toString();
    }
}
