package messages;

public class RedirectMessage extends Message{

    public RedirectMessage(String src, String dst, String leader, String MID) {
        super(src, dst, leader, "redirect", MID);
    }
}
