package messages;

import model.PutEntry;

import java.util.UUID;

public class AppendEntriesMessage extends Message{
    public int term;
    public int prevLogIndex;
    public int prevLogTerm;
    public int leaderCommit;
    public PutEntry[] entries;

    public AppendEntriesMessage(String src, String dst, String leader, int term, int prevLogIndex, int prevLogTerm, int leaderCommit) {
        super(src, dst, leader, "appendEntries", UUID.randomUUID().toString().replace("-", ""));
        this.term = term;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.leaderCommit = leaderCommit;
        this.entries = new PutEntry[0];
    }

    public AppendEntriesMessage(String src, String dst, String leader, int term, int prevLogIndex, int prevLogTerm, int leaderCommit, PutEntry[] entries) {
        super(src, dst, leader, "appendEntries", UUID.randomUUID().toString().replace("-", ""));
        this.term = term;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.leaderCommit = leaderCommit;
        this.entries = entries;
    }

    public AppendEntriesMessage(String src, String dst, String leader, int term, int prevLogIndex, int prevLogTerm, int leaderCommit, PutEntry putEntry) {
        this(src, dst, leader, term, prevLogIndex, prevLogTerm, leaderCommit, new PutEntry[]{putEntry});
    }
}
