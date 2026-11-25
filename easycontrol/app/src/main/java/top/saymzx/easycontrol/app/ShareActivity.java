package top.saymzx.easycontrol.app;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.Parcelable;
import android.widget.Toast;

import java.util.ArrayList;

import top.saymzx.easycontrol.app.entity.AppData;
import top.saymzx.easycontrol.app.entity.Device;
import top.saymzx.easycontrol.app.helper.FileSender;

public class ShareActivity extends EnglishActivity {
  private static final String STATE_SENDING = "sending_file";

  private AlertDialog dialog;
  private Transfer transfer;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    transfer = (Transfer) getLastNonConfigurationInstance();
    if (transfer != null) {
      transfer.attach(this);
      return;
    }
    if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_SENDING)) {
      finishWithMessage(R.string.file_send_interrupted);
      return;
    }
    Uri uri = getSharedFile(getIntent());
    if (uri == null) {
      finishWithMessage(R.string.file_send_read_failed);
      return;
    }
    AppData.init(this);
    ArrayList<Device> devices = AppData.dbHelper.getAll();
    if (devices.isEmpty()) {
      finishWithMessage(R.string.file_send_no_devices);
    } else if (devices.size() == 1) {
      send(devices.get(0), uri);
    } else {
      String[] names = new String[devices.size()];
      for (int i = 0; i < names.length; i++) names[i] = devices.get(i).name;
      dialog = new AlertDialog.Builder(this)
        .setTitle(R.string.file_send_choose_device)
        .setItems(names, (dialog, index) -> send(devices.get(index), uri))
        .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
        .setOnCancelListener(dialog -> finish())
        .show();
    }
  }

  private static Uri getSharedFile(Intent intent) {
    if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return null;
    try {
      Parcelable stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
      if (!(stream instanceof Uri)) return null;
      Uri uri = (Uri) stream;
      return ContentResolver.SCHEME_CONTENT.equals(uri.getScheme()) ? uri : null;
    } catch (BadParcelableException e) {
      return null;
    }
  }

  private void send(Device device, Uri uri) {
    transfer = new Transfer();
    transfer.attach(this);
    FileSender.send(this, device, uri, transfer::complete);
  }

  private void showProgress() {
    dialog = new AlertDialog.Builder(this)
      .setMessage(R.string.file_send_progress)
      .setCancelable(false)
      .show();
  }

  private void finishWithMessage(int message) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    finish();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(STATE_SENDING, transfer != null);
  }

  @Override
  public Object onRetainNonConfigurationInstance() {
    return transfer;
  }

  @Override
  protected void onDestroy() {
    if (dialog != null) dialog.dismiss();
    if (transfer != null && transfer.activity == this) transfer.activity = null;
    super.onDestroy();
  }

  // Keep the activity's temporary URI grant until the background transfer completes.
  private static final class Transfer {
    private ShareActivity activity;
    private boolean complete;

    void attach(ShareActivity activity) {
      this.activity = activity;
      if (complete) {
        activity.finish();
      } else {
        activity.showProgress();
      }
    }

    void complete() {
      complete = true;
      if (activity != null) activity.finish();
    }
  }
}
