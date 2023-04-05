package model;

import messages.AppendEntriesMessage;
import messages.RequestVoteMessage;

public interface State {
    void handleAppendEntries(AppendEntriesMessage message);
    void handleRequestVote(RequestVoteMessage message);
    void handleTimeout();
}
