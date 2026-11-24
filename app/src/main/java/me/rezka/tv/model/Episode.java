package me.rezka.tv.model;

public class Episode {
    public final int season;
    public final int number;
    /** Human label from the site, e.g. "20-21" for a merged episode. */
    public final String label;
    /** Post id (same for every episode of a title). */
    public final String id;

    public Episode(int season, int number, String label, String id) {
        this.season = season;
        this.number = number;
        this.label = label == null || label.isEmpty() ? String.valueOf(number) : label;
        this.id = id == null ? "" : id;
    }
}
