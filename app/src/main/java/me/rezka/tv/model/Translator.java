package me.rezka.tv.model;

/** A voiceover/dub listed under "В русской озвучке от". */
public class Translator {
    public final String id;
    public final String name;
    public final boolean premium;
    /** Film pages link to a per-voiceover page instead of using AJAX. */
    public final String href;

    public Translator(String id, String name, boolean premium, String href) {
        this.id = id;
        this.name = name;
        this.premium = premium;
        this.href = href == null ? "" : href;
    }
}
