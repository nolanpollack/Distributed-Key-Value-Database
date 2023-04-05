package model;

import messages.AppendEntriesMessage;
import messages.RequestVoteMessage;

import java.util.Map;

public class LeaderState implements State{
    private StateData stateData;
    private Map<String, Integer> nextIndex;
    private Map<String, Integer> matchIndex;

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
