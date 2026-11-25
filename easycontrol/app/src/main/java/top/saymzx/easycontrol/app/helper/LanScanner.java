package top.saymzx.easycontrol.app.helper;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class LanScanner {
  private static final int ADB_PORT = 5555;
  private static final int CONNECT_TIMEOUT_MS = 400;
  private static final int SCAN_TIMEOUT_MS = 5000;
  private static final int WORKER_COUNT = 32;

  // A null result means the active network has no Wi-Fi IPv4 subnet to scan.
  static List<String> findHosts(Context context) {
    ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
    if (manager == null) return null;
    Network network = manager.getActiveNetwork();
    if (network == null) return null;
    NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
    if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null;
    LinkProperties properties = manager.getLinkProperties(network);
    if (properties == null) return null;

    for (LinkAddress linkAddress : properties.getLinkAddresses()) {
      InetAddress address = linkAddress.getAddress();
      if (address instanceof Inet4Address) return scanSubnet(network, address.getAddress());
    }
    return null;
  }

  private static List<String> scanSubnet(Network network, byte[] address) {
    List<Callable<String>> probes = new ArrayList<>(254);
    for (int host = 1; host <= 254; host++) {
      byte[] target = {address[0], address[1], address[2], (byte) host};
      probes.add(() -> probeHost(network, target));
    }

    List<String> hosts = new ArrayList<>();
    ExecutorService workers = Executors.newFixedThreadPool(WORKER_COUNT);
    try {
      for (Future<String> probe : workers.invokeAll(probes, SCAN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        if (probe.isCancelled()) continue;
        try {
          String host = probe.get();
          if (host != null) hosts.add(host);
        } catch (ExecutionException ignored) {
          // A failed probe does not prevent the other hosts from being reported.
        }
      }
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    } finally {
      workers.shutdownNow();
    }
    return hosts;
  }

  private static String probeHost(Network network, byte[] target) {
    try (Socket socket = network.getSocketFactory().createSocket()) {
      InetAddress host = InetAddress.getByAddress(target);
      socket.connect(new InetSocketAddress(host, ADB_PORT), CONNECT_TIMEOUT_MS);
      return host.getHostAddress() + ":" + ADB_PORT;
    } catch (IOException ignored) {
      return null;
    }
  }
}
