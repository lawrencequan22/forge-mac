// forge-mac: rt-patch java.lang.Enum.getSharedConstants(Class)[Enum to consult
// java.lang.EnumConstantsRegistry BEFORE its reflective values() path. For enums the launcher has
// registered (the oversized ones MobiVM can't reflect), this returns the pre-computed array; for all
// other enums it falls through to the stock reflective path unchanged. Same idea as FilePatcher.
//
//   T[] getSharedConstants(Class<T> t) {
//       Object[] r = EnumConstantsRegistry.get(t);   // injected
//       if (r != null) return (Enum[]) r;            // injected
//       ... original reflective body ...
//   }
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;

public class EnumPatcher {
    public static void main(String[] a) throws Exception {
        byte[] in = Files.readAllBytes(Paths.get(a[0]));
        ClassNode cn = new ClassNode();
        new ClassReader(in).accept(cn, 0);
        boolean patched = false;
        for (MethodNode m : cn.methods) {
            if (m.name.equals("getSharedConstants") && m.desc.equals("(Ljava/lang/Class;)[Ljava/lang/Enum;")) {
                InsnList pre = new InsnList();
                LabelNode notRegistered = new LabelNode();
                pre.add(new VarInsnNode(Opcodes.ALOAD, 0));                                   // enumType
                pre.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "forge/rt/EnumRegistry",
                        "get", "(Ljava/lang/Class;)[Ljava/lang/Object;", false));             // -> Object[]
                pre.add(new InsnNode(Opcodes.DUP));
                pre.add(new JumpInsnNode(Opcodes.IFNULL, notRegistered));
                pre.add(new TypeInsnNode(Opcodes.CHECKCAST, "[Ljava/lang/Enum;"));            // erased T[] return type
                pre.add(new InsnNode(Opcodes.ARETURN));
                pre.add(notRegistered);
                pre.add(new InsnNode(Opcodes.POP));                                           // discard the null
                m.instructions.insert(pre);
                patched = true;
            }
        }
        if (!patched) throw new IllegalStateException("getSharedConstants(Class)[Enum not found in " + a[0]);
        // COMPUTE_FRAMES (the inserted branch needs a stack-map frame); tolerate rt-internal types we
        // can't load on the patcher classpath by falling back to Object, like RecordDesugar does.
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
            @Override protected String getCommonSuperClass(String x, String y) {
                try { return super.getCommonSuperClass(x, y); } catch (Throwable t) { return "java/lang/Object"; }
            }
        };
        cn.accept(cw);
        Files.write(Paths.get(a[1]), cw.toByteArray());
        System.out.println("patched java.lang.Enum.getSharedConstants -> " + a[1]);
    }
}
