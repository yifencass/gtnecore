import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 从 shimmer jar 的 shimmer.mixins.json 移除 "ProgramMixin"：
 * 它与 oculus 的 MixinProgram 都注入 Program.compileShaderInternal，
 * dev 环境报 "The call compileShaderInternal is not cancellable" 崩溃。
 * 其余类与配置完全不动（shimmer 其他 mixin/功能保留）。
 *
 * 用法: java PatchShimmerMixins <in.jar> <out.jar>
 */
public class PatchShimmerMixins {

    public static void main(String[] args) throws IOException {
        Path in = Paths.get(args[0]);
        Path out = Paths.get(args[1]);
        int changed = 0;
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
                if (entry.getName().equals("shimmer.mixins.json")) {
                    String json = new String(bytes, StandardCharsets.UTF_8);
                    String patched = json
                            .replace("\"ProgramMixin\",", "")
                            .replace("\"ProgramMixin\"", "");
                    if (!patched.equals(json)) {
                        bytes = patched.getBytes(StandardCharsets.UTF_8);
                        changed++;
                        System.out.println("已从 shimmer.mixins.json 移除 ProgramMixin");
                    }
                }
                zos.putNextEntry(new ZipEntry(entry.getName()));
                zos.write(bytes);
                zos.closeEntry();
            }
        }
        System.out.println("完成: " + out + "（改动条目 " + changed + " 个）");
        if (changed == 0) {
            System.err.println("WARN: 未找到 shimmer.mixins.json 或 ProgramMixin，输出为原样拷贝");
        }
    }
}