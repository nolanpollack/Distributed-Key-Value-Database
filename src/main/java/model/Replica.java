package model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import messages.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.*;

public class Replica {
    enum State {
        LEADER, FOLLOWER, CANDIDATE
    }

    private final String BROADCAST = "FFFF";
    private final int TIMEOUTMIN = 150;
    private final int TIMEOUTMAX = 300;
    private final int HEARTBEAT = 100;

    private final int port;
    private final String id;
    private final String[] peers;
    private final DatagramSocket socket;
    Gson gson;
    private final Random random;

    private int currentTerm;
    private String votedFor;
    List<PutEntry> log;
    private int commitIndex = 0;
    private int lastApplied = 0;

    HashMap<String, String> kvStore;
    private String leaderId;
    private long lastHeartbeat;
    private long electionTimeout;
    private State state;
    private long lastSentHeartbeat;

    /**
     * Create a new replica.
     *
     * @param port  the port to listen on.
     * @param id    the id of the replica.
     * @param peers the ids of the other replicas.
     */
    public Replica(int port, String id, String[] peers) {
        this.port = port;
        this.id = id;
        this.peers = peers;
        this.gson = initializeGson();
        this.state = State.FOLLOWER;
        this.lastHeartbeat = System.currentTimeMillis();
        this.random = new Random();
        this.electionTimeout = random.nextInt(TIMEOUTMAX) + TIMEOUTMIN;
        this.currentTerm = 0;
        this.votedFor = null;
        this.leaderId = BROADCAST;
        this.log = new ArrayList<>();
        log.add(new PutEntry("0", "0", 0, null));
        this.kvStore = new HashMap<>();

        try {
            this.socket = new DatagramSocket();
            System.out.println("Replica " + id + " starting up ");
            Message msg = new Message(id, BROADCAST, BROADCAST, "hello");
            send(msg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Initialize the Gson object with the appropriate adapters.
     *
     * @return the Gson object.
     */
    private Gson initializeGson() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Message.class, new json.Adapters.MessageDeserializer());
        return builder.create();
    }

    /**
     * Send a message.
     *
     * @param msg the message to send.
     * @throws IOException if there is an error sending the message.
     */
    private void send(Message msg) throws IOException {
        String message = new Gson().toJson(msg);

        DatagramPacket packet = new DatagramPacket(message.getBytes(),
                message.getBytes().length,
                new InetSocketAddress("localhost", port));
        this.socket.send(packet);
//        System.out.println("Sent " + new String(packet.getData()));
    }

    /**
     * Run the replica. Delegate to the appropriate method based on the current state.
     *
     * @throws IOException if there is an error sending or receiving messages.
     */
    private void run() throws IOException {
        while (true) {
            switch (state) {
                case LEADER:
                    runLeader(new LinkedList<>());
                    break;
                case FOLLOWER:
                    runFollower(new LinkedList<>());
                    break;
                case CANDIDATE:
                    runCandidate();
                    break;
            }
        }
    }

    private void runCandidate() throws IOException {
        int numVotes = 1;
        int requiredVotes = peers.length / 2 + 1;
        Queue<Message> entryQueue = new LinkedList<>();
        while (this.state == State.CANDIDATE) {
            if (electionTimeout()) {
                startElection();
                return;
            }
            Message received = receive();
            switch (received.type) {
                case "vote":
                    numVotes += receiveVote(numVotes, requiredVotes, (VoteMessage) received);
                    break;
                case "appendEntries":
                    handleAppendEntries((AppendEntriesMessage) received);
                    break;
                case "put":
                case "get":
                    entryQueue.add(received);
                    break;
                case "requestVote":
                    handleRequestVote((RequestVoteMessage) received);
            }
        }
        if (state.equals(State.LEADER)) {
            runLeader(entryQueue);
        } else if (state.equals(State.FOLLOWER)) {
            runFollower(entryQueue);
        }
    }

    /**
     * Handle a vote message.
     *
     * @param numVotes      the number of votes received so far.
     * @param requiredVotes the number of votes required to win.
     * @param msg           the vote message.
     * @return 1 if the vote was granted, 0 otherwise.
     */
    private int receiveVote(int numVotes, int requiredVotes, VoteMessage msg) {
        if (msg.term > currentTerm) {
            currentTerm = msg.term;
            state = State.FOLLOWER;
            votedFor = null;
            return 0;
        } else if (msg.voteGranted) {
            if (numVotes + 1 >= requiredVotes) {
                System.out.println("Elected as leader for term " + currentTerm);
                state = State.LEADER;
                leaderId = id;
            }
            return 1;
        }
        return 0;
    }

    private void runFollower(Queue<Message> entryQueue) throws IOException {
        if (electionTimeout()) {
            startElection();
            return;
        }

        while (!entryQueue.isEmpty()) {
            Message m = entryQueue.poll();
            handleMessageFollower(m);
        }

        Message received = receive();
        handleMessageFollower(received);
    }

    private void handleAppendEntries(AppendEntriesMessage msg) throws IOException {
        lastHeartbeat = System.currentTimeMillis();
        if (msg.term > currentTerm) {
            currentTerm = msg.term;
            votedFor = null;
            leaderId = msg.src;
        }
        if (msg.entries.length == 0) {
            return;
        }
        if (validAppendEntries(msg)) {
            state = State.FOLLOWER;
            leaderId = msg.src;
            if (log.size() > msg.prevLogIndex + 1) {
                System.out.println("Log size is " + log.size() + " but expected " + (msg.prevLogIndex + 1));
                log.subList(msg.prevLogIndex + 1, log.size()).clear();
            }
            log.addAll(Arrays.asList(msg.entries));
//            System.out.println("Log size now: " + log.size());

            if (msg.leaderCommit > commitIndex) {
                commitIndex = Math.min(log.size() - 1, msg.leaderCommit);
                for (int j = lastApplied + 1; j <= commitIndex; j++) {
                    PutEntry e = log.get(j);
                    kvStore.put(e.getKey(), e.getValue());
                }
                lastApplied = commitIndex;
            }
            send(new AppendEntriesResponseMessage(id, msg.src, leaderId, currentTerm, true, msg.MID, log.size() - 1));

        } else {
            send(new AppendEntriesResponseMessage(id, msg.src, leaderId, currentTerm, false, msg.MID, msg.prevLogIndex + msg.entries.length));
        }
    }

    /**
     * Checks if the append entries message is valid.
     *
     * @param msg the appendEntries message.
     * @return true if the message is valid, false otherwise.
     */
    private boolean validAppendEntries(AppendEntriesMessage msg) {
        if (msg.term < currentTerm) {
            return false;
        }
        if (msg.prevLogIndex > log.size() - 1) {
            return false;
        }

        return msg.prevLogIndex <= 0 || log.get(msg.prevLogIndex).getTerm() == msg.prevLogTerm;
    }

    private void handleMessageFollower(Message received) throws IOException {
        switch (received.type) {
            case "requestVote":
                handleRequestVote((RequestVoteMessage) received);
                break;
            case "appendEntries":
                handleAppendEntries((AppendEntriesMessage) received);
                break;
            case "put":
            case "get":
                send(new RedirectMessage(id, received.src, leaderId, received.MID));
                break;
        }
    }

    /**
     * Handles an incoming request vote message. Either votes for the candidate or rejects the vote.
     *
     * @param message the request vote message.
     * @throws IOException if the VoteMessage cannot be sent.
     */
    private void handleRequestVote(RequestVoteMessage message) throws IOException {
        this.lastHeartbeat = System.currentTimeMillis();
        if (message.term > currentTerm) {
            currentTerm = message.term;
            state = State.FOLLOWER;
            votedFor = null;
        }
        if (message.term < currentTerm ||
                message.lastLogTerm < log.get(log.size() - 1).getTerm() ||
                message.lastLogTerm == log.get(log.size() - 1).getTerm() && message.lastLogIndex < log.size() - 1) {
            send(new VoteMessage(id, message.src, leaderId, message.MID, currentTerm, false));
            return;
        }

        if (votedFor == null || votedFor.equals(message.src)) {
            send(new VoteMessage(id, message.src, leaderId, message.MID, currentTerm, true));
            votedFor = message.src;
        } else {
            send(new VoteMessage(id, message.src, leaderId, message.MID, currentTerm, false));
        }
    }

    /**
     * Returns true if the election timeout has been reached.
     *
     * @return true if the election timeout has been reached.
     */
    private boolean electionTimeout() {
        return System.currentTimeMillis() - lastHeartbeat > electionTimeout;
    }

    /**
     * Starts an election.
     *
     * @throws IOException if the election cannot be started.
     */
    private void startElection() throws IOException {
//        System.out.println("Timeout! Starting election for term " + currentTerm);
        state = State.CANDIDATE;
        electionTimeout = random.nextInt(TIMEOUTMAX) + TIMEOUTMIN;
        currentTerm++;
        votedFor = id;
        lastHeartbeat = System.currentTimeMillis();
        send(new RequestVoteMessage(id, BROADCAST, BROADCAST, currentTerm, log.size() - 1, log.get(log.size() - 1).getTerm()));
    }

    private void runLeader(Queue<Message> queuedEntries) throws IOException {
        Map<String, Integer> nextIndex = initializeNextIndex();
        Map<String, Integer> matchIndex = initializeMatchIndex();
        initializeLeader(queuedEntries, nextIndex, matchIndex);

        while (this.state == State.LEADER) {
            //Send Heartbeat
            if (System.currentTimeMillis() - lastSentHeartbeat > HEARTBEAT) {
                send(new AppendEntriesMessage(id, BROADCAST, id, currentTerm, log.size() - 1, log.get(log.size() - 1).getTerm(), commitIndex));
                lastSentHeartbeat = System.currentTimeMillis();
            }

            Message received = receive();
            handleMessageLeader(received, nextIndex, matchIndex);
        }
    }

    private void initializeLeader(Queue<Message> queuedEntries, Map<String, Integer> nextIndex, Map<String, Integer> matchIndex) throws IOException {
        send(new AppendEntriesMessage(id, BROADCAST, id, currentTerm, log.size() - 1, log.get(log.size() - 1).getTerm(), commitIndex));
        lastSentHeartbeat = System.currentTimeMillis();

        while (!queuedEntries.isEmpty()) {
            Message m = queuedEntries.poll();
            handleMessageLeader(m, nextIndex, matchIndex);
        }
    }

    /**
     * Initializes the nextIndex map.
     *
     * @return the initialized nextIndex map.
     */
    private Map<String, Integer> initializeNextIndex() {
        return initializeIndexHelper(log.size());
    }

    /**
     * Initializes the matchIndex map.
     *
     * @return the initialized matchIndex map.
     */
    private Map<String, Integer> initializeMatchIndex() {
        return initializeIndexHelper(0);
    }

    /**
     * Initializes the index map.
     *
     * @param value the value to initialize the map with.
     * @return the initialized index map.
     */
    private Map<String, Integer> initializeIndexHelper(int value) {
        Map<String, Integer> index = new HashMap<>();
        for (String server : peers) {
            index.put(server, value);
        }
        return index;
    }

    /**
     * Receives a message on the socket.
     *
     * @return the received message.
     * @throws IOException if the message cannot be received.
     */
    private Message receive() throws IOException {
        DatagramPacket packet = new DatagramPacket(new byte[65535], 65535);
        socket.receive(packet);
        String message = new String(packet.getData(), 0, packet.getLength());
        return gson.fromJson(message, Message.class);
    }

    private void handleMessageLeader(Message message, Map<String, Integer> nextIndex, Map<String, Integer> matchIndex) throws IOException {
        switch (message.type) {
            case "get":
                get((GetMessage) message);
                break;
            case "put":
                put((PutMessage) message);
                break;
            case "vote":
                break;
            case "requestVote":
                handleRequestVote((RequestVoteMessage) message);
                break;
            case "appendEntriesResponse":
                handleAppendEntriesResponse((AppendEntriesResponseMessage) message, nextIndex, matchIndex);
                break;
            default:
                System.out.println("Unknown message type: " + message.type);
        }
    }

    private void get(GetMessage message) throws IOException {
        if (kvStore.containsKey(message.key)) {
            send(new OkMessage(id, message.src, message.leader, message.MID, kvStore.get(message.key)));
        } else {
            send(new OkMessage(id, message.src, message.leader, message.MID, ""));
            System.out.println("Key not found: " + message.key);
        }
    }

    private void put(PutMessage message) throws IOException {
        PutEntry putEntry = new PutEntry(message.key, message.value, currentTerm, message);
        AppendEntriesMessage appendEntries = new AppendEntriesMessage(id, BROADCAST, leaderId, currentTerm, log.size() - 1, log.get(log.size() - 1).getTerm(), commitIndex, putEntry);
        log.add(putEntry);
        send(appendEntries);
        lastSentHeartbeat = System.currentTimeMillis();
    }

    private void handleAppendEntriesResponse(AppendEntriesResponseMessage message, Map<String, Integer> nextIndex, Map<String, Integer> matchIndex) throws IOException {
        if (message.success) {
            handleAppendEntriesSuccess(message, nextIndex, matchIndex);
        } else {
            handleAppendEntriesFailure(message, nextIndex);
        }
    }

    private void handleAppendEntriesSuccess(AppendEntriesResponseMessage message, Map<String, Integer> nextIndex, Map<String, Integer> matchIndex) throws IOException {
        matchIndex.put(message.src, message.index);
        nextIndex.put(message.src, message.index + 1);
//        System.out.println("Success for " + message.src + " on message " + message.index);

        if (log.size() - 1 >= nextIndex.get(message.src)) {
//            System.out.println("Resending ");
//            send(new AppendEntriesMessage(id, message.src, leaderId, currentTerm, nextIndex.get(message.src) - 1, log.get(nextIndex.get(message.src) - 1).getTerm(), commitIndex, log.get(nextIndex.get(message.src))));
        } else {
            checkCommit(matchIndex);
        }
    }

    private void handleAppendEntriesFailure(AppendEntriesResponseMessage message, Map<String, Integer> nextIndex) throws IOException {
//        System.out.println("Fail");
        if (nextIndex.get(message.src) > 1) {
            nextIndex.put(message.src, nextIndex.get(message.src) - 1);
            PutEntry[] entries = getEntries(nextIndex.get(message.src));

            send(new AppendEntriesMessage(id, message.src, leaderId, currentTerm, nextIndex.get(message.src) - 1, log.get(nextIndex.get(message.src) - 1).getTerm(), commitIndex, entries));
        } else {
            send(new AppendEntriesMessage(id, message.src, leaderId, currentTerm, 0, 0, commitIndex, log.get(1)));
        }
    }

    private PutEntry[] getEntries(int nextIndex) {
        PutEntry[] entries = new PutEntry[log.size() - nextIndex];
        for (int i = nextIndex; i < log.size(); i++) {
            entries[i - nextIndex] = log.get(i);
        }
        return entries;
    }

    /**
     * Check if any new entries have been replicated to a majority of the cluster, if so commit them.
     *
     * @param matchIndex the matchIndex map.
     */
    private void checkCommit(Map<String, Integer> matchIndex) throws IOException {
        Map<Integer, Integer> numReplicated = numReplicatedPerIndex(matchIndex);

        //Check if any new entries have been replicated to a majority of the cluster
        for (int i = commitIndex + 1; i < log.size(); i++) {
            if (log.get(i).getTerm() == currentTerm && numReplicated.getOrDefault(i, 0) + 1 > peers.length / 2) {
                commitEntry(i);
            }
        }
    }

    private Map<Integer, Integer> numReplicatedPerIndex(Map<String, Integer> matchIndex) {
        Map<Integer, Integer> numReplicated = new HashMap<>();
        for (String server : matchIndex.keySet()) {
            if (matchIndex.get(server) > commitIndex) {
                for (int i = commitIndex + 1; i <= matchIndex.get(server); i++) {
                    numReplicated.put(i, numReplicated.getOrDefault(i, 0) + 1);
                }
            }
        }
        return numReplicated;
    }

    private void commitEntry(int index) throws IOException {
        for (int i = commitIndex + 1; i <= index; i++) {
            kvStore.put(log.get(i).getKey(), log.get(i).getValue());
        }
        commitIndex = index;
        lastApplied = index;
        PutMessage message = log.get(index).getMessage();
        send(new OkMessage(id, message.src, leaderId, message.MID));
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        String id = args[1];
        String[] peers = new String[args.length - 2];
        System.arraycopy(args, 2, peers, 0, args.length - 2);
        Replica replica = new Replica(port, id, peers);
        try {
            replica.run();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
