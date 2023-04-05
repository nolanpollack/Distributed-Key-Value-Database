package messages;

public class FailMessage extends Message{

    public FailMessage(String src, String dst, String leader, String MID) {
        super(src, dst, leader, "fail", MID);
    }
}
