package messages;

public class OkMessage extends Message{
    String value;
    public OkMessage(String src, String dst, String leader, String MID) {
        super(src, dst, leader, "ok", MID);
    }

    public OkMessage(String src, String dst, String leader, String MID, String value) {
        super(src, dst, leader, "ok", MID);
        this.value = value;
    }
}
