package me.rezka.tv.model;

/** A top-level catalogue section (Фильмы, Сериалы, …). */
public class Category {
    public final String title;
    public final String url;
    /** Collections index: its items are collection pages, not titles. */
    public final boolean collections;
    /** Non-zero: the site's find-best block id (1/2/3/82) for genre filters. */
    public final int bestBlockId;

    public Category(String title, String url, boolean collections, int bestBlockId) {
        this.title = title;
        this.url = url;
        this.collections = collections;
        this.bestBlockId = bestBlockId;
    }
}
