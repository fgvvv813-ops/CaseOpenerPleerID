package online.vaultx.playeridhelper;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts only explicitly labeled Player IDs. Never stores raw log lines. */
public final class GameLogParser {
    private static final Pattern PLAYER_ID = Pattern.compile(
        "(?i)(?:^|[^a-z0-9_])[\"']?player[\\s._-]*id[\"']?\\s*[:=]\\s*[\"']?([a-z0-9_-]{4,40})"
    );
    private static final Pattern THREADTIME_PID = Pattern.compile(
        "^\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+\\s+(\\d+)\\s+"
    );

    private GameLogParser() {}

    public static String findCandidate(String logLine, int gamePid) {
        if (logLine == null || logLine.length() > 16384) return null;
        String lower = logLine.toLowerCase(Locale.ROOT);
        Matcher pidMatch = THREADTIME_PID.matcher(logLine);
        int linePid = 0;
        if (pidMatch.find()) {
            try { linePid = Integer.parseInt(pidMatch.group(1)); }
            catch (NumberFormatException ignored) { return null; }
        }
        if (gamePid > 0 && linePid > 0) {
            if (linePid != gamePid) return null;
        } else {
            // Without a known PID, only accept lines with recognizable game/Unity context.
            if (!(lower.contains("unity") || lower.contains("jakpok")
                    || lower.contains("caseopener") || lower.contains("efezgames"))) return null;
        }
        Matcher m = PLAYER_ID.matcher(logLine);
        if (!m.find()) return null;
        String candidate = m.group(1);
        String normalized = candidate.toLowerCase(Locale.ROOT);
        if (normalized.equals("null") || normalized.equals("none")
                || normalized.equals("unknown") || normalized.equals("undefined")
                || normalized.equals("false") || normalized.equals("true")
                || candidate.matches("0+")) return null;
        return candidate;
    }
}
