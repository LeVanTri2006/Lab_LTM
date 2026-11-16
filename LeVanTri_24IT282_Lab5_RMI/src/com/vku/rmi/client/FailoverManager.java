package com.vku.rmi.client;

import com.vku.rmi.service.IClientService;

import java.rmi.registry.LocateRegistry;

public class FailoverManager {
    public record ServerEndpoint(String name, String host, int port) {
        @Override
        public String toString() {
            return name + " · " + host + ":" + port;
        }
    }

    private final ServerEndpoint primary;
    private final ServerEndpoint secondary;
    private ServerEndpoint activeEndpoint;
    private IClientService activeService;

    public FailoverManager(String host1, int port1, String host2, int port2) {
        primary = new ServerEndpoint("SERVER_1", host1, port1);
        secondary = new ServerEndpoint("SERVER_2", host2, port2);
        activeEndpoint = primary;
    }

    public synchronized IClientService getService() {
        if (activeService != null) {
            try {
                activeService.getServerId();
                return activeService;
            } catch (Exception e) {
                activeService = null;
            }
        }
        if (connect(activeEndpoint) == null) connect(other(activeEndpoint));
        return activeService;
    }

    public synchronized IClientService switchServer() {
        ServerEndpoint requested = other(activeEndpoint);
        if (connect(requested) == null) return null;
        return activeService;
    }

    private IClientService connect(ServerEndpoint endpoint) {
        try {
            var registry = LocateRegistry.getRegistry(endpoint.host(), endpoint.port());
            var service = (IClientService) registry.lookup("ClientService");
            if (!endpoint.name().equals(service.getServerId())) return null;
            activeEndpoint = endpoint;
            activeService = service;
            return service;
        } catch (Exception e) {
            return null;
        }
    }

    private ServerEndpoint other(ServerEndpoint endpoint) {
        return endpoint.equals(primary) ? secondary : primary;
    }

    public synchronized ServerEndpoint getActiveEndpoint() {
        return activeEndpoint;
    }
}
