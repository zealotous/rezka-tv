package me.rezka.tv.model;

import java.util.ArrayList;
import java.util.List;

/** One catalogue page: cards, sort tabs and the "Найти лучшие" filters. */
public class CategoryPage {
    public final List<CardItem> cards;
    public final List<SortOption> sorts;
    public final List<FilterOption> genres;
    public final List<FilterOption> years;
    public final String bestTitle;

    public CategoryPage(List<CardItem> cards, List<SortOption> sorts,
                        List<FilterOption> genres, List<FilterOption> years,
                        String bestTitle) {
        this.cards = cards == null ? new ArrayList<CardItem>() : cards;
        this.sorts = sorts == null ? new ArrayList<SortOption>() : sorts;
        this.genres = genres == null ? new ArrayList<FilterOption>() : genres;
        this.years = years == null ? new ArrayList<FilterOption>() : years;
        this.bestTitle = bestTitle == null || bestTitle.isEmpty()
                ? "Найти лучшие" : bestTitle;
    }
}
