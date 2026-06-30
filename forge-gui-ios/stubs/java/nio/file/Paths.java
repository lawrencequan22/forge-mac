package java.nio.file;
import java.io.File;
public final class Paths {
    private Paths() {}
    public static Path get(String first, String... more) {
        File f = new File(first);
        for (String m : more) if (m != null && !m.isEmpty()) f = new File(f, m);
        return new Path(f);
    }
}
