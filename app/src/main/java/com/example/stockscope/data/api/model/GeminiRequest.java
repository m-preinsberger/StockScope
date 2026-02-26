package com.example.stockscope.data.api.model;

import java.util.Collections;
import java.util.List;

public class GeminiRequest {
    public List<Content> contents;

    public static GeminiRequest ofUserText(String text) {
        GeminiRequest r = new GeminiRequest();
        Content c = new Content();
        c.role = "user";
        Part p = new Part();
        p.text = text;
        c.parts = Collections.singletonList(p);
        r.contents = Collections.singletonList(c);
        return r;
    }

    public static class Content {
        public String role;
        public List<Part> parts;
    }

    public static class Part {
        public String text;
    }
}
