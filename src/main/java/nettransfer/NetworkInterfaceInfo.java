package nettransfer;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NetworkInterfaceInfo {
    public final String name;
    public final String displayName;
    public final String ipAddress;
    public final String broadcastAddress;
    public final int prefixLength;
    public final boolean isUp;
    public final boolean isLoopback;
    public final boolean isVirtual;
    public final boolean supportsBroadcast;
    public final int mtu;
    public final String macAddress;

    public NetworkInterfaceInfo(String name, String displayName, String ipAddress, String broadcastAddress,
                                 int prefixLength, boolean isUp, boolean isLoopback, boolean isVirtual,
                                 boolean supportsBroadcast, int mtu, String macAddress) {
        this.name = name;
        this.displayName = displayName;
        this.ipAddress = ipAddress;
        this.broadcastAddress = broadcastAddress;
        this.prefixLength = prefixLength;
        this.isUp = isUp;
        this.isLoopback = isLoopback;
        this.isVirtual = isVirtual;
        this.supportsBroadcast = supportsBroadcast;
        this.mtu = mtu;
        this.macAddress = macAddress;
    }

    /** One entry per usable IPv4 address (up, not loopback, has a broadcast address). */
    public static List<NetworkInterfaceInfo> enumerate() {
        List<NetworkInterfaceInfo> result = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String mac = formatMac(ni);
                int mtu = safeMtu(ni);
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress addr = ia.getAddress();
                    InetAddress bcast = ia.getBroadcast();
                    if (addr == null || addr.getAddress().length != 4) continue; // IPv4 only
                    result.add(new NetworkInterfaceInfo(
                            ni.getName(), ni.getDisplayName(), addr.getHostAddress(),
                            bcast != null ? bcast.getHostAddress() : null,
                            ia.getNetworkPrefixLength(), true, false, ni.isVirtual(),
                            bcast != null, mtu, mac));
                }
            }
        } catch (SocketException ignored) {
            // no interfaces available right now — return what we have (possibly empty)
        }
        return result;
    }

    private static String formatMac(NetworkInterface ni) {
        try {
            byte[] mac = ni.getHardwareAddress();
            if (mac == null) return "—";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mac.length; i++) {
                if (i > 0) sb.append(':');
                sb.append(String.format("%02X", mac[i]));
            }
            return sb.toString();
        } catch (SocketException e) {
            return "—";
        }
    }

    private static int safeMtu(NetworkInterface ni) {
        try {
            return ni.getMTU();
        } catch (SocketException e) {
            return -1;
        }
    }
}
