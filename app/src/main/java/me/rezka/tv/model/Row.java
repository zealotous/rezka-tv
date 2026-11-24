package me.rezka.tv.model;

public class Row {
    public final String title;
    public final java.util.List<CardItem> items;

    public Row(String title, java.util.List<CardItem> items) {
        this.title = title;
        this.items = items;
    }
}
