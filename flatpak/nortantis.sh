#!/bin/sh
# Launcher for the Flatpak build. JVM options mirror the Linux .deb installer
# (create_ubuntu_installer.sh) plus the native-access flag from build.gradle.kts.
exec /app/jre/bin/java \
	--enable-native-access=ALL-UNNAMED \
	-XX:MaxRAMPercentage=50.0 \
	-XX:MaxHeapFreeRatio=40 \
	-XX:MinHeapFreeRatio=20 \
	-XX:G1PeriodicGCInterval=15000 \
	-Dfile.encoding=UTF-8 \
	-jar /app/share/nortantis/Nortantis.jar "$@"
