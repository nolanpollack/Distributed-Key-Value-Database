package messages;

import model.PutEntry;

import java.util.UUID;

public class AppendEntriesMessage extends Message{
    // Leader's term
    public int term;
    // Index of log entry immediately preceding new ones
    public int prevLogIndex;
    // Term of prevLogIndex entry
    public int prevLogTerm;
    // Index of highest log entry known to be committed
    public int leaderCommit;
    //Entries to be appended to the log
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
