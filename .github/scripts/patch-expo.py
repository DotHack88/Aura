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
                content = re.sub(r'\s*\.enableUpcomingFeature\("NonisolatedNonsendingByDefault"\),?', '', content)
                content = re.sub(r'\s*\.enableUpcomingFeature\("InferIsolatedConformances"\),?', '', content)
                if "swiftLanguageModes: [.v5]" in content:
                    content = content.replace("swiftLanguageModes: [.v5]", "swiftLanguageModes: [.v6]")
                changed = True
            if changed:
                with open(pkg_path, "w", encoding="utf-8") as f:
                    f.write(content)
                print(f"Patched: {pkg_path}")
        except Exception as e:
            print(f"Error patching {pkg_path}: {e}")

    # 2. Patch JavaScriptCallback.swift: convert local named 'func call' into closures
    js_cb = "node_modules/expo-modules-jsi/apple/Sources/ExpoModulesJSI/Runtime/JavaScriptCallback.swift"
    if os.path.exists(js_cb):
        try:
            with open(js_cb, "r", encoding="utf-8") as f:
                cb_content = f.read()
            cb_content = cb_content.replace("@Sendable func call", "func call")

            pattern_void = r'@JavaScriptActor\s+func call\(_ function: borrowing JavaScriptFunction, _ runtime: JavaScriptRuntime\) throws\s*\{'
            replace_void = 'let call = { @JavaScriptActor (function: borrowing JavaScriptFunction, runtime: JavaScriptRuntime) throws in'
            cb_content, n_void = re.subn(pattern_void, replace_void, cb_content)

            pattern_box = r'@JavaScriptActor\s+func call\(_ function: borrowing JavaScriptFunction, _ runtime: JavaScriptRuntime\) throws -> UncheckedSendableBox<R>\s*\{'
            replace_box = 'let call = { @JavaScriptActor (function: borrowing JavaScriptFunction, runtime: JavaScriptRuntime) throws -> UncheckedSendableBox<R> in'
            cb_content, n_box = re.subn(pattern_box, replace_box, cb_content)

            with open(js_cb, "w", encoding="utf-8") as f:
                f.write(cb_content)
            print(f"Successfully converted local functions in JavaScriptCallback.swift to closures (void: {n_void}, box: {n_box})")
        except Exception as e:
            print(f"Error patching {js_cb}: {e}")

    # 3. Patch JavaScriptRuntime.swift: regex literal & vector.push_back
    js_rt = "node_modules/expo-modules-jsi/apple/Sources/ExpoModulesJSI/Runtime/JavaScriptRuntime.swift"
    if os.path.exists(js_rt):
        try:
            with open(js_rt, "r", encoding="utf-8") as f:
                rt_content = f.read()
            target_regex = "name.wholeMatch(of: /^[a-zA-Z_$][a-zA-Z0-9_$]*$/)"
            replacement_regex = 'name.wholeMatch(of: try! Regex(#"^[a-zA-Z_$][a-zA-Z0-9_$]*$"#))'
            if target_regex in rt_content:
                rt_content = rt_content.replace(target_regex, replacement_regex)
            # `vector.push_back(consuming: propNameId)` is Swift 6.2+ syntax; Xcode 16.4 / Swift 6.1
            # rejects the `consuming:` label on the imported C++ `std::vector::push_back`
            # (error: extraneous argument label 'consuming:' in call). Both the plain
            # `push_back(propNameId)` form and the `push_back(consuming: propNameId)` form
            # appear across expo-modules-jsi versions, so normalize both to the C++ helper
            # `pushBackPropNameId` that performs the move internally.
            if "vector.push_back(consuming: propNameId)" in rt_content:
                rt_content = rt_content.replace(
                    "vector.push_back(consuming: propNameId)",
                    "vector = expo.pushBackPropNameId(vector, propNameId)"
                )
            if "vector.push_back(propNameId)" in rt_content:
                rt_content = rt_content.replace("vector.push_back(propNameId)", "vector = expo.pushBackPropNameId(vector, propNameId)")
            with open(js_rt, "w", encoding="utf-8") as f:
                f.write(rt_content)
            print("Successfully patched JavaScriptRuntime.swift (regex + vector.push_back)")
        except Exception as e:
            print(f"Error patching {js_rt}: {e}")

    # 4. Patch JavaScriptPromise.swift: nonisolated init()
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
        except Exception as e:
            print(f"Error patching {js_promise}: {e}")

    # 5. Patch Task+immediate.swift: replace iOS 26 Task.immediate with standard Task
    task_imm = "node_modules/expo-modules-jsi/apple/Sources/ExpoModulesJSI/Extensions/Task+immediate.swift"
    if os.path.exists(task_imm):
        try:
            with open(task_imm, "r", encoding="utf-8") as f:
                t_content = f.read()
            t_content = re.sub(
                r'if #available\(macOS 26\.0.*?return Task\(name: name, priority: \.high, operation: operation\)\s*\}',
                'return Task(priority: .high, operation: operation)',
                t_content,
                flags=re.DOTALL
            )
            with open(task_imm, "w", encoding="utf-8") as f:
                f.write(t_content)
            print("Successfully patched Task+immediate.swift")
        except Exception as e:
            print(f"Error patching {task_imm}: {e}")

    # 6. Patch HostFunctionClosure.h and RetainedSwiftPointer.h for C++ interop in Swift 6.1
    hfc = "node_modules/expo-modules-jsi/apple/Sources/ExpoModulesJSI-Cxx/include/HostFunctionClosure.h"
    if os.path.exists(hfc):
        try:
            with open(hfc, "r", encoding="utf-8") as f:
                hfc_content = f.read()
            hfc_content = hfc_content.replace("SWIFT_IMMORTAL_REFERENCE", "")
            target_hfc_ctor = "explicit HostFunctionClosure(Context context, Closure closure, Deallocator deallocator) : RetainedSwiftPointer(context, deallocator), _closure(closure) {};"
            replacement_hfc_ctor = """HostFunctionClosure(Context context, Closure *closure, Deallocator *deallocator) : RetainedSwiftPointer(context, deallocator), _closure(closure) {}
  HostFunctionClosure(HostFunctionClosure&&) = default;
  HostFunctionClosure& operator=(HostFunctionClosure&&) = default;"""
            if target_hfc_ctor in hfc_content:
                hfc_content = hfc_content.replace(target_hfc_ctor, replacement_hfc_ctor)
            with open(hfc, "w", encoding="utf-8") as f:
                f.write(hfc_content)
            print("Successfully patched HostFunctionClosure.h")
        except Exception as e:
            print(f"Error patching {hfc}: {e}")

    rsp = "node_modules/expo-modules-jsi/apple/Sources/ExpoModulesJSI-Cxx/include/RetainedSwiftPointer.h"
    if os.path.exists(rsp):
        try:
            with open(rsp, "r", encoding="utf-8") as f:
                rsp_content = f.read()
            rsp_content = rsp_content.replace("SWIFT_IMMORTAL_REFERENCE", "")
            target_rsp_ctor = "explicit RetainedSwiftPointer(Context context, Deallocator deallocator) : _context(context), _deallocator(std::move(deallocator)) {}"
            replacement_rsp_ctor = """RetainedSwiftPointer(Context context, Deallocator *deallocator) : _context(context), _deallocator(deallocator) {}
  RetainedSwiftPointer(RetainedSwiftPointer&&) = default;
  RetainedSwiftPointer& operator=(RetainedSwiftPointer&&) = default;"""
            if target_rsp_ctor in rsp_content:
                rsp_content = rsp_content.replace(target_rsp_ctor, replacement_rsp_ctor)
            with open(rsp, "w", encoding="utf-8") as f:
                f.write(rsp_content)
            print("Successfully patched RetainedSwiftPointer.h")
        except Exception as e:
            print(f"Error patching {rsp}: {e}")

# 7. Patch JSIUtils.h: import HostFunctionClosure by value instead of by pointer
    # The Swift side constructs a `expo.HostFunctionClosure` value and passes it to
    # `expo.createHostFunction`. Swift 6 / Xcode 16.4 imports the C++ `HostFunctionClosure *`
    # parameter as `UnsafeMutablePointer<expo.HostFunctionClosure>` and refuses to convert the
    # passed value to a pointer (error: cannot convert value of type 'expo.HostFunctionClosure'
    # to expected argument type 'UnsafeMutablePointer<expo.HostFunctionClosure>').
    # Taking the closure by value (and moving it into a `shared_ptr`) keeps the C++ lifetime
    # semantics identical while letting Swift pass the value directly.
    jsutils = "node_modules/expo-modules-jsi/apple/Sources/ExpoModulesJSI-Cxx/include/JSIUtils.h"
    if os.path.exists(jsutils):
        try:
            with open(jsutils, "r", encoding="utf-8") as f:
                u_content = f.read()
            u_changed = False
            target_u1 = "const jsi::PropNameID &propName, HostFunctionClosure *closure) {"
            replacement_u1 = "const jsi::PropNameID &propName, HostFunctionClosure closure) {"
            if target_u1 in u_content:
                u_content = u_content.replace(target_u1, replacement_u1)
                u_changed = True
            target_u2 = "auto closurePtr = std::shared_ptr<HostFunctionClosure>(closure);"
            replacement_u2 = "auto closurePtr = std::make_shared<HostFunctionClosure>(std::move(closure));"
            if target_u2 in u_content:
                u_content = u_content.replace(target_u2, replacement_u2)
                u_changed = True
            target_u3 = "const char *name, HostFunctionClosure *closure) {"
            replacement_u3 = "const char *name, HostFunctionClosure closure) {"
            if target_u3 in u_content:
                u_content = u_content.replace(target_u3, replacement_u3)
                u_changed = True
            target_u4 = "  return createHostFunction(runtime, propName, closure);"
            replacement_u4 = "  return createHostFunction(runtime, propName, std::move(closure));"
            if target_u4 in u_content:
                u_content = u_content.replace(target_u4, replacement_u4)
                u_changed = True
            if u_changed:
                with open(jsutils, "w", encoding="utf-8") as f:
                    f.write(u_content)
                print("Successfully patched JSIUtils.h (HostFunctionClosure by value)")
        except Exception as e:
            print(f"Error patching {jsutils}: {e}")

    # 7b. Patch JSIUtils.h: add pushBackPropNameId helper
    # `expo.HostObjectCallbacks::PropNameIds` is `std::vector<facebook::jsi::PropNameID>`, and
    # `jsi::PropNameID` is move-only (its copy constructor is deleted). Swift 6.1 / Xcode 16.4
    # has no `push_back(consuming:)` label on the imported C++ `std::vector::push_back`, so the
    # move must happen in C++ through a small wrapper that takes the value by value.
    if os.path.exists(jsutils):
        try:
            with open(jsutils, "r", encoding="utf-8") as f:
                u2_content = f.read()
            target_u5 = " */\ninline void getStringData("
            replacement_u5 = """ */

/**
 Pushes a move-only `jsi::PropNameID` to the end of a `PropNameIds` vector. The value is taken
 by value and moved in, because `jsi::PropNameID`'s copy constructor is deleted and Swift 6.1
 has no `push_back(consuming:)` on the imported C++ `std::vector`.
 *
 * The vector is taken by value and returned by value rather than through an inout reference:
 * passing a `PropNameIds &` (inout) from Swift crashes swift-frontend (ASSERT_failure in
 * emitConsumedLValueRecursive) when the move-only element type is involved in the interop.
 */
inline HostObjectCallbacks::PropNameIds pushBackPropNameId(HostObjectCallbacks::PropNameIds vector, jsi::PropNameID propNameId) noexcept {
  vector.push_back(std::move(propNameId));
  return vector;
}

inline void getStringData("""
            if target_u5 in u2_content and "pushBackPropNameId" not in u2_content:
                # `PropNameIds` (expo::HostObjectCallbacks::PropNameIds) is defined in
                # HostObjectCallbacks.h, which JSIUtils.h does not include. Add the include so
                # the helper below can take a `PropNameIds &`.
                if '#include "HostObjectCallbacks.h"' not in u2_content:
                    u2_content = u2_content.replace(
                        '#include "TypedArray.h"',
                        '#include "HostObjectCallbacks.h"\n#include "TypedArray.h"'
                    )
                with open(jsutils, "w", encoding="utf-8") as f:
                    f.write(u2_content.replace(target_u5, replacement_u5))
                print("Successfully patched JSIUtils.h (pushBackPropNameId helper)")
        except Exception as e:
            print(f"Error patching {jsutils}: {e}")

    # 8. Patch JSIUtils.h: take `jsi::Value` by rvalue reference in errorFromValue
    # `expo.errorFromValue(runtime.pointee, value.pointee)` is called from
    # JavaScriptError.swift with a move-only `facebook.jsi.Value`. Passing it by value
    # (`jsi::Value value`) makes Swift 6.1 / Xcode 16.4 emit a `load_take` on the
    # move-only parameter during SILGen, which crashes swift-frontend with an
    # ASSERT_failure in `emitConsumedLValueRecursive` / `emitLoadValueOperation`
    # (SILBuilder.h:810, "isLoadableOrOpaque"). The crash targets
    # `JavaScriptError.init(_:value:)` and aborts the ExpoModulesJSI xcframework
    # build phase. Taking the value by rvalue reference (`jsi::Value&& value`) keeps
    # the move semantics identical while avoiding the load_take.
    if os.path.exists(jsutils):
        try:
            with open(jsutils, "r", encoding="utf-8") as f:
                ev_content = f.read()
            target_ev = "inline jsi::JSError errorFromValue(jsi::IRuntime &runtime, jsi::Value value) {"
            replacement_ev = "inline jsi::JSError errorFromValue(jsi::IRuntime &runtime, jsi::Value&& value) {"
            if target_ev in ev_content:
                ev_content = ev_content.replace(target_ev, replacement_ev)
                with open(jsutils, "w", encoding="utf-8") as f:
                    f.write(ev_content)
                print("Successfully patched JSIUtils.h (errorFromValue by rvalue ref)")
        except Exception as e:
            print(f"Error patching JSIUtils.h errorFromValue: {e}")

    # 9. Patch JavaScriptError.swift: add explicit 'consuming:' label for Swift 6.2/
    # Xcode 26+ when passing a move-only field through the C++ FFI.
    # `value.pointee` is a `facebook.jsi.Value` stored inside `JavaScriptValue`;
    # moving it out of the struct requires the explicit `consuming:` label in the
    # call to `expo.errorFromValue(...)`. Without it, Swift 6.2 emits:
    #   error: missing argument label 'consuming:' in call
    js_error = os.path.join(ios_dir, "node_modules/expo-modules-jsi/apple/Sources/ExpoModulesJSI/Runtime/Values/JavaScriptError.swift")
    if os.path.exists(js_error):
        try:
            with open(js_error, "r", encoding="utf-8") as f:
                je_content = f.read()
            target_call = "expo.errorFromValue(runtime.pointee, value.pointee)"
            replacement_call = "expo.errorFromValue(runtime.pointee, consuming: value.pointee)"
            if target_call in je_content:
                je_content = je_content.replace(target_call, replacement_call)
                with open(js_error, "w", encoding="utf-8") as f:
                    f.write(je_content)
                print("Successfully patched JavaScriptError.swift (added consuming: label)")
        except Exception as e:
            print(f"Error patching JavaScriptError.swift: {e}")

if __name__ == "__main__":
    patch_expo()
