package hudson;

public interface FileStorage extends Storage {
    boolean exists();

    void delete();
}
