package de.dittnet.unsplashDownloader.model;

public class TagStats {
    private String tagName;
    private long count;
    
    public TagStats(String tagName, long count) {
        this.tagName = tagName;
        this.count = count;
    }
    
    public String getTagName() {
        return tagName;
    }
    
    public void setTagName(String tagName) {
        this.tagName = tagName;
    }
    
    public long getCount() {
        return count;
    }
    
    public void setCount(long count) {
        this.count = count;
    }
    
    public String getFormattedCount() {
        if (count == 1) {
            return count + " photo";
        } else {
            return count + " photos";
        }
    }
}