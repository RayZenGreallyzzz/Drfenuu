import pathlib
import sys
import unittest
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from brand import brand

SAMPLE = """<message name="IDS_PRODUCT_NAME">Google Chrome for Testing</message>
<message name="IDS_PRODUCT_NAME" translateable="false">Chromium</message>
<message name="IDS_SHORT_PRODUCT_NAME">Chromium</message>"""

class BrandTests(unittest.TestCase):
    def test_rebrand(self):
        s = brand(SAMPLE)
        self.assertIn(">AetherBolt Browser<", s)
        self.assertIn(">AetherBolt<", s)
        self.assertIn(">Google Chrome for Testing<", s)
    def test_idempotent(self):
        self.assertEqual(brand(brand(SAMPLE)), brand(SAMPLE))
    def test_missing(self):
        with self.assertRaises(ValueError):
            brand("<message name='OTHER'>Chromium</message>")
    def test_ambiguous(self):
        with self.assertRaises(ValueError):
            brand(SAMPLE + SAMPLE)

if __name__ == "__main__":
    unittest.main()
