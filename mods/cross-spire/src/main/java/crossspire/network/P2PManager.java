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
    private final String advertisedIp;
    private ServerSocket serverSocket;
    private final Map<String, Socket> connections = new ConcurrentHashMap<String, Socket>();
    private boolean running = false;

    public P2PManager() {
        java.util.Properties cfg = loadConfig();
        String portProp = System.getProperty("crossspire.p2p.port");
        if (portProp == null) portProp = cfg.getProperty("crossspire.p2p.port");
        listenPort = portProp != null ? Integer.parseInt(portProp) : DEFAULT_PORT;
        String ip = System.getProperty("crossspire.p2p.ip");
        if (ip == null) ip = cfg.getProperty("crossspire.p2p.ip");
        advertisedIp = ip != null ? ip : "127.0.0.1";
    }

    private static java.util.Properties loadConfig() {
        java.util.Properties p = new java.util.Properties();
        try {
            java.io.File f = new java.io.File("/storage/emulated/0/Android/data/io.stamethyst/files/sts/crossspire.properties");
            if (f.exists()) {
                java.io.FileInputStream fis = new java.io.FileInputStream(f);
                p.load(fis);
                fis.close();
            }
        } catch (Exception ignored) {}
        return p;
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

    public void send(String peerId, String message) {
        Socket s = connections.get(peerId);
        if (s == null || s.isClosed()) return;
        try {
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"));
            w.write(message + "\n");
            w.flush();
        } catch (IOException e) {
            BaseMod.logger.error("P2PManager send error: " + e.getMessage());
            connections.remove(peerId);
        }
    }

    public void broadcast(String message) {
        for (Map.Entry<String, Socket> entry : connections.entrySet()) {
            send(entry.getKey(), message);
        }
    }

    public void sendHello() {
        Protocol.HelloMessage hello = new Protocol.HelloMessage();
        hello.ip = advertisedIp;
        hello.port = listenPort;
        hello.source = CrossSpireMod.playerId;
        hello.seq = 1;

        java.util.List<Protocol.MemberInfo> peerList = new java.util.ArrayList<Protocol.MemberInfo>();
        for (Map.Entry<String, Socket> entry : connections.entrySet()) {
            Protocol.MemberInfo mi = new Protocol.MemberInfo();
            mi.id = entry.getKey();
            mi.ip = advertisedIp;
            mi.port = listenPort;
            peerList.add(mi);
        }
        hello.peers = peerList.toArray(new Protocol.MemberInfo[0]);

        CrossSpireMod.send(Protocol.GSON.toJson(hello));
        BaseMod.logger.info("P2PManager sent hello: " + advertisedIp + ":" + listenPort + " peers=" + peerList.size());
    }

    public void onHelloReceived(String rawMessage) {
        Protocol.HelloMessage hello = Protocol.GSON.fromJson(rawMessage, Protocol.HelloMessage.class);
        if (hello.source == null || hello.source.equals(CrossSpireMod.playerId)) return;
        if (!connections.containsKey(hello.source)) {
            BaseMod.logger.info("P2PManager hello from " + hello.source.substring(0, 8) + " @" + hello.ip + ":" + hello.port);
            connectTo(hello.source, hello.ip, hello.port);
        }

        if (hello.peers != null) {
            for (Protocol.MemberInfo peer : hello.peers) {
                if (peer.id == null || peer.id.equals(CrossSpireMod.playerId)) continue;
                if (connections.containsKey(peer.id)) continue;
                BaseMod.logger.info("P2PManager auto-connecting to peer: " + peer.id.substring(0, 8) + " @" + peer.ip + ":" + peer.port);
                connectTo(peer.id, peer.ip, peer.port);
            }
        }
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
