package messages;

public class PutMessage extends Message{
    public String key;
    public String value;

    public PutMessage(String src, String dst, String leader, String MID, String key, String value) {
        super(src, dst, leader, "put", MID);
        this.key = key;
        this.value = value;
    }
}
