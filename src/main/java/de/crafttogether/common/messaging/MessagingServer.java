package de.crafttogether.common.messaging;

import de.crafttogether.CTCommons;
import de.crafttogether.common.event.Event;
import de.crafttogether.common.messaging.events.ConnectionErrorEvent;
import de.crafttogether.common.messaging.events.PacketReceivedEvent;
import de.crafttogether.common.messaging.packets.*;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static de.crafttogether.common.messaging.ConnectionError.NO_REMOTE_CONNECTIONS;

public class MessagingServer extends Thread {
    private static String host;
    private static int port;
    private static String secretKey;
    private static boolean acceptRemoteConnections;

    private static ArrayList<ClientConnection> clientsList;
    private boolean listen;
    private ServerSocket serverSocket;

    protected MessagingServer(String host, int port, String secretKey, boolean acceptRemoteConnections) {
        this.setName(CTCommons.getPluginInformation().getName() + " network thread");
        MessagingServer.host = host;
        MessagingServer.port = port;
        MessagingServer.secretKey = secretKey;
        MessagingServer.acceptRemoteConnections = acceptRemoteConnections;
        start();
    }

    @Override
    public void run() {
        clientsList = new ArrayList<>();

        try {
            // Create ServerSocket
            serverSocket = new ServerSocket(port, 5, InetAddress.getByName(host));
            listen = true;

            CTCommons.debug("[MessagingServer]: Server is listening on port " + port, false);

            // Handle incoming connections
            while (listen && !isInterrupted()) {
                Socket connection = null;

                try {
                    connection = serverSocket.accept();
                } catch (SocketException e) {
                    CTCommons.debug(e.getMessage());
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (connection == null)
                    continue;

                ClientConnection client = new ClientConnection(connection);
                CTCommons.debug("[MessagingServer]: " + client.getClientName() + " connected.", false);

                // Should we accept remote connections?
                if (!acceptRemoteConnections && !client.getAddress().equals("127.0.0.1"))
                    client.kick(NO_REMOTE_CONNECTIONS);

                CTCommons.debug("[MessagingServer]: Starting network-thread...", false);
                clientsList.add(client);
                client.read();
            }
        } catch (BindException e) {
            CTCommons.getLogger().warn("[MessagingServer]: Can't bind to " + port + ".. Port already in use!");
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        finally {
            close();
            CTCommons.debug("[MessagingServer]: Server stopped.", false);
        }
    }

    public void send(Packet packet) {

        // Broadcast
        if (packet.getBroadcast()) {
            packet.setRecipients(new ArrayList<>());

            for (ClientConnection clientConnection : getRegisteredClients())
                packet.addRecipient(clientConnection.getClientName());

            for (ClientConnection clientConnection : getRegisteredClients()) {
                if (clientConnection.getClientName().equals(packet.getSender())) continue;
                clientConnection.send(packet);
            }
        }

        // Adressed
        else {
            for (String recipient : packet.getRecipients()) {
                ClientConnection client = getClient(recipient);
                if (client == null) {
                    CTCommons.getLogger().warn("[MessagingService]: Unkown recipient (" + recipient + ") for #" + packet.getClass().getSimpleName() + " sendet by " + packet.getSender());
                    packet.removeRecipient(recipient);
                }
            }

            for (String recipient : packet.getRecipients())
                getClient(recipient).send(packet);
        }
    }

    public static ClientConnection getClient(String clientName) {
        return clientsList.stream().filter(clientConnection -> clientConnection.getClientName().equals(clientName)).findAny().orElse(null);
    }

    public static ArrayList<ClientConnection> getClients() {
        return clientsList;
    }

    public static List<ClientConnection> getRegisteredClients() {
        return clientsList.stream().filter(AbstractConnection::isAuthenticated).collect(Collectors.toList());
    }

    public void close() {
        if (!listen) return;
        listen = false;

        for (ClientConnection client : clientsList)
            client.disconnect();

        if (serverSocket != null) {
            try {
                serverSocket.close();
                serverSocket = null;
            }
            catch (IOException e) { e.printStackTrace(); }
        }
    }

    public static class ClientConnection extends AbstractConnection {
        protected ClientConnection(Socket connection) {
            super(connection);

            CTCommons.getRunnableFactory().create(() -> {
                if (!isAuthenticated())
                    kick(ConnectionError.NOT_AUTHENTICATED);
            }).runTaskLaterAsynchronously(80L);
        }

        @Override
        public void onPacketReceived(Packet abstractPacket) {
            // First packet has to be an AuthenticationPacket
            if (abstractPacket instanceof ErrorPacket packet) {
                Event event = new ConnectionErrorEvent(packet.getError(), getAddress(), getPort());
                CTCommons.debug("[MessagingClient]: Error: " + packet.getError().name());
                CTCommons.getRunnableFactory().create(() -> CTCommons.getEventManager().callEvent(event)).runTask();
            }

            else if (!isAuthenticated() && abstractPacket instanceof AuthenticationPacket packet) {
                if (packet.getClientName() != null && packet.getSecretKey() != null && packet.getSecretKey().equals(secretKey)) {
                    setClientName(packet.getClientName());

                    // Announce new connected server to other connections
                    for (ClientConnection clientConnection : clientsList) {
                        if (!clientConnection.isAuthenticated()) continue;
                        send(new ServerConnectedPacket(packet.getClientName())
                                .setBroadcast(true)
                                .setSender("proxy"));
                    }

                    isAuthenticated(true);
                    send(new AuthenticationSuccessPacket()
                            .addRecipient(getClientName())
                            .setSender("proxy"));

                    CTCommons.debug("[MessagingClient]: Client (" + getClientName() + ") sucessfully authenticated.", false);
                }
                else
                    kick(ConnectionError.INVALID_AUTHENTICATION);
            }

            else if (isAuthenticated()) {
                CTCommons.debug(abstractPacket.getClass().getSimpleName());
                Event event = new PacketReceivedEvent(getConnection(), abstractPacket);
                CTCommons.getRunnableFactory().create(() -> CTCommons.getEventManager().callEvent(event)).runTask();
            }

            else
                kick(ConnectionError.NOT_AUTHENTICATED);
        }

        @Override
        public void onDisconnect() {
            clientsList.remove(this);

            // Announce disconnected server to other connections
            for (ClientConnection clientConnection : clientsList) {
                if (!clientConnection.isAuthenticated()) continue;
                send(new ServerDisconnectedPacket(getClientName())
                        .setBroadcast(true)
                        .setSender("proxy"));
            }
        }

        public void kick(ConnectionError reason) {
            CTCommons.debug("[MessagingServer]: " + getClientName() + " was kicked (" + reason + ").", false);
            send(new ErrorPacket(reason)
                    .addRecipient(getClientName())
                    .setSender("proxy"));
            disconnect();
        }
    }
}