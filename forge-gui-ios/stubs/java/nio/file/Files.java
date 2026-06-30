// forge-mac: minimal java.nio.file.Files backed by java.io.* — only the methods Forge calls on iOS.
package java.nio.file;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
public final class Files {
    private Files() {}

    public static boolean exists(Path path, LinkOption... o) { return path.toFile().exists(); }
    public static boolean notExists(Path path, LinkOption... o) { return !path.toFile().exists(); }
    public static boolean isDirectory(Path path, LinkOption... o) { return path.toFile().isDirectory(); }
    public static boolean isRegularFile(Path path, LinkOption... o) { return path.toFile().isFile(); }
    public static long size(Path path) throws IOException { return path.toFile().length(); }

    public static InputStream newInputStream(Path path, OpenOption... o) throws IOException {
        return new FileInputStream(path.toFile());
    }
    public static OutputStream newOutputStream(Path path, OpenOption... opts) throws IOException {
        boolean append = false;
        for (OpenOption op : opts) if (op == StandardOpenOption.APPEND) append = true;
        File f = path.toFile();
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        return new FileOutputStream(f, append);
    }
    public static BufferedReader newBufferedReader(Path path) throws IOException {
        return newBufferedReader(path, StandardCharsets.UTF_8);
    }
    public static BufferedReader newBufferedReader(Path path, Charset cs) throws IOException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(path.toFile()), cs));
    }
    public static BufferedWriter newBufferedWriter(Path path, OpenOption... o) throws IOException {
        return newBufferedWriter(path, StandardCharsets.UTF_8, o);
    }
    public static BufferedWriter newBufferedWriter(Path path, Charset cs, OpenOption... o) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(newOutputStream(path, o), cs));
    }
    public static Path createDirectories(Path dir, FileAttribute<?>... a) throws IOException {
        File f = dir.toFile();
        if (!f.exists() && !f.mkdirs() && !f.isDirectory()) throw new IOException("Failed to create directories: " + f);
        return dir;
    }
    public static Path createDirectory(Path dir, FileAttribute<?>... a) throws IOException {
        File f = dir.toFile();
        if (f.exists()) throw new FileAlreadyExistsException(f.getPath());
        if (!f.mkdir()) throw new IOException("Failed to create directory: " + f);
        return dir;
    }
    public static Path move(Path src, Path target, CopyOption... opts) throws IOException {
        File s = src.toFile(), t = target.toFile();
        boolean replace = false;
        for (CopyOption o : opts) if (o == StandardCopyOption.REPLACE_EXISTING) replace = true;
        if (t.exists()) { if (replace) t.delete(); else throw new FileAlreadyExistsException(t.getPath()); }
        if (!s.renameTo(t)) { copy(src, target, opts); s.delete(); }
        return target;
    }
    public static long copy(Path src, Path target, CopyOption... opts) throws IOException {
        boolean replace = false;
        for (CopyOption o : opts) if (o == StandardCopyOption.REPLACE_EXISTING) replace = true;
        File sf = src.toFile(), tf = target.toFile();
        if (sf.isDirectory()) { tf.mkdirs(); return 0L; }
        if (tf.exists() && !replace) throw new FileAlreadyExistsException(tf.getPath());
        File parent = tf.getParentFile();
        if (parent != null) parent.mkdirs();
        try (InputStream in = new FileInputStream(sf); OutputStream out = new FileOutputStream(tf)) {
            byte[] buf = new byte[8192]; long total = 0; int r;
            while ((r = in.read(buf)) > 0) { out.write(buf, 0, r); total += r; }
            return total;
        }
    }
    public static Path createTempDirectory(String prefix, FileAttribute<?>... a) throws IOException {
        File base = new File(System.getProperty("java.io.tmpdir", "/tmp"));
        for (int i = 0; i < 1000; i++) {
            File d = new File(base, (prefix == null ? "" : prefix) + System.nanoTime());
            if (d.mkdirs()) return Paths.get(d.getPath());
        }
        throw new IOException("Failed to create temp directory");
    }
    public static Stream<Path> walk(Path start, FileVisitOption... o) throws IOException {
        List<Path> out = new ArrayList<>();
        collect(start.toFile(), out);
        return out.stream();
    }
    private static void collect(File f, List<Path> out) {
        out.add(Paths.get(f.getPath()));
        if (f.isDirectory()) { File[] kids = f.listFiles(); if (kids != null) for (File k : kids) collect(k, out); }
    }
    public static Path walkFileTree(Path start, FileVisitor<? super Path> visitor) throws IOException {
        visit(start.toFile(), visitor);
        return start;
    }
    private static final BasicFileAttributes ATTRS = new BasicFileAttributes() {};
    private static FileVisitResult visit(File f, FileVisitor<? super Path> v) throws IOException {
        Path p = Paths.get(f.getPath());
        if (f.isDirectory()) {
            FileVisitResult r = v.preVisitDirectory(p, ATTRS);
            if (r == FileVisitResult.TERMINATE) return r;
            if (r == FileVisitResult.SKIP_SUBTREE) return FileVisitResult.CONTINUE;
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) if (visit(k, v) == FileVisitResult.TERMINATE) return FileVisitResult.TERMINATE;
            return v.postVisitDirectory(p, null);
        }
        return v.visitFile(p, ATTRS);
    }
}
