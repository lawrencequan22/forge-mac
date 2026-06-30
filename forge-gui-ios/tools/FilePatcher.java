// forge-mac: add java.io.File.toPath() (absent from MobiVM's iOS runtime) by appending one method
// to MobiVM's File.class; everything else is copied byte-for-byte. Put the result FIRST on RoboVM's
// boot classpath so the app's file.toPath() calls resolve.
import org.objectweb.asm.*;
import java.io.*;
import java.nio.file.*;
public class FilePatcher {
    public static void main(String[] a) throws Exception {
        byte[] in = Files.readAllBytes(Paths.get(a[0]));     // MobiVM File.class
        ClassReader cr = new ClassReader(in);
        ClassWriter cw = new ClassWriter(0);                 // preserve original bytecode/frames
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override public void visitEnd() {
                MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "toPath", "()Ljava/nio/file/Path;", null, null);
                mv.visitCode();
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "getPath", "()Ljava/lang/String;", false);
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/nio/file/Paths", "get",
                        "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;", false);
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
                super.visitEnd();
            }
        }, 0);
        new File(a[1]).getParentFile().mkdirs();
        Files.write(Paths.get(a[1]), cw.toByteArray());
        System.out.println("patched File.class -> " + a[1]);
    }
}
