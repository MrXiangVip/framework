#!/usr/bin/env bash
# Sourced by build.sh before launching the desktop. Respect an explicitly selected IM.
if [[ -z "${XMODIFIERS:-}" ]] && grep -qi microsoft /proc/sys/kernel/osrelease 2>/dev/null; then
    if command -v fcitx5 >/dev/null 2>&1; then
        export XMODIFIERS=@im=fcitx
    elif command -v ibus-daemon >/dev/null 2>&1; then
        export XMODIFIERS=@im=ibus
    else
        echo 'Chinese input on WSL requires a Linux XIM input method. Install: sudo apt install fcitx5 fcitx5-chinese-addons' >&2
    fi
fi
if [[ "${XMODIFIERS:-}" == '@im=fcitx' ]] && command -v fcitx5 >/dev/null 2>&1; then
    if [[ -z "${DBUS_SESSION_BUS_ADDRESS:-}" && -S "/run/user/$(id -u)/bus" ]]; then
        export DBUS_SESSION_BUS_ADDRESS="unix:path=/run/user/$(id -u)/bus"
    fi
    # Only seed a first-run profile; preserve all existing user input-method choices.
    im_config="${XDG_CONFIG_HOME:-$HOME/.config}/fcitx5"
    if [[ ! -e "$im_config/profile" ]] && command -v fcitx5-remote >/dev/null 2>&1; then
        mkdir -p "$im_config"
        cat > "$im_config/profile" <<'PROFILE'
[Groups/0]
Name=Default
Default Layout=us
DefaultIM=pinyin

[Groups/0/Items/0]
Name=keyboard-us
Layout=

[Groups/0/Items/1]
Name=pinyin
Layout=

[GroupOrder]
0=Default
PROFILE
    fi
    # Query this D-Bus session; another desktop or test session may have its own daemon.
    im_running=false
    if [[ -n "${DBUS_SESSION_BUS_ADDRESS:-}" ]] && command -v dbus-send >/dev/null 2>&1; then
        if dbus-send --session --print-reply --dest=org.freedesktop.DBus /org/freedesktop/DBus org.freedesktop.DBus.NameHasOwner string:org.fcitx.Fcitx5 2>/dev/null | grep -q 'boolean true'; then
            im_running=true
        fi
    elif pgrep -u "$(id -u)" -x fcitx5 >/dev/null 2>&1; then
        im_running=true
    fi
    if [[ "$im_running" == false ]]; then
        fcitx5 -d --disable=wayland,waylandim > /tmp/framework-fcitx5-"$(id -u)".log 2>&1 || true
    fi
    unset im_config im_running
fi
