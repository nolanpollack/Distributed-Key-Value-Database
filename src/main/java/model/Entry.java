package model;

public class Entry {
    private final String key;
    private final String value;
    private final int term;

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public int getTerm() {
        return term;
    }

    public Entry(String key, String value, int term) {
        this.key = key;
        this.value = value;
        this.term = term;
    }
}
