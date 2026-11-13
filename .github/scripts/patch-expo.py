#!/usr/bin/env python3
import glob
import os
import re
import sys

def patch_expo():
    base_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    ios_dir = os.path.join(base_dir, "ios")
    if not os.path.exists(ios_dir):
        ios_dir = os.getcwd()

    print(f"Patching Expo packages in: {ios_dir}")
    os.chdir(ios_dir)

    # 1. Patch all Package.swift files requiring swift-tools-version: 6.2 down to 6.0
    for pkg_path in glob.glob("node_modules/**/Package.swift", recursive=True):
        try:
            with open(pkg_path, "r", encoding="utf-8") as f:
                content = f.read()
            changed = False
            if "swift-tools-version: 6.2" in content:
                content = content.replace("swift-tools-version: 6.2", "swift-tools-version: 6.0")
                changed = True
            if "expo-modules-jsi" in pkg_path:
                # Remove Swift 6.2 upcoming features
                content = re.sub(r'\s*\.enableUpcomingFeature\("NonisolatedNonsendingByDefault"\),?', '', content)
                content = re.sub(r'\s*\.enableUpcomingFeature\("InferIsolatedConformances"\),?', '', content)
                # Set language mode to v5 to avoid strict concurrency blocking errors
                if "swiftLanguageModes: [.v6]" in content:
                    content = content.replace("swiftLanguageModes: [.v6]", "swiftLanguageModes: [.v5]")
                changed = True
            if changed:
                with open(pkg_path, "w", encoding="utf-8") as f:
                    f.write(content)
                print(f"Patched: {pkg_path}")
        except Exception as e:
            print(f"Error patching {pkg_path}: {e}")

    # 2. Patch JavaScriptCallback.swift to mark call closures as @Sendable
    js_cb = "node_modules/expo-modules-jsi/apple/Sources/ExpoModulesJSI/Runtime/JavaScriptCallback.swift"
    if os.path.exists(js_cb):
        try:
            with open(js_cb, "r", encoding="utf-8") as f:
                cb_content = f.read()
            # Replace @JavaScriptActor followed by func call with @JavaScriptActor followed by @Sendable func call
            cb_content = re.sub(r'(@JavaScriptActor\s+)func call', r'\1@Sendable func call', cb_content)
            with open(js_cb, "w", encoding="utf-8") as f:
                f.write(cb_content)
            print("Successfully patched JavaScriptCallback.swift (@Sendable)")
        except Exception as e:
            print(f"Error patching {js_cb}: {e}")
    else:
        print(f"Note: {js_cb} not found on disk, skipping.")

if __name__ == "__main__":
    patch_expo()
