package model;

import java.util.List;

public class StateData {
    private int currentTerm;
    private String votedFor;
    private List<Entry> log;
    private int commitIndex;
    private int lastApplied;
}
