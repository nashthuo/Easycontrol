package top.saymzx.easycontrol.app.client.view;

import android.view.KeyEvent;

import top.saymzx.easycontrol.app.client.Controller;
import top.saymzx.easycontrol.app.databinding.MirrorKeyButtonsBinding;

final class MirrorKeys {
  private MirrorKeys() {
  }

  static void bind(MirrorKeyButtonsBinding buttons, Controller controller) {
    buttons.buttonVolumeUp.setOnClickListener(v -> controller.sendKeyEvent(KeyEvent.KEYCODE_VOLUME_UP));
    buttons.buttonVolumeDown.setOnClickListener(v -> controller.sendKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN));
    buttons.buttonPower.setOnClickListener(v -> controller.sendKeyEvent(KeyEvent.KEYCODE_POWER));
    buttons.buttonWake.setOnClickListener(v -> controller.sendKeyEvent(KeyEvent.KEYCODE_WAKEUP));
  }
}
