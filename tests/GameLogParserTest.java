package online.vaultx.playeridhelper;

public final class GameLogParserTest {
    private static void expect(String actual, String expected) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected=" + expected + " actual=" + actual);
        }
    }
    public static void main(String[] args) {
        expect(GameLogParser.findCandidate(
            "11-17 12:12:12.000  1234  1234 I Unity: Player ID: EfezGame42", 1234), "EfezGame42");
        expect(GameLogParser.findCandidate(
            "11-17 12:12:12.000  1234  1234 I Unity: {\"playerId\":\"My_Player_2026\"}", 1234), "My_Player_2026");
        expect(GameLogParser.findCandidate(
            "11-17 12:12:12.000  9999  9999 I Unity: Player ID: OtherPlayer", 1234), null);
        expect(GameLogParser.findCandidate(
            "11-17 12:12:12.000  1234  1234 I Ads: Player ID: AdAccount", 0), null);
        expect(GameLogParser.findCandidate(
            "11-17 12:12:12.000  1234  1234 I Unity: Player ID: null", 1234), null);
        expect(GameLogParser.findCandidate(
            "11-17 12:12:12.000  1234  1234 I Unity: Auth token: secret", 1234), null);
        System.out.println("GameLogParserTest OK");
    }
}
