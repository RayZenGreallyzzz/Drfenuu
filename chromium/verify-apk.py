"""Check that the APK includes actual ARM64 browser code, not a WebView shell."""
import argparse
import struct
import zipfile

def verify(path):
    with zipfile.ZipFile(path) as apk:
        files = set(apk.namelist())
        if not {"AndroidManifest.xml", "classes.dex"} <= files:
            raise ValueError("Not a complete Android APK")
        libs = [n for n in files if n.startswith("lib/arm64-v8a/") and n.rsplit("/", 1)[1] in ("libchrome.so", "libmonochrome.so")]
        if not libs:
            raise ValueError("No native Chromium ARM64 library")
        for name in libs:
            with apk.open(name) as lib:
                header = lib.read(64)
            if header[:6] != b"\x7fELF\x02\x01" or struct.unpack_from("<H", header, 18)[0] != 183:
                raise ValueError("Invalid AArch64 ELF: " + name)
            if apk.getinfo(name).file_size < 10 * 1024 * 1024:
                raise ValueError("Unexpectedly small Chromium library")
        print("Native ARM64 browser libraries:", ", ".join(sorted(libs)))
        print("Static check passed; install, package, version and runtime extension tests still required.")

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("apk")
    verify(p.parse_args().apk)
