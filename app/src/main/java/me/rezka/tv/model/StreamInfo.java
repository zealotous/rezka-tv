package me.rezka.tv.model;

import java.util.HashMap;
import java.util.Map;

public class StreamInfo {
    public final String url;
    public final String type; // "hls" or "dash"
    public final String poster;
    public final Map<String, String> headers;

    public StreamInfo(String url, String type, String poster, Map<String, String> headers) {
        this.url = url;
        this.type = type;
        this.poster = poster == null ? "" : poster;
        this.headers = headers == null ? new HashMap<String, String>() : headers;
    }
}
