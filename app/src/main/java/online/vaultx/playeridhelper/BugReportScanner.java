package online.vaultx.playeridhelper;

import android.content.ContentResolver;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Processes a user-selected Android bug report entirely offline.
 * Does not store/forward the report, raw logs or authentication credentials.
 */
public final class BugReportScanner {
    private static final long MAX_UNCOMPRESSED_BYTES = 180L * 1024 * 1024;
    private static final int MAX_LINES = 2_000_000;
    private static final int MAX_ZIP_ENTRIES = 1000;

    public static final class Result {
        public final String candidate;
        public final String message;

        Result(String candidate, String message) {
            this.candidate = candidate;
            this.message = message;
        }
    }

    private static final class Limit {
        long chars;
        int lines;
        boolean exceeded;
    }

    private BugReportScanner() {}

    private static String scanLines(BufferedReader reader, Limit limit) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            limit.lines++;
            limit.chars += line.length() + 1;
            if (limit.chars > MAX_UNCOMPRESSED_BYTES || limit.lines > MAX_LINES) {
                limit.exceeded = true;
                return null;
            }
            // Only explicit Player ID labels, on possible game/Unity lines, are accepted.
            String candidate = GameLogParser.findCandidate(line, 0);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private static boolean logTextEntry(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return !n.contains("__macosx") &&
                (n.endsWith(".txt") || n.endsWith(".log") || n.endsWith(".logcat"));
    }

    public static Result read(ContentResolver resolver, Uri uri) {
        if (resolver == null || uri == null)
            return new Result(null, "Не выбран отчёт Android");
        try (InputStream source = resolver.openInputStream(uri)) {
            if (source == null) return new Result(null, "Не удалось открыть файл");
            BufferedInputStream stream = new BufferedInputStream(source, 8192);
            stream.mark(8);
            byte[] header = new byte[4];
            int read = stream.read(header);
            stream.reset();
            boolean isZip = read >= 4 && header[0] == 'P' && header[1] == 'K';
            Limit limit = new Limit();
            String candidate = null;

            if (isZip) {
                try (ZipInputStream zip = new ZipInputStream(stream, StandardCharsets.UTF_8)) {
                    ZipEntry entry;
                    int entries = 0;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (++entries > MAX_ZIP_ENTRIES) break;
                        if (entry.isDirectory() || !logTextEntry(entry.getName())) {
                            zip.closeEntry();
                            continue;
                        }
                        if (entry.getSize() > MAX_UNCOMPRESSED_BYTES) {
                            zip.closeEntry();
                            continue;
                        }
                        // The reader must not be closed here: it wraps the shared ZipInputStream.
                        BufferedReader lines =
                                new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8));
                        candidate = scanLines(lines, limit);
                        if (candidate != null || limit.exceeded) break;
                        zip.closeEntry();
                    }
                }
            } else {
                candidate = scanLines(
                        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)),
                        limit);
            }
            if (candidate != null)
                return new Result(candidate, "В журнале найден возможный Player ID");
            if (limit.exceeded)
                return new Result(null, "Отчёт слишком большой для безопасного анализа");
            return new Result(null,
                    "Player ID не найден в отчёте. Возможно, игра не записывает его в logcat.");
        } catch (IOException | SecurityException e) {
            return new Result(null, "Не удалось прочитать отчёт. Выбери ZIP или TXT отчёт Android.");
        }
    }
}
