package me.rezka.tv.model;

public class CardItem {
    public final String url;
    public final String title;
    public final String poster;

    /** 0..100, only used by "continue watching" cards. */
    public int progressPercent;
    /** Optional small line above the title, e.g. "Серия 5 • 12:34". */
    public String subtitle = "";

    public CardItem(String url, String title, String poster) {
        this.url = url;
        this.title = title;
        this.poster = poster;
    }
}
