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
                # Keep Swift 6 language mode (.v6) for C++ interop, consuming, and Task.immediate
                if "swiftLanguageModes: [.v5]" in content:
                    content = content.replace("swiftLanguageModes: [.v5]", "swiftLanguageModes: [.v6]")
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
            cb_content = re.sub(r'(@JavaScriptActor\s+)func call', r'\1@Sendable func call', cb_content)
            with open(js_cb, "w", encoding="utf-8") as f:
                f.write(cb_content)
            print("Successfully patched JavaScriptCallback.swift (@Sendable)")
        except Exception as e:
            print(f"Error patching {js_cb}: {e}")

    # 3. Patch JavaScriptRuntime.swift bare slash regex literal
    js_rt = "node_modules/expo-modules-jsi/apple/Sources/ExpoModulesJSI/Runtime/JavaScriptRuntime.swift"
    if os.path.exists(js_rt):
        try:
            with open(js_rt, "r", encoding="utf-8") as f:
                rt_content = f.read()
            target_regex = "name.wholeMatch(of: /^[a-zA-Z_$][a-zA-Z0-9_$]*$/)"
            replacement_regex = 'name.wholeMatch(of: try! Regex(#"^[a-zA-Z_$][a-zA-Z0-9_$]*$"#))'
            if target_regex in rt_content:
                rt_content = rt_content.replace(target_regex, replacement_regex)
                with open(js_rt, "w", encoding="utf-8") as f:
                    f.write(rt_content)
                print("Successfully patched JavaScriptRuntime.swift regex literal")
            else:
                print("Note: regex literal in JavaScriptRuntime.swift already patched or not found")
        except Exception as e:
            print(f"Error patching {js_rt}: {e}")

    # 4. Patch JavaScriptPromise.swift LongLivedState initializer for nonisolated context
    js_promise = "node_modules/expo-modules-jsi/apple/Sources/ExpoModulesJSI/Runtime/Values/JavaScriptPromise.swift"
    if os.path.exists(js_promise):
        try:
            with open(js_promise, "r", encoding="utf-8") as f:
                p_content = f.read()
            target_class = "private final class LongLivedState: LongLivedObject {"
            replacement_class = "private final class LongLivedState: LongLivedObject {\n    nonisolated init() {}"
            if target_class in p_content and "nonisolated init() {}" not in p_content:
                p_content = p_content.replace(target_class, replacement_class)
                with open(js_promise, "w", encoding="utf-8") as f:
                    f.write(p_content)
                print("Successfully patched JavaScriptPromise.swift (nonisolated init)")
            else:
                print("Note: LongLivedState in JavaScriptPromise.swift already patched or not found")
        except Exception as e:
            print(f"Error patching {js_promise}: {e}")

if __name__ == "__main__":
    patch_expo()
