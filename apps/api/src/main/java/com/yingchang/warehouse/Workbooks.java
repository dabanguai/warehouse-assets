package com.yingchang.warehouse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static com.yingchang.warehouse.Data.*;

@Component
public class Workbooks {
    private final String python, script;
    final Path files;
    public Workbooks(@Value("${warehouse.python}") String python, @Value("${warehouse.workbook-script}") String script, @Value("${warehouse.data-dir}") String dataDir) throws Exception {
        this.python = python; this.script = Path.of(script).toAbsolutePath().toString();
        files = Path.of(dataDir).toAbsolutePath().normalize().resolve("files"); Files.createDirectories(files);
    }
    byte[] execute(String mode, String file, String kind, Object options) {
        Process proc = null;
        try {
            List<String> args = new ArrayList<>(List.of(python, "-X", "utf8", script, mode));
            if (!mode.equals("export")) args.addAll(List.of(file, kind));
            proc = new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            Process running = proc;
            CompletableFuture<byte[]> output = CompletableFuture.supplyAsync(() -> {
                try (var stream = running.getInputStream()) { return stream.readNBytes(25 * 1024 * 1024 + 1); }
                catch (Exception e) { throw new CompletionException(e); }
            });
            try (var stdin = proc.getOutputStream()) { stdin.write(json(options).getBytes(StandardCharsets.UTF_8)); }
            if (!proc.waitFor(60, TimeUnit.SECONDS)) throw new BusinessError("WORKBOOK_ERROR", "Excel处理超时，请缩小文件后重试", 422);
            byte[] bytes = output.get(5, TimeUnit.SECONDS);
            if (bytes.length > 25 * 1024 * 1024) throw new BusinessError("WORKBOOK_ERROR", "Excel处理结果过大", 422);
            if (proc.exitValue() != 0) {
                String message = "Excel读取失败，请检查文件格式及Python依赖";
                try { message = parse(new String(bytes, StandardCharsets.UTF_8)).s("error", message); } catch (IllegalStateException ignored) {}
                throw new BusinessError("WORKBOOK_ERROR", message, 422);
            }
            return bytes;
        } catch (BusinessError e) { throw e; }
        catch (Exception e) { if (e instanceof InterruptedException) Thread.currentThread().interrupt(); throw new BusinessError("WORKBOOK_ERROR", "Excel读取失败，请检查文件格式及Python依赖", 422); }
        finally { if (proc != null && proc.isAlive()) proc.destroyForcibly(); }
    }
    public Data read(String mode, Path file, String kind, Object options) { return parse(new String(execute(mode, file.toString(), kind, options), StandardCharsets.UTF_8)); }
    public byte[] export(String title, List<? extends List<?>> rows) { return execute("export", "", "", of("title", title, "rows", rows)); }
    public Path file(String storedName) {
        Path path = files.resolve(storedName).normalize();
        if (!path.getParent().equals(files)) throw new BusinessError("FILE_PATH", "文件路径无效", 422);
        return path;
    }
}
