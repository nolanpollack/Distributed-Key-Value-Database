package model;

import messages.AppendEntriesMessage;
import messages.RequestVoteMessage;

public class FollowerState implements State{
    @Override
    public void handleAppendEntries(AppendEntriesMessage message) {

    }

    @Override
    public void handleRequestVote(RequestVoteMessage message) {

    }

    @Override
    public void handleTimeout() {

    }
}
