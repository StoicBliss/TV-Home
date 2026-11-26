#!/usr/bin/env python3
"""Cross-platform ADB-over-Wi-Fi tool for Android TV / Google TV.

No adb binary needed; avoids macOS local network blocks and works on Windows, macOS, and Linux.

Usage:
    tv.py --find                             Discover TVs on local Wi-Fi and save IP
    tv.py --probe                            Show TV hardware, brand, model & OS version
    tv.py --backup [file.txt]                Backup list of all installed packages
    tv.py --disable <debloat.txt>            Disable all packages in debloat list (user 0)
    tv.py --restore <debloat.txt> [--stock]  Re-enable packages and restore stock launcher
    tv.py --shot [out.png]                   Capture screenshot from TV
    tv.py --type "text" [--enter]            Type text into focused field on TV
    tv.py --push-b64 <local> <remote>        Push file (chunked base64 + md5 check)
    tv.py --pull <remote> <local>            Pull file from TV
    tv.py --batch < cmds.txt                 Execute commands line-by-line in one connection
    tv.py <command>                          Run arbitrary shell command on TV
"""

import base64
import concurrent.futures
import getpass
import hashlib
import os
import shlex
import socket
import sys
import urllib.request

from adb_shell.adb_device import AdbDeviceTcp
from adb_shell.auth.keygen import keygen
from adb_shell.auth.sign_pythonrsa import PythonRSASigner

HERE = os.path.dirname(os.path.abspath(__file__))
HOST_FILE = os.path.join(HERE, ".tv_host")
KEY = os.path.join(HERE, "adbkey", "adbkey")


def get_configured_host():
    if os.environ.get("TV_HOST"):
        return os.environ.get("TV_HOST").strip()
    if os.path.exists(HOST_FILE):
        val = open(HOST_FILE).read().strip()
        if val:
            return val
    return ""


def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def find_tvs():
    local_ip = get_local_ip()
    if local_ip == "127.0.0.1":
        print("Error: Could not determine local IP. Check network connection.")
        sys.exit(1)

    parts = local_ip.split(".")
    subnet = ".".join(parts[:3])
    print(f"Scanning {subnet}.0/24 from local IP {local_ip}...")

    def check_target(ip):
        results = {}
        for port, name in ((5555, "adb"), (8008, "cast")):
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(0.35)
            try:
                if sock.connect_ex((ip, port)) == 0:
                    results[name] = port
            except Exception:
                pass
            finally:
                sock.close()
        return ip, results

    found_adb = []
    found_cast = []

    with concurrent.futures.ThreadPoolExecutor(max_workers=60) as executor:
        futures = [executor.submit(check_target, f"{subnet}.{i}") for i in range(1, 255)]
        for f in concurrent.futures.as_completed(futures):
            ip, services = f.result()
            if "adb" in services:
                found_adb.append(ip)
            if "cast" in services:
                found_cast.append(ip)

    if found_cast:
        print("\nGoogle Cast devices discovered:")
        for cip in sorted(found_cast, key=lambda x: int(x.split(".")[-1])):
            name = ""
            try:
                req = urllib.request.Request(f"http://{cip}:8008/setup/eureka_info?params=name", headers={"User-Agent": "tv-home"})
                with urllib.request.urlopen(req, timeout=2.0) as resp:
                    data = resp.read().decode("utf-8", "ignore")
                    if '"name":"' in data:
                        name = data.split('"name":"')[1].split('"')[0]
            except Exception:
                pass
            print(f"  {cip}{f' ({name})' if name else ''}")

    if found_adb:
        print("\nAndroid TV devices with ADB (port 5555) open:")
        for aip in sorted(found_adb, key=lambda x: int(x.split(".")[-1])):
            print(f"  {aip}")

    # Filter out local IP - local machine is never the TV
    candidates = [ip for ip in found_adb if ip != local_ip]

    if candidates:
        # Prioritize device that also announced as Cast device
        cast_matches = [ip for ip in candidates if ip in found_cast]
        target_tv = cast_matches[0] if cast_matches else sorted(candidates, key=lambda x: int(x.split(".")[-1]))[0]
        with open(HOST_FILE, "w") as f:
            f.write(target_tv + "\n")
        print(f"\nSaved {target_tv} to tools/.tv_host.")
        print(f"Next: python tools/tv.py --probe")
    else:
        print("\nNo TV with ADB (port 5555) open was found.")
        print("Please ensure your TV is turned ON and on the same Wi-Fi, with USB debugging enabled:")
        print("  Settings -> Device Preferences / System -> About -> click 'Build' 7 times")
        print("  Developer options -> turn on 'USB debugging'")
        if found_cast:
            print("(Your TV is likely one of the Cast devices listed above.)")
        sys.exit(1)


def signer():
    os.makedirs(os.path.dirname(KEY), exist_ok=True)
    if not os.path.exists(KEY):
        keygen(KEY)
    return PythonRSASigner(open(KEY + ".pub").read(), open(KEY).read())


def connect():
    host = get_configured_host()
    if not host:
        sys.exit("TV host not configured. Run 'tv.py --find' or set TV_HOST=192.168.x.y")
    port = int(os.environ.get("TV_PORT", "5555"))
    dev = AdbDeviceTcp(host, port, default_transport_timeout_s=120.0)
    dev.connect(rsa_keys=[signer()], auth_timeout_s=120)
    return dev


def push_b64(dev, local, remote, chunk=48_000):
    if not os.path.exists(local):
        sys.exit(f"Local file not found: {local}")
    data = open(local, "rb").read()
    enc = base64.b64encode(data).decode()
    tmp = remote + ".b64"
    dev.shell(f"rm -f {tmp} {remote}")
    for i in range(0, len(enc), chunk):
        dev.shell(f"echo -n '{enc[i:i + chunk]}' >> {tmp}")
    dev.shell(f"base64 -d {tmp} > {remote} && rm -f {tmp}")
    remote_md5 = dev.shell(f"md5sum {remote}").split()[0]
    local_md5 = hashlib.md5(data).hexdigest()
    if remote_md5 != local_md5:
        sys.exit(f"md5 mismatch: remote ({remote_md5}) != local ({local_md5})")
    print(f"Pushed {local} -> {remote} ({len(data):,} bytes, md5 verified)")


def cmd_probe(dev):
    props = [
        "ro.product.manufacturer",
        "ro.product.brand",
        "ro.product.model",
        "ro.product.device",
        "ro.build.version.release",
        "ro.build.version.sdk",
        "ro.build.display.id",
    ]
    print("--- TV Device Information ---")
    for p in props:
        val = dev.shell(f"getprop {p}").strip()
        print(f"  {p} = {val}")

    try:
        wm_size = dev.shell("wm size").strip()
        wm_density = dev.shell("wm density").strip()
        if wm_size:
            print(f"  {wm_size}")
        if wm_density:
            print(f"  {wm_density}")
    except Exception:
        pass


def cmd_backup(dev, out_file):
    if not out_file:
        out_file = os.path.join(HERE, "..", "backup", "packages.txt")
    out_dir = os.path.dirname(os.path.abspath(out_file))
    os.makedirs(out_dir, exist_ok=True)
    print(f"Querying installed packages from TV...")
    output = dev.shell("pm list packages -f", read_timeout_s=120)
    lines = [line.strip() for line in output.splitlines() if line.strip()]
    with open(out_file, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"Saved {len(lines)} installed packages to {out_file}")


def cmd_disable(dev, list_file):
    if not os.path.exists(list_file):
        sys.exit(f"Debloat list file not found: {list_file}")

    packages = []
    with open(list_file, "r", encoding="utf-8") as f:
        for line in f:
            clean = line.strip()
            if clean and not clean.startswith("#"):
                pkg = clean.split()[0]
                packages.append(pkg)

    if not packages:
        print(f"No packages found in {list_file}")
        return

    print(f"Disabling {len(packages)} packages for user 0 (reversible)...")
    for idx, pkg in enumerate(packages, 1):
        resp = dev.shell(f"pm disable-user --user 0 {pkg}", read_timeout_s=60).strip()
        print(f"  [{idx}/{len(packages)}] {pkg}: {resp}")
    print("\nDebloat completed. Packages are disabled, not uninstalled.")


def cmd_restore(dev, list_file, stock_launcher=None):
    if not os.path.exists(list_file):
        sys.exit(f"Debloat list file not found: {list_file}")

    packages = []
    with open(list_file, "r", encoding="utf-8") as f:
        for line in f:
            clean = line.strip()
            if clean and not clean.startswith("#"):
                pkg = clean.split()[0]
                packages.append(pkg)

    print(f"Re-enabling {len(packages)} packages...")
    for idx, pkg in enumerate(packages, 1):
        resp = dev.shell(f"pm enable {pkg}", read_timeout_s=60).strip()
        print(f"  [{idx}/{len(packages)}] {pkg}: {resp}")

    # Re-enable setupwraith
    dev.shell("pm enable com.google.android.tungsten.setupwraith", read_timeout_s=60)

    # Re-enable stock launcher
    if stock_launcher:
        stock_pkg = stock_launcher.split("/")[0]
        dev.shell(f"pm enable {stock_pkg}", read_timeout_s=60)
        dev.shell(f"cmd package set-home-activity --user 0 {stock_launcher}", read_timeout_s=60)

    dev.shell("input keyevent KEYCODE_HOME")
    print("\nRestore completed. Press Home on the TV remote.")


def cmd_type(dev, text, press_enter=False):
    if not text:
        text = getpass.getpass("Text to type on TV (hidden): ")
    if not text:
        sys.exit("Nothing to type.")

    # ADB input text escapes: space becomes %s, safely quoted for shell execution
    safe_text = shlex.quote(text.replace(" ", "%s"))
    dev.shell(f"input text {safe_text}")
    if press_enter:
        dev.shell("input keyevent KEYCODE_ENTER")
    print(f"Typed {len(text)} characters on TV{' (with Enter)' if press_enter else ''}.")


def cmd_shot(dev, out_path):
    if not out_path:
        out_path = "screenshot.png"
    remote_tmp = "/data/local/tmp/_shot.png"
    dev.shell(f"screencap -p {remote_tmp}")
    dev.pull(remote_tmp, out_path)
    dev.shell(f"rm -f {remote_tmp}")
    print(f"Saved screenshot to {out_path}")


def print_help():
    print("""tv-home ADB Control Tool

Usage:
  tv.py --find                           Discover TVs on local Wi-Fi and save IP
  tv.py --probe                          Query TV properties (model, OS, display)
  tv.py --backup [output_file]           Backup installed packages list
  tv.py --disable <debloat.txt>          Disable packages in debloat list (user 0)
  tv.py --restore <debloat.txt> [--stock <activity>]
                                         Re-enable packages and restore stock launcher
  tv.py --shot [output.png]              Take screenshot from TV
  tv.py --type "text" [--enter]          Type text into focused field on TV
  tv.py --push-b64 <local> <remote>      Push local file to TV with md5 verification
  tv.py --pull <remote> <local>          Pull file from TV
  tv.py --batch                          Read and run commands from stdin
  tv.py <command>                        Run arbitrary shell command on TV
""")


def main():
    args = sys.argv[1:]
    if not args or args[0] in ("-h", "--help"):
        print_help()
        return

    cmd = args[0]

    # Discovery doesn't need existing ADB connection
    if cmd == "--find":
        find_tvs()
        return

    dev = connect()
    try:
        if cmd == "--probe":
            cmd_probe(dev)
        elif cmd == "--backup":
            out_file = args[1] if len(args) > 1 else None
            cmd_backup(dev, out_file)
        elif cmd == "--disable":
            if len(args) < 2:
                sys.exit("Usage: tv.py --disable <debloat_list.txt>")
            cmd_disable(dev, args[1])
        elif cmd == "--restore":
            if len(args) < 2:
                sys.exit("Usage: tv.py --restore <debloat_list.txt> [--stock <activity>]")
            list_file = args[1]
            stock = None
            if "--stock" in args:
                idx = args.index("--stock")
                if idx + 1 < len(args):
                    stock = args[idx + 1]
            cmd_restore(dev, list_file, stock)
        elif cmd == "--shot":
            out_path = args[1] if len(args) > 1 else "screenshot.png"
            cmd_shot(dev, out_path)
        elif cmd == "--type":
            text_parts = []
            press_enter = False
            for a in args[1:]:
                if a == "--enter":
                    press_enter = True
                else:
                    text_parts.append(a)
            text = " ".join(text_parts) if text_parts else ""
            cmd_type(dev, text, press_enter)
        elif cmd == "--push-b64":
            if len(args) < 3:
                sys.exit("Usage: tv.py --push-b64 <local_path> <remote_path>")
            push_b64(dev, args[1], args[2])
        elif cmd == "--pull":
            if len(args) < 3:
                sys.exit("Usage: tv.py --pull <remote_path> <local_path>")
            dev.pull(args[1], args[2])
            print(f"Pulled {args[1]} -> {args[2]}")
        elif cmd == "--batch":
            for line in sys.stdin:
                line = line.strip()
                if line and not line.startswith("#"):
                    print(f"$ {line}\n{dev.shell(line, read_timeout_s=120).rstrip()}")
        else:
            raw_cmd = " ".join(args)
            print(dev.shell(raw_cmd, read_timeout_s=120).rstrip())
    finally:
        dev.close()


if __name__ == "__main__":
    main()
