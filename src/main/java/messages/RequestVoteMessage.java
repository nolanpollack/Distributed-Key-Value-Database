package messages;

import java.util.UUID;

public class RequestVoteMessage extends Message{
    public int term;
    public int lastLogIndex;
    public int lastLogTerm;

    public RequestVoteMessage(String src, String dst, String leader, int term, int lastLogIndex, int lastLogTerm) {
        super(src, dst, leader, "requestVote", UUID.randomUUID().toString().replace("-", ""));
        this.term = term;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }
}
