package model;

import messages.PutMessage;

// Represents a PUT entry in the log
public class PutEntry {
    private final String key;
    private final String value;
    private final int term;
    private final PutMessage message;


    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public int getTerm() {
        return term;
    }

    public PutMessage getMessage() {
        return message;
    }

    /**
     * Constructor for PUT
     * @param key key of the entry
     * @param value value of the entry
     * @param term term of the entry
     */
    public PutEntry(String key, String value, int term, PutMessage message) {
        this.key = key;
        this.value = value;
        this.term = term;
        this.message = message;
    }
}
