package hudson;

import java.io.IOException;

public interface Storage {
    Object read() throws IOException;

    void write(Object o) throws IOException;
}
