package crossspire.network;

import basemod.BaseMod;
import crossspire.CrossSpireMod;
import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class P2PManager {

    private static final int DEFAULT_PORT = 54321;
    private final int listenPort;
    private ServerSocket serverSocket;
    private final Map<String, Socket> connections = new ConcurrentHashMap<String, Socket>();
    private boolean running = false;

    public P2PManager() {
        String portProp = System.getProperty("crossspire.p2p.port");
        listenPort = portProp != null ? Integer.parseInt(portProp) : DEFAULT_PORT;
    }

    public void start() {
        if (running) return;
        running = true;
        try {
            serverSocket = new ServerSocket(listenPort);
            BaseMod.logger.info("P2PManager listening on :" + listenPort);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (running) {
                        try {
                            Socket client = serverSocket.accept();
                            String peerId = readPeerId(client);
                            if (peerId != null) {
                                connections.put(peerId, client);
                                BaseMod.logger.info("P2PManager accepted connection from " + peerId.substring(0, 8));
                                startReader(peerId, client);
                            }
                        } catch (IOException e) {
                            if (running) BaseMod.logger.error("P2PManager accept error: " + e.getMessage());
                        }
                    }
                }
            }, "P2P-Accept").start();

        } catch (IOException e) {
            BaseMod.logger.error("P2PManager start error: " + e.getMessage());
        }
    }

    public void connectTo(String peerId, String host, int port) {
        if (connections.containsKey(peerId)) return;
        try {
            Socket socket = new Socket(host, port);
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            w.write(CrossSpireMod.playerId + "\n");
            w.flush();
            connections.put(peerId, socket);
            BaseMod.logger.info("P2PManager connected to " + peerId.substring(0, 8) + " @" + host + ":" + port);
            startReader(peerId, socket);
        } catch (IOException e) {
            BaseMod.logger.error("P2PManager connect error: " + e.getMessage());
        }
    }

    private boolean isDirect(String peerId) {
        Socket s = connections.get(peerId);
        return s != null && !s.isClosed();
    }

    public void sendOrRelay(String peerId, String message) {
        if (isDirect(peerId)) {
            send(peerId, message);
        } else {
            relayViaServer(message);
        }
    }

    public void relayViaServer(String message) {
        if (CrossSpireMod.relayClient != null && CrossSpireMod.relayClient.isOpen()) {
            CrossSpireMod.relayClient.send(message);
        }
    }

    public void send(String peerId, String message) {
        Socket s = connections.get(peerId);
        if (s == null || s.isClosed()) {
            relayViaServer(message);
            return;
        }
        try {
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"));
            w.write(message + "\n");
            w.flush();
        } catch (IOException e) {
            BaseMod.logger.error("P2PManager send error, falling back to relay: " + e.getMessage());
            connections.remove(peerId);
            relayViaServer(message);
        }
    }

    public void broadcast(String message) {
        for (Map.Entry<String, Socket> entry : connections.entrySet()) {
            send(entry.getKey(), message);
        }
    }

    public void sendHello() {
        if (CrossSpireMod.relayClient == null || !CrossSpireMod.relayClient.isOpen()) return;
        Protocol.HelloMessage hello = new Protocol.HelloMessage();
        hello.ip = "127.0.0.1";
        hello.port = listenPort;
        hello.source = CrossSpireMod.playerId;
        hello.seq = 1;
        CrossSpireMod.relayClient.send(Protocol.GSON.toJson(hello));
        BaseMod.logger.info("P2PManager sent hello: 127.0.0.1:" + listenPort);
    }

    public void onHelloReceived(String rawMessage) {
        Protocol.HelloMessage hello = Protocol.GSON.fromJson(rawMessage, Protocol.HelloMessage.class);
        if (hello.source == null || hello.source.equals(CrossSpireMod.playerId)) return;
        if (connections.containsKey(hello.source)) return;
        BaseMod.logger.info("P2PManager hello from " + hello.source.substring(0, 8) + " @" + hello.ip + ":" + hello.port);
        connectTo(hello.source, hello.ip, hello.port);
    }

    private String readPeerId(Socket socket) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            String line = r.readLine();
            return (line != null && !line.isEmpty()) ? line.trim() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void startReader(final String peerId, final Socket socket) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedReader r = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                    String line;
                    while ((line = r.readLine()) != null) {
                        routeP2pMessage(line);
                    }
                } catch (IOException e) {
                    BaseMod.logger.info("P2PManager " + peerId.substring(0, 8) + " disconnected");
                } finally {
                    connections.remove(peerId);
                    try { socket.close(); } catch (IOException ignored) {}
                }
            }
        }, "P2P-Reader-" + peerId.substring(0, 8)).start();
    }

    private void routeP2pMessage(String line) {
        try {
            CrossSpireMod.messageRouter.route(line);
        } catch (Exception e) {
            BaseMod.logger.error("P2PManager route error: " + e.getMessage());
        }
    }

    public boolean hasDirectConnection(String peerId) {
        return isDirect(peerId);
    }

    public void stop() {
        running = false;
        for (Socket s : connections.values()) {
            try { s.close(); } catch (IOException ignored) {}
        }
        connections.clear();
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
    }

    public int getPort() { return listenPort; }
    public int connectionCount() { return connections.size(); }
}
