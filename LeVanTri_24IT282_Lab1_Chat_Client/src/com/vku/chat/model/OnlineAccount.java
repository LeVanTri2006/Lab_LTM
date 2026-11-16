package com.vku.chat.model;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;

public record OnlineAccount(String username, String fullName, String ip) implements Serializable {
    public boolean matchesIp(String address) {
        if (ip.equalsIgnoreCase(address)) return true;

        // Accept compressed IPv6 literals as well as the form displayed by the server.
        if (!ip.contains(":") || !address.contains(":") || !address.matches("[0-9a-fA-F:.]+")) {
            return false;
        }
        try {
            return InetAddress.getByName(ip.split("%", 2)[0]).equals(InetAddress.getByName(address));
        } catch (UnknownHostException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return fullName + " (@" + username + ")";
    }
}
