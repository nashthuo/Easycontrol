# 易控使用说明

## 准备

1. 被控端打开无线ADB调试，端口为5555
``` shell
// root用户：
adb shell
su
echo "sleep 5 && setprop service.adb.tcp.port 5555 && stop adbd && start adbd" > /data/adb/service.d/adb.sh && chmod +x /data/adb/service.d/adb.sh
// 无root：（先有线连接adb）
adb tcpip 5555
```
2. 被控端安装易控(可选)
	- 如主备控端分辨率相同，或在添加设备时选择不修改分辨率则可以不在被控端安装投屏

## 软件使用

1. 简单使用
	- 主控端安装易控，打开软件进行悬浮窗授权，添加设备，点击添加后的设备，被控端同意永久调试，开始投屏。
2. 高级使用
	- 在添加设备时设置高级选项，可自定义编解码参数、开启自动修改被控端分辨率等。
3. 工具栏
	- 在小窗模式下点击上横条，可查看工具栏，包括发送导航按钮命令，发送电源键，最小化、全屏、关闭功能。
	- 在全屏模式下点击导航栏更多按钮，可查看工具栏，包括发送电源键，最小化、小窗、关闭、隐藏导航栏功能。
4. 小窗模式使用
	- 可通过拖动横条移动小窗。
	- 拖动右下角可更改小窗大小。
5. 最小化模式使用
	- 可上下拖动
	- 单机返回小窗模式

## Automation (Tasker)

In Easycontrol EN, open Settings and enable **Allow automation commands (Tasker)** under **Other**. The switch is off by default. When it is on, any app on the tablet can send these commands to the receiver. Grant overlay permission in Easycontrol EN before you use OPEN from the background.

Run these commands against the tablet. Replace `Redmi` with your saved device name.

```sh
adb shell am broadcast -n io.github.nashthuo.easycontrol/top.saymzx.easycontrol.app.CommandReceiver -a io.github.nashthuo.easycontrol.action.OPEN --es device Redmi
adb shell am broadcast -n io.github.nashthuo.easycontrol/top.saymzx.easycontrol.app.CommandReceiver -a io.github.nashthuo.easycontrol.action.KEY --es device Redmi --es key home
adb shell am broadcast -n io.github.nashthuo.easycontrol/top.saymzx.easycontrol.app.CommandReceiver -a io.github.nashthuo.easycontrol.action.CLOSE --es device Redmi
```

In Tasker, add **Send Intent** and set these fields. [Tasker intent guide](https://tasker.joaoapps.com/userguide/en/intents.html)

| Field | Value |
| --- | --- |
| Action | `io.github.nashthuo.easycontrol.action.OPEN`, `io.github.nashthuo.easycontrol.action.KEY`, or `io.github.nashthuo.easycontrol.action.CLOSE` |
| Package | `io.github.nashthuo.easycontrol` |
| Class | `top.saymzx.easycontrol.app.CommandReceiver` |
| Target | **Broadcast Receiver** |
| Extra | `device:Redmi`, or `device_id:1` with the saved device ID |
| Extra (KEY only) | `key:home`, or `keycode:3` with the Android key code |

- Names match without regard to case. Use `device_id` if more than one saved device has the same name.
- Without a device extra, OPEN and KEY use the default device. CLOSE closes all open mirrors.
- Send KEY after the mirror connects. The key names are `home`, `back`, `recents`, `volume_up`, `volume_down`, and `wake`. The receiver ignores `power` and key code `26`.
