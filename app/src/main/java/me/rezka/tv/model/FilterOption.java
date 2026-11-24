package me.rezka.tv.model;

/** One option from the site's "Найти лучшие" dropdowns (genre URL or year value). */
public class FilterOption {
    public final String title;
    public final String value;

    public FilterOption(String title, String value) {
        this.title = title;
        this.value = value == null ? "" : value;
    }
}
