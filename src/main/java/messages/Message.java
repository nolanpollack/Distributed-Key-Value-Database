package messages;

public class Message {
    public String src;
    public String dst;
    public String leader;
    public String type;
    public String MID;

    public Message(String src, String dst, String leader, String type) {
        this.src = src;
        this.dst = dst;
        this.leader = leader;
        this.type = type;
        this.MID = null;
    }
    public Message(String src, String dst, String leader, String type, String MID) {
        this.src = src;
        this.dst = dst;
        this.leader = leader;
        this.type = type;
        this.MID = MID;
    }
}
