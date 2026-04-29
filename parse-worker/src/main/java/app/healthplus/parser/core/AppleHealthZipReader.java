package app.healthplus.parser.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

@Component
public class AppleHealthZipReader {

    public InputStream openHealthXml(Path zipPath) throws IOException {
        try (InputStream zipStream = Files.newInputStream(zipPath)) {
            String targetName = findTargetEntryName(zipStream);
            ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath));
            return positionAtEntry(zis, targetName);
        }
    }

    public InputStream openEntry(InputStream zipStream, String entryName) throws IOException {
        ZipInputStream zis = new ZipInputStream(zipStream);
        return positionAtEntry(zis, entryName);
    }

    public HealthXmlResult openHealthXmlSinglePass(InputStream zipStream) throws IOException {
        ZipInputStream zis = new ZipInputStream(zipStream);
        String bestSeenName = null;
        boolean positioned = false;

        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            String name = entry.getName().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".xml") || name.endsWith("export_cda.xml")) {
                continue;
            }
            if (name.endsWith("export.xml")) {
                return new HealthXmlResult(entry.getName(), zis);
            }
            if (bestSeenName == null) {
                bestSeenName = entry.getName();
            }
        }

        if (bestSeenName == null) {
            zis.close();
            throw new IOException("No valid Apple Health XML found in ZIP stream");
        }

        zis.close();
        return new HealthXmlResult(bestSeenName, null);
    }

    private InputStream positionAtEntry(ZipInputStream zis, String targetName) throws IOException {
        for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
            if (entry.getName().equals(targetName)) {
                return zis;
            }
        }
        zis.close();
        throw new IOException("Target Apple Health XML entry not found: " + targetName);
    }

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

    public boolean looksLikeZip(Path zipPath) {
        return Files.exists(zipPath) && zipPath.toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    public record HealthXmlResult(String entryName, InputStream inputStream) {}
}
