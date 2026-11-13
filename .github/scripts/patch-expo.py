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
                # Ensure Swift 6 mode is preserved
                if "swiftLanguageModes: [.v5]" in content:
                    content = content.replace("swiftLanguageModes: [.v5]", "swiftLanguageModes: [.v6]")
                changed = True
            if changed:
                with open(pkg_path, "w", encoding="utf-8") as f:
                    f.write(content)
                print(f"Patched: {pkg_path}")
        except Exception as e:
            print(f"Error patching {pkg_path}: {e}")

    # 2. Patch JavaScriptCallback.swift to convert local 'func call' into Sendable closures
    js_cb = "node_modules/expo-modules-jsi/apple/Sources/ExpoModulesJSI/Runtime/JavaScriptCallback.swift"
    if os.path.exists(js_cb):
        try:
            with open(js_cb, "r", encoding="utf-8") as f:
                cb_content = f.read()
            # Clean up any previously inserted @Sendable func call
            cb_content = cb_content.replace("@Sendable func call", "func call")

            # Replace 4 void-returning local functions with closures
            pattern_void = r'@JavaScriptActor\s+func call\(_ function: borrowing JavaScriptFunction, _ runtime: JavaScriptRuntime\) throws\s*\{'
            replace_void = 'let call = { @JavaScriptActor (function: borrowing JavaScriptFunction, runtime: JavaScriptRuntime) throws in'
            cb_content, n_void = re.subn(pattern_void, replace_void, cb_content)

            # Replace 3 box-returning local functions with closures
            pattern_box = r'@JavaScriptActor\s+func call\(_ function: borrowing JavaScriptFunction, _ runtime: JavaScriptRuntime\) throws -> UncheckedSendableBox<R>\s*\{'
            replace_box = 'let call = { @JavaScriptActor (function: borrowing JavaScriptFunction, runtime: JavaScriptRuntime) throws -> UncheckedSendableBox<R> in'
            cb_content, n_box = re.subn(pattern_box, replace_box, cb_content)

            with open(js_cb, "w", encoding="utf-8") as f:
                f.write(cb_content)
            print(f"Successfully converted local functions in JavaScriptCallback.swift to closures (void: {n_void}, box: {n_box})")
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
