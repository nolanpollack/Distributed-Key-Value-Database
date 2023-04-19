package messages;

public class AppendEntriesResponseMessage extends Message{
    public int term;
    public boolean success;
    public int index;

    public AppendEntriesResponseMessage(String src, String dst, String leader, int term, boolean success, String MID, int index) {
        super(src, dst, leader, "appendEntriesResponse", MID);
        this.term = term;
        this.success = success;
        this.index = index;
    }
}
