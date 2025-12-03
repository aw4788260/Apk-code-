package com.example.secureapp;

public class ContentItem {
    public static final int TYPE_VIDEO = 0;
    public static final int TYPE_PDF = 1;

    int type;
    int id;
    String title;
    int sortOrder;
    String extraData; // للفيديو: youtubeId

    public ContentItem(int type, int id, String title, int sortOrder, String extraData) {
        this.type = type;
        this.id = id;
        this.title = title;
        this.sortOrder = sortOrder;
        this.extraData = extraData;
    }
}
