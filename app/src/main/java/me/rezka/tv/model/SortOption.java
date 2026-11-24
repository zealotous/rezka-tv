package me.rezka.tv.model;

/** A category sort/filter tab, e.g. «Последние поступления» (?filter=last). */
public class SortOption {
    public final String title;
    public final String url;
    public final boolean active;

    public SortOption(String title, String url, boolean active) {
        this.title = title;
        this.url = url;
        this.active = active;
    }
}
