package app.healthplus.parser.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

/**
 * Apple Health ZIP 文件读取器。
 *
 * <p>Apple Health 导出的 export.zip 包含多个文件，其中核心数据在 export.xml 中。
 * 本组件负责从 ZIP 流中定位并提取目标 XML 条目，支持两种场景：
 * <ul>
 *   <li>基于文件路径的随机访问（{@link #openHealthXml(Path)}）</li>
 *   <li>基于 InputStream 的单趟扫描（{@link #openHealthXmlSinglePass(InputStream)}）</li>
 * </ul>
 *
 * <p>目标条目的优先级：export.xml > 第一个 .xml 文件（排除 export_cda.xml）。
 * export_cda.xml 是 CDA 格式的临床文档，与健康记录的 XML 结构不同，需要排除。
 */
@Component
public class AppleHealthZipReader {

    /**
     * 从 ZIP 文件路径打开健康数据 XML 的输入流。
     *
     * <p>实现方式：先扫描一遍 ZIP 找到目标条目名，再重新打开 ZIP 定位到该条目。
     * 需要两次扫描是因为 ZipInputStream 不支持随机访问（只能顺序读取）。
     *
     * @param zipPath ZIP 文件路径
     * @return 定位到目标 XML 条目的输入流（调用方需自行关闭）
     * @throws IOException 如果 ZIP 中没有找到有效的 XML 文件
     */
    public InputStream openHealthXml(Path zipPath) throws IOException {
        try (InputStream zipStream = Files.newInputStream(zipPath)) {
            String targetName = findTargetEntryName(zipStream);
            ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath));
            return positionAtEntry(zis, targetName);
        }
    }

    /**
     * 从 ZIP 输入流中打开指定名称的条目。
     *
     * @param zipStream ZIP 文件的输入流
     * @param entryName 目标条目名称
     * @return 定位到目标条目的输入流
     * @throws IOException 如果指定条目不存在
     */
    public InputStream openEntry(InputStream zipStream, String entryName) throws IOException {
        ZipInputStream zis = new ZipInputStream(zipStream);
        return positionAtEntry(zis, entryName);
    }

    /**
     * 单趟扫描 ZIP 流，优先返回 export.xml 条目。
     *
     * <p>与 {@link #openHealthXml(Path)} 不同，此方法只扫描一次 ZIP 流，
     * 适用于流式场景（如 MinIO 下载的 InputStream，无法重复读取）。
     * 如果找到 export.xml 则立即返回（流已定位），否则返回第一个 .xml 条目的名称
     * 但不返回流（需要调用方重新打开）。
     *
     * @param zipStream ZIP 文件的输入流
     * @return 包含条目名和（可能为 null 的）输入流的结果对象
     * @throws IOException 如果 ZIP 中没有找到任何 XML 文件
     */
    public HealthXmlResult openHealthXmlSinglePass(InputStream zipStream) throws IOException {
        ZipInputStream zis = new ZipInputStream(zipStream);
        String bestSeenName = null;
        boolean positioned = false;

        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            String name = entry.getName().toLowerCase(Locale.ROOT);
            // 跳过非 XML 文件和 CDA 格式文件
            if (!name.endsWith(".xml") || name.endsWith("export_cda.xml")) {
                continue;
            }
            // 找到 export.xml，直接返回（流已定位到该条目）
            if (name.endsWith("export.xml")) {
                return new HealthXmlResult(entry.getName(), zis);
            }
            // 记录第一个 XML 条目作为备选
            if (bestSeenName == null) {
                bestSeenName = entry.getName();
            }
        }

        // 没找到 export.xml，返回备选条目名（流需要重新打开）
        if (bestSeenName == null) {
            zis.close();
            throw new IOException("No valid Apple Health XML found in ZIP stream");
        }

        zis.close();
        return new HealthXmlResult(bestSeenName, null);
    }

    /**
     * 在 ZIP 流中定位到指定名称的条目，返回该条目的输入流。
     *
     * <p>ZipInputStream 只能顺序读取，所以需要逐个遍历条目直到找到目标。
     * 返回的 InputStream 就是 ZipInputStream 本身，读取它就是读取目标条目的内容。
     */
    private InputStream positionAtEntry(ZipInputStream zis, String targetName) throws IOException {
        for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
            if (entry.getName().equals(targetName)) {
                return zis;
            }
        }
        zis.close();
        throw new IOException("Target Apple Health XML entry not found: " + targetName);
    }

    /**
     * 扫描 ZIP 流，找到目标 XML 条目的名称。
     *
     * <p>优先级：export.xml > 第一个非 CDA 的 .xml 文件。
     * 注意：此方法会消费 zipStream，调用方需要重新打开流才能再次读取 ZIP。
     */
    public String findTargetEntryName(InputStream zipStream) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            String fallback = null;
            for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".xml") || name.endsWith("export_cda.xml")) {
                    continue;
                }
                if (name.endsWith("export.xml")) {
                    return entry.getName();
                }
                if (fallback == null) {
                    fallback = entry.getName();
                }
            }
            if (fallback != null) {
                return fallback;
            }
        }
        throw new IOException("No valid Apple Health XML found in ZIP stream");
    }

    /**
     * 判断文件路径是否看起来像 ZIP 文件（基于扩展名）。
     */
    public boolean looksLikeZip(Path zipPath) {
        return Files.exists(zipPath) && zipPath.toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    /**
     * ZIP 中 XML 条目的扫描结果。
     *
     * @param entryName 条目名称
     * @param inputStream 条目内容的输入流（可能为 null，表示需要重新打开 ZIP）
     */
    public record HealthXmlResult(String entryName, InputStream inputStream) {}
}
