package messages;

import java.util.UUID;

public class AppendEntriesMessage extends Message{
    public int term;
    public int prevLogIndex;
    public int prevLogTerm;
    public int leaderCommit;

    public AppendEntriesMessage(String src, String dst, String leader, int term, int prevLogIndex, int prevLogTerm, int leaderCommit) {
        super(src, dst, leader, "appendEntries", UUID.randomUUID().toString().replace("-", ""));
        this.term = term;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.leaderCommit = leaderCommit;
    }
}
