package messages;

public class GetMessage extends Message {
    public String key;

    public GetMessage(String src, String dst, String leader, String MID, String key) {
        super(src, dst, leader, "get", MID);
        this.MID = MID;
        this.key = key;
    }
}
