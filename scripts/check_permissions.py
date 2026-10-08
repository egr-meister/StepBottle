#!/usr/bin/env python3
"""Fail if a packaged artifact or merged manifest requests a permission outside the allowlist.

Usage:
  check_permissions.py apk <aapt2> <apk> <applicationId>
  check_permissions.py manifest <AndroidManifest.xml> <applicationId>
(For an AAB, dump the base manifest with `bundletool dump manifest` and use the manifest mode.)
"""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
FORBIDDEN = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACTIVITY_RECOGNITION",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.BODY_SENSORS",
    "android.permission.SCHEDULE_EXACT_ALARM",
    "android.permission.USE_EXACT_ALARM",
    "android.permission.WAKE_LOCK",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
}


def allowed(app_id):
    return {
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.RECEIVE_BOOT_COMPLETED",
        # Signature-level permission AndroidX core adds for non-exported dynamic receivers.
        f"{app_id}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    }


def from_apk(aapt2, apk):
    out = subprocess.run([aapt2, "dump", "permissions", apk], check=True, capture_output=True, text=True).stdout
    print(out)
    return set(re.findall(r"uses-permission(?:-sdk-23)?: name='([^']+)'", out))


def from_manifest(path):
    root = ET.parse(path).getroot()
    tags = ("uses-permission", "uses-permission-sdk-23")
    return {e.get(ANDROID_NS + "name") for t in tags for e in root.iter(t)}


mode = sys.argv[1]
if mode == "apk":
    requested, app_id = from_apk(sys.argv[2], sys.argv[3]), sys.argv[4]
elif mode == "manifest":
    requested, app_id = from_manifest(sys.argv[2]), sys.argv[3]
else:
    sys.exit(f"unknown mode {mode}")

unexpected = requested - allowed(app_id)
bad = requested & FORBIDDEN
if unexpected or bad:
    print(f"[{mode}] Unexpected permissions:", sorted(unexpected | bad))
    sys.exit(1)
print(f"[{mode}] Permissions OK:", sorted(requested))
