package net.mca.server.world.data.politics;

public record PoliticalCompass(double nationalist, double communist, double authoritarian, double libertarian) {
    public PoliticalCompass {
        nationalist = clamp(nationalist);
        communist = clamp(communist);
        authoritarian = clamp(authoritarian);
        libertarian = clamp(libertarian);
    }

    private static double clamp(double v) {
        return Math.max(0.0D, Math.min(1.0D, v));
    }
}
