package meblox;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import javax.microedition.io.Connector;
import javax.microedition.io.ServerSocketConnection;
import javax.microedition.io.SocketConnection;

/**
 * Small TCP session for up to five Roblox3DME players on one local network.
 * MIDP socket support is optional, so callers handle failures and show the
 * device's networking error instead of preventing the MIDlet from starting.
 */
final class LanSession {
    static final int PORT = 47653;
    private static final int MAGIC = 0x4D334C32; // "M3L2"
    private static final int MAX_PLAYERS = 5;

    static final class PlayerState {
        public boolean connected;
        public float x, y, z, yaw;
        public int health;
        public int skinColor=0xF5C542, shirtColor=0x1769AA, legColor=0x2E9E45;
        public boolean alive;
    }

    private final boolean host;
    private final int gameId;
    private final int localSlot;
    private final PlayerState[] players = new PlayerState[MAX_PLAYERS];
    private final Peer[] peers = new Peer[MAX_PLAYERS];
    private final PlayerState[] broadcastSnapshot = new PlayerState[MAX_PLAYERS];
    private final boolean[] readConnected = new boolean[MAX_PLAYERS];
    private final float[][] readPosition = new float[MAX_PLAYERS][4];
    private final int[] readHealth = new int[MAX_PLAYERS];
    private final int[] readSkin = new int[MAX_PLAYERS];
    private final int[] readShirt = new int[MAX_PLAYERS];
    private final int[] readLegs = new int[MAX_PLAYERS];
    private final boolean[] readAlive = new boolean[MAX_PLAYERS];
    private ServerSocketConnection server;
    private volatile boolean closed;
    private SocketConnection client;
    private DataInputStream clientIn;
    private DataOutputStream clientOut;
    private Thread reader;
    private long lastWriteAt;

    private LanSession(boolean isHost, int game, int slot) {
        host = isHost;
        gameId = game;
        localSlot = slot;
        for (int i = 0; i < MAX_PLAYERS; i++) players[i] = new PlayerState();
        players[slot].connected = true;
        players[slot].alive = true;
        players[slot].health = 100;
    }

    static LanSession host(int game) throws IOException {
        final LanSession session = new LanSession(true, game, 0);
        session.server = (ServerSocketConnection)Connector.open("socket://:" + PORT);
        Thread accept = new Thread(new Runnable() {
            public void run() { session.acceptClients(); }
        });
        accept.start();
        return session;
    }

    static LanSession join(String address) throws IOException {
        if (address == null) throw new IOException("Enter the host's IPv4 address");
        address = address.trim();
        if (address.length() == 0 || address.indexOf(":") >= 0 ||
                address.indexOf("/") >= 0 || address.indexOf(" ") >= 0)
            throw new IOException("Enter an IPv4 address, e.g. 192.168.1.20");
        SocketConnection socket = (SocketConnection)Connector.open(
                "socket://" + address + ":" + PORT);
        DataInputStream input = socket.openDataInputStream();
        DataOutputStream output = socket.openDataOutputStream();
        output.writeInt(MAGIC);
        output.flush();
        int game = input.readInt();
        int slot = input.readInt();
        if (game < 0 || game >= 15 || slot < 1 || slot >= MAX_PLAYERS) {
            try { socket.close(); } catch (Throwable ignored) {}
            throw new IOException("The host rejected this game session");
        }
        final LanSession session = new LanSession(false, game, slot);
        session.client = socket;
        session.clientIn = input;
        session.clientOut = output;
        Thread read = new Thread(new Runnable() {
            public void run() { session.readSnapshots(); }
        });
        session.reader = read;
        read.start();
        return session;
    }

    int getGameId() { return gameId; }
    int getLocalSlot() { return localSlot; }
    boolean isHost() { return host; }
    boolean isConnected() { return !closed; }

    String getLocalAddress() {
        if (server == null) return "";
        try { return server.getLocalAddress(); }
        catch (Throwable t) { return ""; }
    }

    synchronized void copySnapshot(PlayerState[] copy) {
        if (copy == null || copy.length < MAX_PLAYERS) return;
        for (int i = 0; i < MAX_PLAYERS; i++) {
            PlayerState source = players[i];
            PlayerState target = copy[i];
            if (target == null) {
                target = new PlayerState();
                copy[i] = target;
            }
            target.connected = source.connected;
            target.x = source.x; target.y = source.y; target.z = source.z;
            target.yaw = source.yaw; target.health = source.health;
            target.skinColor=source.skinColor; target.shirtColor=source.shirtColor;
            target.legColor=source.legColor;
            target.alive = source.alive;
        }
    }

    void publish(float x, float y, float z, float yaw, int health, boolean alive,
            int skinColor, int shirtColor, int legColor) {
        long now = System.currentTimeMillis();
        if (now - lastWriteAt < 90L || closed) return;
        lastWriteAt = now;
        synchronized (this) {
            PlayerState local = players[localSlot];
            local.connected = true;
            local.x = x; local.y = y; local.z = z; local.yaw = yaw;
            local.health = health; local.alive = alive;
            local.skinColor=skinColor; local.shirtColor=shirtColor; local.legColor=legColor;
        }
        if (host) broadcast();
        else writeClientState(x, y, z, yaw, health, alive,
                skinColor, shirtColor, legColor);
    }

    private void writeClientState(float x, float y, float z, float yaw,
            int health, boolean alive, int skinColor, int shirtColor,
            int legColor) {
        try {
            synchronized (clientOut) {
                clientOut.writeFloat(x); clientOut.writeFloat(y);
                clientOut.writeFloat(z); clientOut.writeFloat(yaw);
                clientOut.writeInt(health); clientOut.writeBoolean(alive);
                clientOut.writeInt(skinColor); clientOut.writeInt(shirtColor);
                clientOut.writeInt(legColor);
                clientOut.flush();
            }
        } catch (Throwable t) {
            close();
        }
    }

    private void acceptClients() {
        while (!closed) {
            SocketConnection socket = null;
            try {
                socket = (SocketConnection)server.acceptAndOpen();
                DataInputStream input = socket.openDataInputStream();
                DataOutputStream output = socket.openDataOutputStream();
                if (input.readInt() != MAGIC) {
                    socket.close();
                    continue;
                }
                int slot = reserveSlot();
                if (slot < 1) {
                    output.writeInt(-1); output.writeInt(-1); output.flush();
                    socket.close();
                    continue;
                }
                output.writeInt(gameId); output.writeInt(slot); output.flush();
                Peer peer = new Peer(socket, input, output, slot);
                synchronized (this) { peers[slot] = peer; }
                Thread worker = new Thread(peer);
                worker.start();
                socket = null;
            } catch (Throwable t) {
                if (socket != null) try { socket.close(); } catch (Throwable ignored) {}
                if (!closed) {
                    try { Thread.sleep(250L); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    private synchronized int reserveSlot() {
        for (int i = 1; i < MAX_PLAYERS; i++) {
            if (peers[i] == null) {
                players[i].connected = true;
                players[i].alive = true;
                players[i].health = 100;
                return i;
            }
        }
        return -1;
    }

    private void broadcast() {
        copySnapshot(broadcastSnapshot);
        PlayerState[] copy = broadcastSnapshot;
        for (int slot = 1; slot < MAX_PLAYERS; slot++) {
            Peer peer;
            synchronized (this) { peer = peers[slot]; }
            if (peer == null) continue;
            try {
                synchronized (peer.output) {
                    for (int i = 0; i < MAX_PLAYERS; i++) {
                        PlayerState state = copy[i];
                        peer.output.writeBoolean(state.connected);
                        if (state.connected) {
                            peer.output.writeFloat(state.x);
                            peer.output.writeFloat(state.y);
                            peer.output.writeFloat(state.z);
                            peer.output.writeFloat(state.yaw);
                            peer.output.writeInt(state.health);
                            peer.output.writeBoolean(state.alive);
                            peer.output.writeInt(state.skinColor);
                            peer.output.writeInt(state.shirtColor);
                            peer.output.writeInt(state.legColor);
                        }
                    }
                    peer.output.flush();
                }
            } catch (Throwable t) {
                peer.close();
            }
        }
    }

    private void readSnapshots() {
        try {
            while (!closed) {
                for (int i = 0; i < MAX_PLAYERS; i++) {
                    readConnected[i] = clientIn.readBoolean();
                    if (readConnected[i]) {
                        readPosition[i][0] = clientIn.readFloat();
                        readPosition[i][1] = clientIn.readFloat();
                        readPosition[i][2] = clientIn.readFloat();
                        readPosition[i][3] = clientIn.readFloat();
                        readHealth[i] = clientIn.readInt();
                        readAlive[i] = clientIn.readBoolean();
                        readSkin[i] = clientIn.readInt();
                        readShirt[i] = clientIn.readInt();
                        readLegs[i] = clientIn.readInt();
                    }
                }
                synchronized (this) {
                    for (int i = 0; i < MAX_PLAYERS; i++) {
                        PlayerState state = players[i];
                        state.connected = readConnected[i];
                        if (readConnected[i]) {
                            state.x = readPosition[i][0]; state.y = readPosition[i][1];
                            state.z = readPosition[i][2]; state.yaw = readPosition[i][3];
                            state.health = readHealth[i]; state.alive = readAlive[i];
                            state.skinColor=readSkin[i]; state.shirtColor=readShirt[i];
                            state.legColor=readLegs[i];
                        }
                    }
                }
            }
        } catch (Throwable t) {
            if (!closed) close();
        }
    }

    void close() {
        if (closed) return;
        closed = true;
        if (server != null) try { server.close(); } catch (Throwable ignored) {}
        if (client != null) try { client.close(); } catch (Throwable ignored) {}
        if (clientIn != null) try { clientIn.close(); } catch (Throwable ignored) {}
        if (clientOut != null) try { clientOut.close(); } catch (Throwable ignored) {}
        synchronized (this) {
            for (int i = 1; i < MAX_PLAYERS; i++) {
                if (peers[i] != null) peers[i].close();
                peers[i] = null;
                players[i].connected = false;
            }
        }
    }

    private final class Peer implements Runnable {
        final SocketConnection socket;
        final DataInputStream input;
        final DataOutputStream output;
        final int slot;
        Peer(SocketConnection s, DataInputStream in, DataOutputStream out, int id) {
            socket = s; input = in; output = out; slot = id;
        }
        public void run() {
            try {
                while (!closed) {
                    float x = input.readFloat();
                    float y = input.readFloat();
                    float z = input.readFloat();
                    float yaw = input.readFloat();
                    int health = input.readInt();
                    boolean alive = input.readBoolean();
                    int skinColor=input.readInt();
                    int shirtColor=input.readInt();
                    int legColor=input.readInt();
                    synchronized (LanSession.this) {
                        PlayerState state = players[slot];
                        state.x = x; state.y = y; state.z = z;
                        state.yaw = yaw; state.health = health;
                        state.skinColor=skinColor;state.shirtColor=shirtColor;
                        state.legColor=legColor;
                        state.alive = alive; state.connected = true;
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                close();
            }
        }
        void close() {
            try { socket.close(); } catch (Throwable ignored) {}
            synchronized (LanSession.this) {
                if (peers[slot] == this) peers[slot] = null;
                players[slot].connected = false;
            }
        }
    }
}
