package net.mca.server.world.data.politics;

public record PolicyVector(double nationalist, double communist, double authoritarian, double libertarian) {
    public double dot(PoliticalCompass compass) {
        return nationalist * compass.nationalist()
                + communist * compass.communist()
                + authoritarian * compass.authoritarian()
                + libertarian * compass.libertarian();
    }
}
