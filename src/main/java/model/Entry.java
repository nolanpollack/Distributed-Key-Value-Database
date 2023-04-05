package model;

import java.util.Optional;

public class Entry {
    public enum Type {
        GET,
        PUT
    }
    private final Type type;
    private final String key;
    private final Optional<String> value;
    private final int term;

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value.orElse(null);
    }

    public int getTerm() {
        return term;
    }

    /**
     * Constructor for PUT
     * @param key
     * @param value
     * @param term
     */
    public Entry(String key, String value, int term) {
        this.type = Type.PUT;
        this.key = key;
        this.value = Optional.of(value);
        this.term = term;
    }

    public Entry (String key, int term) {
        this.type = Type.GET;
        this.key = key;
        this.value = Optional.empty();
        this.term = term;
    }
}
