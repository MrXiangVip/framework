#!/usr/bin/env bash
set -euo pipefail
APPS_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$APPS_DIR/../.." && pwd)"
FRAMEWORK_CLASSES="$REPO_ROOT/out/framework/classes"
COMPILE_FRAMEWORK=true
if [[ "${1:-}" == "--framework-classes" ]]; then
    [[ $# -ge 2 ]] || { echo 'Missing framework classes directory' >&2; exit 1; }
    FRAMEWORK_CLASSES="$2"
    COMPILE_FRAMEWORK=false
    shift 2
fi

# With no application names, discover every direct child containing a manifest.
APP_NAMES=("$@")
if [[ ${#APP_NAMES[@]} -eq 0 ]]; then
    for app_dir in "$APPS_DIR"/*/; do
        [[ -f "$app_dir/AndroidManifest.xml" ]] || continue
        APP_NAMES+=("$(basename -- "$app_dir")")
    done
fi
[[ ${#APP_NAMES[@]} -gt 0 ]] || { echo 'No applications found' >&2; exit 1; }
for name in "${APP_NAMES[@]}"; do
    [[ "$name" =~ ^[a-zA-Z0-9_-]+$ && -f "$APPS_DIR/$name/AndroidManifest.xml" && -d "$APPS_DIR/$name/src" ]] \
        || { echo "Invalid application or missing manifest/src: $name" >&2; exit 1; }
done

if "$COMPILE_FRAMEWORK"; then
    mkdir -p "$FRAMEWORK_CLASSES"
    mapfile -d '' -t sources < <(find "$REPO_ROOT/base/com/android" -name '*.java' -print0)
    javac -encoding UTF-8 -source 8 -target 8 -cp "$REPO_ROOT/libs/*" -d "$FRAMEWORK_CLASSES" "${sources[@]}"
fi
[[ -f "$FRAMEWORK_CLASSES/com/android/app/Activity.class" ]] \
    || { echo "Missing compiled framework: $FRAMEWORK_CLASSES" >&2; exit 1; }

mkdir -p "$REPO_ROOT/out/apps"
BUILD_TMP=''
cleanup() { if [[ -n "$BUILD_TMP" ]]; then rm -rf -- "$BUILD_TMP"; fi; }
trap cleanup EXIT
for name in "${APP_NAMES[@]}"; do
    app_dir="$APPS_DIR/$name"
    # Browser APK and its external Chromium host must be prepared together on Linux.
    if [[ "$name" == "browser" ]]; then
        echo "Prepare browser Chromium runtime"
        bash "$app_dir/setup.sh" --ensure
    fi
    apk="$REPO_ROOT/out/system/app/$name/$name.apk"
    mapfile -d '' -t sources < <(find "$app_dir/src" -name '*.java' -print0)
    [[ ${#sources[@]} -gt 0 ]] || { echo "No Java sources: $name" >&2; exit 1; }
    # A fresh directory prevents deleted or renamed classes from entering the next APK.
    BUILD_TMP="$(mktemp -d "$REPO_ROOT/out/apps/$name.XXXXXX")"
    mkdir -p "$BUILD_TMP/classes" "$(dirname -- "$apk")"
    javac -encoding UTF-8 -source 8 -target 8 -cp "$FRAMEWORK_CLASSES:$REPO_ROOT/libs/*:$app_dir/libs/*" \
        -sourcepath "$app_dir/src" -d "$BUILD_TMP/classes" "${sources[@]}"
    if [[ -d "$app_dir/libs" ]]; then
        for dependency in "$app_dir/libs/"*.jar; do
            [[ -f "$dependency" ]] || continue
            (cd "$BUILD_TMP/classes" && jar xf "$dependency")
        done
        rm -f -- "$BUILD_TMP/classes/META-INF/MANIFEST.MF" "$BUILD_TMP/classes/"module-info.class
        find "$BUILD_TMP/classes/META-INF" -type f \( -name '*.SF' -o -name '*.RSA' -o -name '*.DSA' \) -delete
    fi
    entries=(-C "$BUILD_TMP/classes" . -C "$app_dir" AndroidManifest.xml)
    if [[ -d "$app_dir/res" ]]; then entries+=(-C "$app_dir" res); fi
    jar cf "$BUILD_TMP/$name.apk" "${entries[@]}"
    # Validate declared runtime resources before replacing the installed package.
    if [[ -f "$app_dir/required-apk-entries.txt" ]]; then
        jar tf "$BUILD_TMP/$name.apk" > "$BUILD_TMP/apk-entries.txt"
        while IFS= read -r entry || [[ -n "$entry" ]]; do
            entry="${entry%$'\r'}"
            [[ -n "$entry" ]] || continue
            if ! grep -Fxq -- "$entry" "$BUILD_TMP/apk-entries.txt"; then
                echo "Incomplete APK $name: missing $entry. Check $app_dir/libs dependencies." >&2
                exit 1
            fi
        done < "$app_dir/required-apk-entries.txt"
    fi
    mv -f -- "$BUILD_TMP/$name.apk" "$apk"
    cleanup
    BUILD_TMP=''
    echo "Built: $apk"
done
echo "Built ${#APP_NAMES[@]} applications"
