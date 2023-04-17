package messages;

public class AppendEntriesResponseMessage extends Message{
    public int term;
    public boolean success;
//    public int lastLogIndex;

    public AppendEntriesResponseMessage(String src, String dst, String leader, int term, boolean success, String MID) {
        super(src, dst, leader, "appendEntriesResponse", MID);
        this.term = term;
        this.success = success;
//        this.lastLogIndex = lastLogIndex;
    }
}
