"""Minimal source branding overlay. Fails closed if upstream strings change."""
import pathlib
import re
import sys

def brand(text):
    for key, value in (("IDS_PRODUCT_NAME", "AetherBolt Browser"),
                       ("IDS_SHORT_PRODUCT_NAME", "AetherBolt")):
        pattern = r'(<message\s+name="' + key + r'"[^>]*>\s*)Chromium(\s*</message>)'
        text, n = re.subn(pattern, lambda m: m[1] + value + m[2], text)
        if n != 1:
            current = r'<message\s+name="' + key + r'"[^>]*>\s*' + value + r'\s*</message>'
            if n or len(re.findall(current, text)) != 1:
                raise ValueError("Upstream branding changed: " + key)
    return text

def main():
    p = pathlib.Path(sys.argv[1]) / "chrome/app/chromium_strings.grd"
    original = p.read_text(encoding="utf-8")
    p.write_text(brand(original), encoding="utf-8")

if __name__ == "__main__":
    main()
