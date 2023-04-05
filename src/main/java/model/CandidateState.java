package model;

import messages.AppendEntriesMessage;
import messages.RequestVoteMessage;

public class CandidateState implements State{
    private StateData stateData;

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
