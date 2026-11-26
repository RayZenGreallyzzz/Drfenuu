import tempfile, pathlib, zipfile, os, unittest
from delta import create, apply
class DeltaTest(unittest.TestCase):
    def test_roundtrip(self):
        with tempfile.TemporaryDirectory() as d:
            a,b,p,o=[pathlib.Path(d)/x for x in ('old.apk','new.apk','patch.aeb','out.apk')]
            engine=os.urandom(500000)
            for f,ui in ((a,b'old'),(b,b'new interface')):
                with zipfile.ZipFile(f,'w',compression=zipfile.ZIP_DEFLATED) as z:
                    z.writestr('classes.dex',ui);z.writestr('lib/arm64-v8a/libxul.so',engine)
            create(a,b,p);apply(a,p,o)
            self.assertEqual(b.read_bytes(),o.read_bytes())
            self.assertLess(p.stat().st_size,10000)
            with self.assertRaises(ValueError):apply(b,p,o)
if __name__=='__main__': unittest.main()
