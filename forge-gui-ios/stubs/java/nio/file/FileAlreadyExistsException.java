package java.nio.file;
import java.io.IOException;
public class FileAlreadyExistsException extends IOException {
    public FileAlreadyExistsException(String file) { super(file); }
}
