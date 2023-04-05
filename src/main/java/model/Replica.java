package model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import messages.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class Replica {
    enum State {
        LEADER, FOLLOWER, CANDIDATE
    }

    private final String BROADCAST = "FFFF";
    private final int TIMEOUTMIN = 750;
    private final int TIMEOUTMAX = 1500;

    private final int port;
    private final String id;
    private String[] peers;
    private final DatagramSocket socket;
    Gson gson;
    private final Random random;

    private int currentTerm;
    private String votedFor;
    List<Entry> log;
    private int commitIndex = 0;
    private int lastApplied = 0;

    HashMap<String, String> kvStore;
    private String leaderId;
    private long lastHeartbeat;
    private long electionTimeout;
    private State state;

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
        log.add(new Entry("0", "0", 0));
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

    private Gson initializeGson() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Message.class, new json.Adapters.MessageDeserializer());
        return builder.create();
    }

    private void send(Message msg) throws IOException {
        String message = new Gson().toJson(msg);

        DatagramPacket packet = new DatagramPacket(message.getBytes(),
                message.getBytes().length,
                new InetSocketAddress("localhost", port));
        this.socket.send(packet);
//        System.out.println("Sent " + new String(packet.getData()));
    }

    private void run() throws IOException {
        while (true) {
            switch (state) {
                case LEADER:
                    runLeader();
                    break;
                case FOLLOWER:
                    runFollower();
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
                    send(new FailMessage(id, received.src, leaderId, received.MID));
                    break;
            }
        }
    }

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
            }
            return 1;
        }
        return 0;
    }

    private void runFollower() throws IOException {
        if (electionTimeout()) {
            startElection();
            return;
        }
        Message received = receive();
        handleMessageFollower(received);
    }

    private void handleAppendEntries(AppendEntriesMessage msg) throws IOException {
        if (validAppendEntries(msg)) {
            currentTerm = msg.term;
            state = State.FOLLOWER;
            votedFor = null;
            leaderId = msg.src;
        } else {
            send(new AppendEntriesResponseMessage(id, msg.src, leaderId, currentTerm, false, msg.MID));
        }
    }

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
                AppendEntriesMessage appendEntriesMessage = ((AppendEntriesMessage) received);
                lastHeartbeat = System.currentTimeMillis();
                if (appendEntriesMessage.term > currentTerm) {
                    votedFor = null;
                    currentTerm = appendEntriesMessage.term;
                    state = State.FOLLOWER;
                    leaderId = appendEntriesMessage.src;
                }
                break;
            case "put":
            case "get":
                send(new RedirectMessage(id, received.src, leaderId, received.MID));
                break;
        }
    }

    private void handleRequestVote(RequestVoteMessage message) throws IOException {
        this.lastHeartbeat = System.currentTimeMillis();
        if (message.term > currentTerm) {
            currentTerm = message.term;
            state = State.FOLLOWER;
            votedFor = null;
        }
        if (message.term < currentTerm) {
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

    private boolean electionTimeout() {
        return System.currentTimeMillis() - lastHeartbeat > electionTimeout;
    }

    private void startElection() throws IOException {
        System.out.println("Timeout! Starting election for term " + currentTerm);
        state = State.CANDIDATE;
        electionTimeout = random.nextInt(TIMEOUTMAX) + TIMEOUTMIN;
        currentTerm++;
        votedFor = id;
        lastHeartbeat = System.currentTimeMillis();
        send(new RequestVoteMessage(id, BROADCAST, BROADCAST, currentTerm, log.size() - 1, log.get(log.size() - 1).getTerm()));
    }

    private void runLeader() throws IOException {
        long lastSentHeartbeat = System.currentTimeMillis();
        send(new AppendEntriesMessage(id, BROADCAST, id, currentTerm, log.size() - 1, log.get(log.size() - 1).getTerm(), commitIndex));
        while (this.state == State.LEADER) {
            if (System.currentTimeMillis() - lastSentHeartbeat > 500) {
                send(new AppendEntriesMessage(id, BROADCAST, id, currentTerm, log.size() - 1, log.get(log.size() - 1).getTerm(), commitIndex));
                lastSentHeartbeat = System.currentTimeMillis();
            }
            Message received = receive();
            handleMessageLeader(received);
        }
    }

    private Message receive() throws IOException {
        DatagramPacket packet = new DatagramPacket(new byte[65535], 65535);
        socket.receive(packet);
        String message = new String(packet.getData(), 0, packet.getLength());
        Message msg = gson.fromJson(message, Message.class);
//        System.out.println("Received " + msg.type + " from " + msg.src);
        return msg;
    }

    private void handleMessageLeader(Message message) throws IOException {
        switch (message.type) {
            case "get":
                handleGet((GetMessage) message);
                break;
            case "put":
                handlePut((PutMessage) message);
                break;
            case "vote":
                break;
            case "requestVote":
                handleRequestVote((RequestVoteMessage) message);
                break;
            default:
                System.out.println("Unknown message type: " + message.type);
        }
    }

    private void handleGet(GetMessage message) throws IOException {
        send(new FailMessage(id, message.src, message.leader, message.MID));
    }

    private void handlePut(PutMessage message) throws IOException {
        send(new FailMessage(id, message.src, message.leader, message.MID));
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
