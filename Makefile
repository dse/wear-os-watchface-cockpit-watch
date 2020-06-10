ADB = adb -s TKQ7N18112000190
PACKAGE_NAME = com.webonastick.watchface.avionics
APK_FILENAME = app/build/outputs/apk/debug/app-debug.apk

install:
	$(ADB) uninstall $(PACKAGE_NAME) || true
	$(ADB) install $(APK_FILENAME)
reinstall:
	$(ADB) install -r $(APK_FILENAME)
uninstall:
	$(ADB) uninstall $(PACKAGE_NAME)
