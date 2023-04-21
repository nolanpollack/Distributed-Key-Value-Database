package model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import messages.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.sql.SQLOutput;
import java.util.*;

public class Replica {
    enum State {
        LEADER, FOLLOWER, CANDIDATE
    }

    private final String BROADCAST = "FFFF";
    // Minimum timeout in milliseconds
    private final int TIMEOUTMIN = 250;
    // Maximum timeout in milliseconds
    private final int TIMEOUTMAX = 500;
    // Frequency of heartbeats in milliseconds
    private final int HEARTBEAT = 100;

    private final int port;
    private final String id;
    private final String[] peers;
    private final DatagramSocket socket;
    // Gson object for serializing and deserializing messages
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
        this.random = new Random();
        this.electionTimeout = random.nextInt(TIMEOUTMAX - TIMEOUTMIN) + TIMEOUTMIN;
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
    }

    /**
     * Run the replica. Delegate to the appropriate method based on the current state.
     *
     * @throws IOException if there is an error sending or receiving messages.
     */
    private void run() throws IOException {
        this.lastHeartbeat = System.currentTimeMillis();
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

    /**
     * Run the candidate.
     *
     * @throws IOException if there is an error sending or receiving messages.
     */
    private void runCandidate() throws IOException {
        int numVotes = 1;
        int requiredVotes = peers.length / 2 + 1;
        Queue<Message> entryQueue = new LinkedList<>();
        System.out.println("Starting election for term " + currentTerm);
        while (this.state == State.CANDIDATE) {
            if (electionTimeout()) {
                startElection();
                numVotes = 1; // reset votes
                System.out.println("Starting election for term " + currentTerm);
            }

            Message received = receive();
            numVotes = handleMessageCandidate(received, numVotes, requiredVotes, entryQueue);
        }
        if (state.equals(State.LEADER)) {
            runLeader(entryQueue);
        } else if (state.equals(State.FOLLOWER)) {
            runFollower(entryQueue);
        }
    }

    /**
     * Handle a message received by a candidate.
     *
     * @param received      the message received.
     * @param numVotes      the number of votes received so far.
     * @param requiredVotes the number of votes required to win.
     * @param entryQueue    the queue of entries to be processed when the election ends.
     * @return the number of votes received so far.
     * @throws IOException if there is an error sending or receiving messages.
     */
    private int handleMessageCandidate(Message received, int numVotes, int requiredVotes, Queue<Message> entryQueue) throws IOException {
        System.out.println("Received: " + received.type);
        switch (received.type) {
            case "vote":
                numVotes += receiveVote(numVotes, requiredVotes, (VoteMessage) received);
                break;
            case "appendEntries":
                handleAppendEntries((AppendEntriesMessage) received);
                break;
            case "put":
            case "get":
            case "appendEntriesResponse":
                entryQueue.add(received);
                break;
            case "requestVote":
                handleRequestVote((RequestVoteMessage) received);
                break;
            default:
        }
        return numVotes;
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
        lastHeartbeat = System.currentTimeMillis();
//        System.out.println("Received " + msg.voteGranted + " vote from " + msg.src + " for term " + msg.term);
        if (msg.term > currentTerm) {
            convertToFollower(msg.term, msg.leader);
            return 0;
        } else if (msg.voteGranted && msg.term == currentTerm) {
            if (numVotes + 1 >= requiredVotes) {
                System.out.println("Elected as leader for term " + currentTerm);
                state = State.LEADER;
                leaderId = id;
            }
            return 1;
        }
        return 0;
    }

    private void convertToFollower(int term, String leader) {
        currentTerm = term;
        votedFor = null;
        state = State.FOLLOWER;
        leaderId = leader;
        lastHeartbeat = System.currentTimeMillis();
    }

    /**
     * Run the follower.
     *
     * @param entryQueue a queue of entries to be processed initially. This is used to process entries
     *                   received during an election.
     * @throws IOException if there is an error sending or receiving messages.
     */
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


    /**
     * Handle an AppendEntries message received by a follower or candidate.
     *
     * @param msg the AppendEntries message.
     * @throws IOException if there is an error sending or receiving messages.
     */
    private void handleAppendEntries(AppendEntriesMessage msg) throws IOException {
        lastHeartbeat = System.currentTimeMillis();
        if (msg.term > currentTerm) {
            convertToFollower(msg.term, msg.src);
        }
        if (msg.entries.length == 0) {
            return;
        }
//        System.out.println("Received AppendEntries from index " + (msg.prevLogIndex + 1) + "-" + (msg.prevLogIndex + msg.entries.length));
        if (validAppendEntries(msg)) {
            handleValidAppendEntries(msg);
        } else {
            handleInvalidAppendEntries(msg);
        }
    }

    /**
     * Handle an append entries accepted by a follower or candidate.
     *
     * @param msg the appendEntries message.
     * @throws IOException
     */
    private void handleValidAppendEntries(AppendEntriesMessage msg) throws IOException {
        state = State.FOLLOWER;
        leaderId = msg.src;
        if (log.size() > msg.prevLogIndex + 1) {
            log.subList(msg.prevLogIndex + 1, log.size()).clear();
        }
        log.addAll(Arrays.asList(msg.entries));

        if (msg.leaderCommit > commitIndex) {
            updateCommits(msg.leaderCommit);

        }
        send(new AppendEntriesResponseMessage(id, msg.src, leaderId, currentTerm, true, msg.MID, log.size() - 1));
    }

    /**
     * Update the commit index for a follower.
     *
     * @param leaderCommit the leader's commit index known by the follower.
     */
    private void updateCommits(int leaderCommit) {
        commitIndex = Math.min(Math.max(commitIndex, log.size() - 1), leaderCommit);
        for (int j = lastApplied + 1; j <= commitIndex; j++) {
            PutEntry e = log.get(j);
            kvStore.put(e.getKey(), e.getValue());
        }
        lastApplied = commitIndex;
    }

    /**
     * Handle an append entries rejected by a follower or candidate.
     *
     * @param msg the appendEntries message.
     * @throws IOException if there is an error sending or receiving messages.
     */
    private void handleInvalidAppendEntries(AppendEntriesMessage msg) throws IOException {
        int conflictingTerm = msg.entries[0].getTerm();
        int conflictingIndex = log.size() - 1;
        if (currentTerm < msg.prevLogTerm) {
            conflictingTerm = currentTerm;
        } else if (msg.prevLogIndex >= log.size()) {
            conflictingTerm = log.get(log.size() - 1).getTerm();
        } else if (log.get(msg.prevLogIndex).getTerm() != msg.prevLogTerm) {
            conflictingTerm = log.get(msg.prevLogIndex).getTerm();
            conflictingIndex = firstWithTerm(conflictingTerm);
        }

        send(new AppendEntriesResponseMessage(id, msg.src, leaderId, currentTerm, false, msg.MID, msg.prevLogIndex + msg.entries.length, conflictingTerm, conflictingIndex));
    }

    /**
     * Find the first index in the log with the given term.
     *
     * @param term the term to search for.
     * @return the index of the first entry with the given term.
     */
    private int firstWithTerm(int term) {
        int firstWithTerm = log.size();
        for (int i = log.size() - 1; i >= 0; i--) {
            if (log.get(i).getTerm() == term) {
                firstWithTerm = i;
            } else if (log.get(i).getTerm() < term) {
                break;
            }
        }
        return firstWithTerm;
    }

    /**
     * Checks if the append entries message is valid, according to the Raft paper.
     *
     * @param msg the appendEntries message.
     * @return true if the message is valid, false otherwise.
     */
    private boolean validAppendEntries(AppendEntriesMessage msg) {
        if (msg.term < currentTerm) {
            System.out.println("Invalid append entries: " + msg.term + " < " + currentTerm);
            return false;
        }
        convertToFollower(msg.term, msg.src);
        if (msg.prevLogIndex > log.size() - 1) {
            System.out.println("Invalid append entries: " + msg.prevLogIndex + " > " + (log.size() - 1));
            return false;
        }
        if (msg.prevLogIndex > 0 && log.get(msg.prevLogIndex).getTerm() != msg.prevLogTerm) {
            System.out.println("Invalid append entries: " + log.get(msg.prevLogIndex).getTerm() + " != " + msg.prevLogTerm);
            return false;
        }
        return msg.prevLogIndex <= 0 || log.get(msg.prevLogIndex).getTerm() == msg.prevLogTerm;
    }

    /**
     * Handle a message received by a follower.
     *
     * @param received the message received.
     * @throws IOException if there is an error sending or receiving messages.
     */
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
            default:
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
            convertToFollower(message.term, message.src);
        }
        if (message.term < currentTerm ||
                message.lastLogTerm < log.get(log.size() - 1).getTerm() ||
                ((message.lastLogTerm == log.get(log.size() - 1).getTerm()) && message.lastLogIndex < log.size() - 1)) {
            System.out.println("Voting false for " + message.src);
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
        if (System.currentTimeMillis() - lastHeartbeat > electionTimeout) {
//            System.out.println("Election timeout reached " + (System.currentTimeMillis() - lastHeartbeat) + " " + electionTimeout);
        }
        return System.currentTimeMillis() - lastHeartbeat > electionTimeout;
    }

    /**
     * Starts an election.
     *
     * @throws IOException if the election cannot be started.
     */
    private void startElection() throws IOException {
        state = State.CANDIDATE;
        electionTimeout = random.nextInt(TIMEOUTMAX - TIMEOUTMIN) + TIMEOUTMIN;
        currentTerm++;
        votedFor = id;
        lastHeartbeat = System.currentTimeMillis();
        send(new RequestVoteMessage(id, BROADCAST, BROADCAST, currentTerm, log.size() - 1, log.get(log.size() - 1).getTerm()));
    }

    /**
     * Runs the leader state.
     *
     * @param queuedEntries entries queued before becoming leader, to now be processed.
     * @throws IOException if there is an error sending or receiving messages.
     */
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

    /**
     * Initializes the leader state. Sends an initial heartbeat and processes any queued entries.
     *
     * @param queuedEntries entries queued before becoming leader, to now be processed.
     * @param nextIndex     the nextIndex map.
     * @param matchIndex    the matchIndex map.
     * @throws IOException if there is an error sending or receiving messages.
     */
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

    /**
     * Handles a message in the leader state.
     *
     * @param message    the message to handle.
     * @param nextIndex  the nextIndex map.
     * @param matchIndex the matchIndex map.
     * @throws IOException if there is an error sending or receiving messages.
     */
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
            case "appendEntries":
                handleAppendEntriesLeader((AppendEntriesMessage) message);
                break;
            default:
                System.out.println("Unknown message type: " + message.type);
        }
    }

    /**
     * Handles an appendEntries message in the leader state.
     *
     * @param msg the appendEntries message to handle.
     * @throws IOException if there is an error sending or receiving messages.
     */
    private void handleAppendEntriesLeader(AppendEntriesMessage msg) throws IOException {
        if (msg.term > currentTerm) {
            convertToFollower(msg.term, msg.src);
        } else {
            send(new AppendEntriesResponseMessage(id, msg.src, msg.leader, currentTerm, false, msg.MID, msg.prevLogIndex + msg.entries.length));
        }
    }

    /**
     * Handles a get message in the leader state. Checks if the key is in the kvStore, and sends an ok message with the value.
     * If the key is not in the kvStore, sends an ok message with an empty value.
     *
     * @param message the get message to handle.
     * @throws IOException if there is an error sending or receiving messages.
     */
    private void get(GetMessage message) throws IOException {
        if (kvStore.containsKey(message.key)) {
            send(new OkMessage(id, message.src, message.leader, message.MID, kvStore.get(message.key)));
        } else {
            send(new OkMessage(id, message.src, message.leader, message.MID, ""));
            System.out.println("Key not found: " + message.key);
        }
    }

    /**
     * Handles a put message in the leader state. Adds the put entry to the log and broadcasts an appendEntries message.
     *
     * @param message the put message to handle.
     * @throws IOException if there is an error sending or receiving messages.
     */
    private void put(PutMessage message) throws IOException {
        PutEntry putEntry = new PutEntry(message.key, message.value, currentTerm, message);
        AppendEntriesMessage appendEntries = new AppendEntriesMessage(id, BROADCAST, leaderId, currentTerm, log.size() - 1, log.get(log.size() - 1).getTerm(), commitIndex, putEntry);
        send(appendEntries);
//        System.out.println("Adding put to log at index " + log.size() + ": " + gson.toJson(putEntry));
        log.add(putEntry);
        lastSentHeartbeat = System.currentTimeMillis();
    }

    /**
     * Handles an appendEntriesResponse message in the leader state.
     *
     * @param message    the appendEntriesResponse message to handle.
     * @param nextIndex  the nextIndex map.
     * @param matchIndex the matchIndex map.
     * @throws IOException if there is an error sending or receiving messages.
     */
    private void handleAppendEntriesResponse(AppendEntriesResponseMessage message, Map<String, Integer> nextIndex, Map<String, Integer> matchIndex) throws IOException {
        if (message.success) {
            handleAppendEntriesSuccess(message, nextIndex, matchIndex);
        } else {
            handleAppendEntriesFailure(message, nextIndex);
        }
    }

    /**
     * Handles a successful appendEntriesResponse message in the leader state. Updates the matchIndex and nextIndex maps,
     * then checks if the log can be committed.
     *
     * @param message    the appendEntriesResponse message to handle.
     * @param nextIndex  the nextIndex map.
     * @param matchIndex the matchIndex map.
     * @throws IOException if there is an error sending or receiving messages.
     */
    private void handleAppendEntriesSuccess(AppendEntriesResponseMessage message, Map<String, Integer> nextIndex, Map<String, Integer> matchIndex) throws IOException {
        matchIndex.put(message.src, message.index);
        nextIndex.put(message.src, message.index + 1);

        if (log.size() - 1 < nextIndex.get(message.src)) {
            checkCommit(matchIndex);
        }
    }

    /**
     * Handles a failed appendEntriesResponse message in the leader state. Decrements the nextIndex map for the server,
     * then sends all entries from the nextIndex to the end of the log (will not send more than 100 entries at a time due
     * to packet size limitations).
     *
     * @param message   the appendEntriesResponse message to handle.
     * @param nextIndex the nextIndex map.
     * @throws IOException if there is an error sending or receiving messages.
     */
    private void handleAppendEntriesFailure(AppendEntriesResponseMessage message, Map<String, Integer> nextIndex) throws IOException {
        if (message.term > currentTerm) {
            convertToFollower(message.term, message.src);
//            electionTimeout = random.nextInt(TIMEOUTMAX - TIMEOUTMIN) + TIMEOUTMIN;
            return;
        }
        if (nextIndex.get(message.src) > 1) {
            if (message.conflictTermFirstIndex > log.size() - 1) {
                nextIndex.put(message.src, log.size() - 1);
            } else if (message.conflictTermFirstIndex > 1) {
                if (log.get(message.conflictTermFirstIndex).getTerm() == message.conflictTerm) {
                    nextIndex.put(message.src, message.conflictTermFirstIndex);
                } else {
                    nextIndex.put(message.src, message.conflictTermFirstIndex - 1);
                }
            } else {
                nextIndex.put(message.src, 1);
            }

            PutEntry[] entries = getEntries(nextIndex.get(message.src));
            send(new AppendEntriesMessage(id, message.src, leaderId, currentTerm, nextIndex.get(message.src) - 1, log.get(nextIndex.get(message.src) - 1).getTerm(), commitIndex, entries));
        } else {
            send(new AppendEntriesMessage(id, message.src, leaderId, currentTerm, 0, 0, commitIndex, log.get(1)));
        }
    }

    /**
     * Gets the entries to send to a server, starting from nextIndex, all the way to the end of the log, or until there
     * is 100 entries, whichever comes first.
     *
     * @param nextIndex the nextIndex map.
     * @return the entries to send to the server.
     */
    private PutEntry[] getEntries(int nextIndex) {
        int numEntriesToSend = Math.min(log.size() - nextIndex, 100);
        PutEntry[] entries = new PutEntry[numEntriesToSend];
        for (int i = nextIndex; i < nextIndex + numEntriesToSend; i++) {
            PutEntry entry = log.get(i);
            entries[i - nextIndex] = entry;
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
//        System.out.println(matchIndex.values());
        for (int i = commitIndex + 1; i < log.size(); i++) {
            if (log.get(i).getTerm() == currentTerm && (numReplicated.getOrDefault(i, 0) + 1) > (peers.length / 2)) {
                commitEntry(i);
            }
        }
    }

    /**
     * Returns a map of the number of entries replicated to each index.
     *
     * @param matchIndex the matchIndex map.
     * @return a map of the number of entries replicated to each index.
     */
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

    /**
     * Commits all entries up to the given index for the leader.
     *
     * @param index the index to commit up to.
     * @throws IOException if there is an error sending or receiving messages.
     */
    private void commitEntry(int index) throws IOException {
        for (int i = commitIndex + 1; i <= index; i++) {
            if (log.get(i).getTerm() == currentTerm) {
                kvStore.put(log.get(i).getKey(), log.get(i).getValue());
                PutMessage message = log.get(i).getMessage();
                send(new OkMessage(id, message.src, leaderId, message.MID));
//                System.out.println("Committed entry " + i + ". Term: " + log.get(i).getTerm() + " Key: " + log.get(i).getKey() + " Value:" + log.get(i).getValue() + " Num Uncommitted: " + (log.size() - commitIndex - 1));
            } else {
                kvStore.put(log.get(i).getKey(), log.get(i).getValue());
//                System.out.println("Adding entry " + i + " . Term: " + log.get(i).getTerm() + " Key: " + log.get(i).getKey() + " Value:" + log.get(i).getValue());
            }
        }
        commitIndex = index;
        lastApplied = index;
    }

    /**
     * Creates a new replica with the given port, id, and peers.
     *
     * @param args the command line arguments.
     */
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
