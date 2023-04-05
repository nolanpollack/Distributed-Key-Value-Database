package messages;

import model.Entry;

import java.util.UUID;

public class AppendEntriesMessage extends Message{
    public int term;
    public int prevLogIndex;
    public int prevLogTerm;
    public int leaderCommit;
    public Entry[] entries;

    public AppendEntriesMessage(String src, String dst, String leader, int term, int prevLogIndex, int prevLogTerm, int leaderCommit) {
        super(src, dst, leader, "appendEntries", UUID.randomUUID().toString().replace("-", ""));
        this.term = term;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.leaderCommit = leaderCommit;
        this.entries = new Entry[0];
    }

    public AppendEntriesMessage(String src, String dst, String leader, int term, int prevLogIndex, int prevLogTerm, int leaderCommit, Entry[] entries) {
        super(src, dst, leader, "appendEntries", UUID.randomUUID().toString().replace("-", ""));
        this.term = term;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.leaderCommit = leaderCommit;
        this.entries = entries;
    }

    public AppendEntriesMessage(String src, String dst, String leader, int term, int prevLogIndex, int prevLogTerm, int leaderCommit, Entry entry) {
        this(src, dst, leader, term, prevLogIndex, prevLogTerm, leaderCommit, new Entry[]{entry});
    }
}
