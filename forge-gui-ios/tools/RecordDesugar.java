// forge-mac: Bytecode "de-record" desugarer for the MobiVM/RoboVM iOS build.
//
// MobiVM 2.3.23's runtime is Java-8 level: it has no java.lang.Record and no
// java.lang.runtime.ObjectMethods, so Java records fail to compile/link. This tool rewrites
// every record class on the iOS classpath into a plain final class:
//   - superclass java/lang/Record -> java/lang/Object (and fix the <init> super call)
//   - drop the Record attribute / ACC_RECORD flag and record components
//   - regenerate value-based equals(Object), hashCode(), toString() from the components
//     (the originals use invokedynamic -> ObjectMethods, which MobiVM lacks)
// It also strips PermittedSubclasses (sealed) attributes, which MobiVM likewise can't read.
//
// Source is NOT touched — this runs over compiled .class files / jars, so the fork stays
// upstream-mergeable. Usage:  java RecordDesugar <path-to-dir-or-jar> [more...]
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.commons.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

public class RecordDesugar {
    static int scanned = 0, records = 0, sealedStripped = 0;

    public static void main(String[] args) throws Exception {
        for (String p : args) {
            Path path = Paths.get(p);
            if (!Files.exists(path)) { System.out.println("  (skip, absent) " + p); continue; }
            if (Files.isDirectory(path)) processDir(path);
            else if (p.endsWith(".jar")) processJar(path);
        }
        System.out.println("RecordDesugar: scanned " + scanned + " classes, de-recorded " + records
                + ", de-sealed " + sealedStripped);
    }

    static void processDir(Path dir) throws IOException {
        List<Path> classes = new ArrayList<>(), jars = new ArrayList<>();
        Files.walk(dir).forEach(f -> {
            String s = f.toString();
            if (s.endsWith(".class")) classes.add(f);
            else if (s.endsWith(".jar") && !s.endsWith("-sources.jar")) jars.add(f);
        });
        for (Path f : classes) {
            byte[] out = transform(Files.readAllBytes(f));
            if (out != null) Files.write(f, out);
        }
        for (Path j : jars) processJar(j);
    }

    static void processJar(Path jar) throws IOException {
        Path tmp = Files.createTempFile("rd", ".jar");
        boolean changed = false;
        try (JarFile jf = new JarFile(jar.toFile());
             JarOutputStream jos = new JarOutputStream(Files.newOutputStream(tmp))) {
            for (Enumeration<JarEntry> en = jf.entries(); en.hasMoreElements();) {
                JarEntry e = en.nextElement();
                byte[] data = jf.getInputStream(e).readAllBytes();
                if (e.getName().endsWith(".class")) {
                    byte[] out = transform(data);
                    if (out != null) { data = out; changed = true; }
                }
                JarEntry ne = new JarEntry(e.getName());
                if (e.getTime() != -1) ne.setTime(e.getTime());
                jos.putNextEntry(ne);
                jos.write(data);
                jos.closeEntry();
            }
        }
        if (changed) Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
        else Files.deleteIfExists(tmp);
    }

    static byte[] transform(byte[] in) {
        scanned++;
        ClassNode cn = new ClassNode();
        new ClassReader(in).accept(cn, 0);
        boolean isRecord = (cn.access & Opcodes.ACC_RECORD) != 0 || "java/lang/Record".equals(cn.superName);
        boolean isSealed = cn.permittedSubclasses != null && !cn.permittedSubclasses.isEmpty();
        if (!isRecord && !isSealed) return null;

        if (isSealed) { cn.permittedSubclasses = null; sealedStripped++; }
        if (!isRecord) { // sealed-only: just strip the attribute, keep everything else
            return write(cn);
        }
        records++;

        // Gather components (name + descriptor), preferring the Record attribute, else instance fields.
        List<String[]> comps = new ArrayList<>();
        if (cn.recordComponents != null && !cn.recordComponents.isEmpty()) {
            for (RecordComponentNode rc : cn.recordComponents) comps.add(new String[]{rc.name, rc.descriptor});
        } else {
            for (FieldNode f : cn.fields) if ((f.access & Opcodes.ACC_STATIC) == 0) comps.add(new String[]{f.name, f.desc});
        }

        cn.superName = "java/lang/Object";
        cn.access &= ~Opcodes.ACC_RECORD;
        cn.recordComponents = null;

        // Fix super(...) call in every constructor: java/lang/Record.<init> -> java/lang/Object.<init>
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals("<init>")) continue;
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn instanceof MethodInsnNode min && min.getOpcode() == Opcodes.INVOKESPECIAL
                        && "java/lang/Record".equals(min.owner) && "<init>".equals(min.name)) {
                    min.owner = "java/lang/Object";
                }
            }
        }

        // Replace the indy-based equals/hashCode/toString with generated value-based ones.
        cn.methods.removeIf(m ->
                (m.name.equals("equals") && m.desc.equals("(Ljava/lang/Object;)Z")) ||
                (m.name.equals("hashCode") && m.desc.equals("()I")) ||
                (m.name.equals("toString") && m.desc.equals("()Ljava/lang/String;")));
        cn.methods.add(genHashCode(cn.name, comps));
        cn.methods.add(genEquals(cn.name, comps));
        cn.methods.add(genToString(cn.name, comps));

        return write(cn);
    }

    static byte[] write(ClassNode cn) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
            @Override protected String getCommonSuperClass(String a, String b) {
                try { return super.getCommonSuperClass(a, b); }
                catch (Throwable t) { return "java/lang/Object"; } // we never merge unrelated ref types in generated code
            }
        };
        cn.accept(cw);
        return cw.toByteArray();
    }

    static final Type OBJ = Type.getType(Object.class);
    static final Type OBJECTS = Type.getType(java.util.Objects.class);

    static MethodNode genHashCode(String owner, List<String[]> comps) {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "hashCode", "()I", null, null);
        GeneratorAdapter g = new GeneratorAdapter(mn, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "hashCode", "()I");
        g.push(comps.size());
        g.newArray(OBJ);
        int i = 0;
        for (String[] c : comps) {
            g.dup();
            g.push(i++);
            g.loadThis();
            g.getField(Type.getObjectType(owner), c[0], Type.getType(c[1]));
            g.box(Type.getType(c[1]));        // primitives -> wrapper; objects unchanged
            g.arrayStore(OBJ);
        }
        g.invokeStatic(OBJECTS, new Method("hash", "([Ljava/lang/Object;)I"));
        g.returnValue();
        g.endMethod();
        return mn;
    }

    static MethodNode genEquals(String owner, List<String[]> comps) {
        String desc = "(Ljava/lang/Object;)Z";
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "equals", desc, null, null);
        GeneratorAdapter g = new GeneratorAdapter(mn, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "equals", desc);
        Type ownerT = Type.getObjectType(owner);
        // if (this == o) return true;
        Label notSame = g.newLabel();
        g.loadThis();
        g.loadArg(0);
        g.ifCmp(OBJ, GeneratorAdapter.NE, notSame);
        g.push(true); g.returnValue();
        g.mark(notSame);
        // if (!(o instanceof Owner)) return false;
        g.loadArg(0);
        g.instanceOf(ownerT);
        Label isInst = g.newLabel();
        g.ifZCmp(GeneratorAdapter.NE, isInst);
        g.push(false); g.returnValue();
        g.mark(isInst);
        // Owner other = (Owner) o;
        int other = g.newLocal(ownerT);
        g.loadArg(0); g.checkCast(ownerT); g.storeLocal(other);
        if (comps.isEmpty()) {
            g.push(true);
        } else {
            boolean first = true;
            for (String[] c : comps) {
                Type ft = Type.getType(c[1]);
                g.loadThis(); g.getField(ownerT, c[0], ft); g.box(ft);
                g.loadLocal(other); g.getField(ownerT, c[0], ft); g.box(ft);
                g.invokeStatic(OBJECTS, new Method("equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z"));
                if (!first) g.math(GeneratorAdapter.AND, Type.INT_TYPE); // bitwise & of booleans (no side effects)
                first = false;
            }
        }
        g.returnValue();
        g.endMethod();
        return mn;
    }

    static MethodNode genToString(String owner, List<String[]> comps) {
        String desc = "()Ljava/lang/String;";
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "toString", desc, null, null);
        GeneratorAdapter g = new GeneratorAdapter(mn, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "toString", desc);
        Type ownerT = Type.getObjectType(owner);
        Type sb = Type.getType(StringBuilder.class);
        String simple = owner.substring(owner.lastIndexOf('/') + 1);
        g.newInstance(sb); g.dup(); g.invokeConstructor(sb, new Method("<init>", "()V"));
        appendStr(g, sb, simple + "[");
        boolean first = true;
        for (String[] c : comps) {
            appendStr(g, sb, (first ? "" : ", ") + c[0] + "=");
            first = false;
            Type ft = Type.getType(c[1]);
            g.loadThis(); g.getField(ownerT, c[0], ft);
            g.invokeVirtual(sb, new Method("append", "(" + appendDesc(ft) + ")Ljava/lang/StringBuilder;"));
        }
        appendStr(g, sb, "]");
        g.invokeVirtual(sb, new Method("toString", "()Ljava/lang/String;"));
        g.returnValue();
        g.endMethod();
        return mn;
    }

    static void appendStr(GeneratorAdapter g, Type sb, String s) {
        g.push(s);
        g.invokeVirtual(sb, new Method("append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
    }

    // StringBuilder.append overload descriptor for a field type (widen byte/short -> int).
    static String appendDesc(Type t) {
        switch (t.getSort()) {
            case Type.BOOLEAN: return "Z";
            case Type.CHAR:    return "C";
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:     return "I";
            case Type.LONG:    return "J";
            case Type.FLOAT:   return "F";
            case Type.DOUBLE:  return "D";
            default:           return "Ljava/lang/Object;"; // objects + arrays
        }
    }
}
