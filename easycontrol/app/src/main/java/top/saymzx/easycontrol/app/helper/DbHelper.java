package top.saymzx.easycontrol.app.helper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

import top.saymzx.easycontrol.app.entity.Device;

public class DbHelper extends SQLiteOpenHelper {
  private static final String TABLE = "DevicesDb";
  private static final int VERSION = 2;

  public DbHelper(Context context) {
    super(context, "app.db", null, VERSION);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE " + TABLE + " (id INTEGER PRIMARY KEY AUTOINCREMENT,"
      + " name TEXT, address TEXT, isAudio INTEGER, maxSize INTEGER, maxFps INTEGER,"
      + " maxVideoBit INTEGER, setResolution INTEGER, fallbackAddress TEXT NOT NULL DEFAULT '')");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    if (oldVersion < 2)
      db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN fallbackAddress TEXT NOT NULL DEFAULT ''");
  }

  public ArrayList<Device> getAll() {
    ArrayList<Device> devices = new ArrayList<>();
    try (Cursor cursor = getReadableDatabase().query(TABLE, null, null, null, null, null, null)) {
      while (cursor.moveToNext()) devices.add(getDeviceFromCursor(cursor));
    }
    return devices;
  }

  public Device getById(int id) {
    try (Cursor cursor = getReadableDatabase().query(TABLE, null, "id = ?",
      new String[]{String.valueOf(id)}, null, null, null)) {
      return cursor.moveToFirst() ? getDeviceFromCursor(cursor) : null;
    }
  }

  public void insert(Device device) {
    getWritableDatabase().insert(TABLE, null, getValues(device));
  }

  public void update(Device device) {
    getWritableDatabase().update(TABLE, getValues(device), "id = ?", new String[]{String.valueOf(device.id)});
  }

  public void delete(Device device) {
    getWritableDatabase().delete(TABLE, "id = ?", new String[]{String.valueOf(device.id)});
  }

  private ContentValues getValues(Device device) {
    ContentValues values = new ContentValues();
    values.put("name", device.name);
    values.put("isAudio", device.isAudio);
    values.put("address", device.address);
    values.put("fallbackAddress", device.fallbackAddress);
    values.put("maxSize", device.maxSize);
    values.put("maxFps", device.maxFps);
    values.put("maxVideoBit", device.maxVideoBit);
    values.put("setResolution", device.setResolution);
    return values;
  }

  private Device getDeviceFromCursor(Cursor cursor) {
    String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
    return new Device(
      cursor.getInt(cursor.getColumnIndexOrThrow("id")),
      cursor.getString(cursor.getColumnIndexOrThrow("name")),
      address == null ? "" : address,
      cursor.getString(cursor.getColumnIndexOrThrow("fallbackAddress")),
      cursor.getInt(cursor.getColumnIndexOrThrow("isAudio")) == 1,
      cursor.getInt(cursor.getColumnIndexOrThrow("maxSize")),
      cursor.getInt(cursor.getColumnIndexOrThrow("maxFps")),
      cursor.getInt(cursor.getColumnIndexOrThrow("maxVideoBit")),
      cursor.getInt(cursor.getColumnIndexOrThrow("setResolution")) == 1);
  }
}
