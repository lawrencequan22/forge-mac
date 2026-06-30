// forge-mac: minimal java.nio.file.Path backed by java.io.File (MobiVM's iOS runtime has no NIO.2).
package java.nio.file;
import java.io.File;
public final class Path implements Comparable<Path> {
    private final File file;
    Path(File f) { this.file = f; }
    public File toFile() { return file; }
    public Path resolve(Path other) {
        return other.file.isAbsolute() ? other : new Path(new File(file, other.file.getPath()));
    }
    public Path resolve(String other) { return resolve(new Path(new File(other))); }
    public Path getParent() { File p = file.getParentFile(); return p == null ? null : new Path(p); }
    public Path getFileName() { String n = file.getName(); return n.isEmpty() ? null : new Path(new File(n)); }
    public Path toAbsolutePath() { return new Path(file.getAbsoluteFile()); }
    public Path normalize() { return this; }
    public Path relativize(Path other) {
        String base = file.getAbsolutePath(), tgt = other.file.getAbsolutePath();
        if (tgt.equals(base)) return new Path(new File(""));
        String basePref = base.endsWith(File.separator) ? base : base + File.separator;
        if (tgt.startsWith(basePref)) return new Path(new File(tgt.substring(basePref.length())));
        return other;
    }
    public boolean startsWith(Path other) { return file.getPath().startsWith(other.file.getPath()); }
    public boolean startsWith(String other) { return file.getPath().startsWith(other); }
    public boolean endsWith(Path other) { return file.getPath().endsWith(other.file.getPath()); }
    public boolean endsWith(String other) { return file.getPath().endsWith(other); }
    public boolean isAbsolute() { return file.isAbsolute(); }
    @Override public String toString() { return file.getPath(); }
    @Override public boolean equals(Object o) { return o instanceof Path && ((Path) o).file.equals(file); }
    @Override public int hashCode() { return file.hashCode(); }
    @Override public int compareTo(Path o) { return file.compareTo(o.file); }
}
