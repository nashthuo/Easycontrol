package top.saymzx.easycontrol.app;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.os.Bundle;
import android.util.Pair;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;

import top.saymzx.easycontrol.app.databinding.ActivityIpBinding;
import top.saymzx.easycontrol.app.databinding.ItemTextBinding;
import top.saymzx.easycontrol.app.entity.AppData;

public class IpActivity extends EnglishActivity {
  private ActivityIpBinding ipActivity;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ipActivity = ActivityIpBinding.inflate(this.getLayoutInflater());
    setContentView(ipActivity.getRoot());
    AppData.publicTools.setStatusAndNavBar(this);
    setButtonListener();
    drawUi();
  }

  private void drawUi() {
    Pair<ArrayList<String>, ArrayList<String>> listPair = getIp();
    Context context = this;
    for (String i : listPair.first) {
      ItemTextBinding text = AppData.publicTools.createTextCard(context, i, () -> {
        AppData.clipBoard.setPrimaryClip(ClipData.newPlainText(ClipDescription.MIMETYPE_TEXT_PLAIN, i));
        Toast.makeText(context, R.string.toast_copy, Toast.LENGTH_SHORT).show();
      });
      ipActivity.ipv4.addView(text.getRoot());
    }
    for (String i : listPair.second) {
      ItemTextBinding text = AppData.publicTools.createTextCard(context, i, () -> {
        AppData.clipBoard.setPrimaryClip(ClipData.newPlainText(ClipDescription.MIMETYPE_TEXT_PLAIN, i));
        Toast.makeText(context, R.string.toast_copy, Toast.LENGTH_SHORT).show();
      });
      ipActivity.ipv6.addView(text.getRoot());
    }
  }

  private Pair<ArrayList<String>, ArrayList<String>> getIp() {
    ArrayList<String> ipv4Addresses = new ArrayList<>();
    ArrayList<String> ipv6Addresses = new ArrayList<>();

    try {
      Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
      while (networkInterfaces.hasMoreElements()) {
        NetworkInterface networkInterface = networkInterfaces.nextElement();
        Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
        while (inetAddresses.hasMoreElements()) {
          InetAddress inetAddress = inetAddresses.nextElement();
          if (!inetAddress.isLoopbackAddress())
            if (inetAddress instanceof Inet4Address)
              ipv4Addresses.add(inetAddress.getHostAddress());
            else if (inetAddress instanceof Inet6Address && !inetAddress.isLinkLocalAddress())
              ipv6Addresses.add(inetAddress.getHostAddress());
        }
      }
    } catch (Exception ignored) {
    }
    return new Pair<>(ipv4Addresses, ipv6Addresses);
  }

  private void setButtonListener() {
    ipActivity.backButton.setOnClickListener(v -> finish());
  }

}
