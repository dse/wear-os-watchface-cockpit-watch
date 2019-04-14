install:
# 10c6c22e device
	adb -s 10c6c22e uninstall com.webonastick.watchface.avionics || true
	adb -s 10c6c22e install ./app/build/outputs/apk/debug/app-debug.apk
