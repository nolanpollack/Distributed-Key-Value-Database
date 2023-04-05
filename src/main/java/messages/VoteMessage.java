package messages;

public class VoteMessage extends Message{
    public int term;
    public boolean voteGranted;

    public VoteMessage(String src, String dst, String leader, String MID, int term, boolean voteGranted) {
        super(src, dst, leader, "vote", MID);
        this.term = term;
        this.voteGranted = voteGranted;
    }
}
