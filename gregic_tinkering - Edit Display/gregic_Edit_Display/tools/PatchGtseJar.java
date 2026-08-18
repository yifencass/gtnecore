import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 二进制 patch gtse jar：GTValues.VA/VH/VHA 由 int[]（[I）改 long[]（[J）后，
 * 编译时按 [I 引用这些字段的类会在运行期 NoSuchFieldError。
 *
 * 转换规则（对 getstatic GTValues.(VA|VH|VHA):[I 之后紧跟的序列）：
 *   getstatic [I -> [J
 *   iaload       -> laload
 *   i2l          -> （删除，long 已就位）
 *   i2d          -> l2d
 *
 * 用法: java -cp <asm.jar> PatchGtseJar <in.jar> <out.jar>
 */
public class PatchGtseJar {

    private static final String GT_VALUES = "com/gregtechceu/gtceu/api/GTValues";

    public static void main(String[] args) throws IOException {
        Path in = Paths.get(args[0]);
        Path out = Paths.get(args[1]);
        int changed = 0;
        int failed = 0;

        try (ZipFile zip = new ZipFile(in.toFile());
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(out))) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    zos.putNextEntry(new ZipEntry(entry.getName()));
                    zos.closeEntry();
                    continue;
                }
                byte[] bytes = zip.getInputStream(entry).readAllBytes();
                if (entry.getName().endsWith(".class") && bytes.length > 0) {
                    try {
                        Result r = transform(bytes);
                        bytes = r.bytes;
                        if (r.changed) changed++;
                    } catch (Exception e) {
                        System.err.println("WARN: 转换失败 " + entry.getName() + ": " + e);
                        failed++;
                    }
                }
                zos.putNextEntry(new ZipEntry(entry.getName()));
                zos.write(bytes);
                zos.closeEntry();
            }
        }
        System.out.println("完成: " + out + "  (改动类 " + changed + " 个, 失败 " + failed + " 个)");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static Result transform(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(0);
        int[] counter = {0};
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    private int state = 0; // 0=空闲 1=刚 getstatic VA 2=已 laload 等 i2l/i2d

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        if (opcode == Opcodes.GETSTATIC && owner.equals(GT_VALUES)
                                && (name.equals("VA") || name.equals("VH") || name.equals("VHA"))
                                && descriptor.equals("[I")) {
                            super.visitFieldInsn(opcode, owner, name, "[J");
                            state = 1;
                            counter[0]++;
                        } else {
                            state = 0;
                            super.visitFieldInsn(opcode, owner, name, descriptor);
                        }
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (state == 1) {
                            if (opcode >= Opcodes.ICONST_0 && opcode <= Opcodes.ICONST_5) {
                                super.visitInsn(opcode); // 压数组索引
                                return;
                            }
                            if (opcode == Opcodes.IALOAD) {
                                super.visitInsn(Opcodes.LALOAD);
                                state = 2;
                                return;
                            }
                            throw new IllegalStateException("getstatic VA 后非索引/iaload: opcode=" + opcode);
                        }
                        if (state == 2) {
                            if (opcode == Opcodes.I2L) {
                                state = 0; // 删除，long 已就位
                                return;
                            }
                            if (opcode == Opcodes.I2D) {
                                super.visitInsn(Opcodes.L2D);
                                state = 0;
                                return;
                            }
                            throw new IllegalStateException("laload 后非 i2l/i2d: opcode=" + opcode);
                        }
                        super.visitInsn(opcode);
                    }

                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        if (state == 1) {
                            super.visitIntInsn(opcode, operand); // bipush/sipush 压索引
                            return;
                        }
                        if (state != 0) throw new IllegalStateException("VA 序列中意外 int 指令");
                        super.visitIntInsn(opcode, operand);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (state == 1 && value instanceof Integer) {
                            super.visitLdcInsn(value); // ldc 小索引
                            return;
                        }
                        if (state != 0) throw new IllegalStateException("VA 序列中意外 ldc");
                        super.visitLdcInsn(value);
                    }

                    @Override
                    public void visitVarInsn(int opcode, int varIndex) {
                        if (state != 0) throw new IllegalStateException("VA 序列中意外 var 指令");
                        super.visitVarInsn(opcode, varIndex);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        if (state != 0) throw new IllegalStateException("VA 序列中意外方法调用");
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                };
            }
        }, 0);
        return new Result(cw.toByteArray(), counter[0] > 0);
    }

    private record Result(byte[] bytes, boolean changed) {}
}
