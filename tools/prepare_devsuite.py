#!/usr/bin/env python3
"""Prepare local assets at build time. Uses upstream releases, not runtime CDNs."""
import hashlib, pathlib, re, shutil, subprocess, tempfile, urllib.request, zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
OUT=ROOT/"app/src/main/assets/devsuite"
def fetch(url,dst):
    if not dst.exists():
        tmp=dst.with_suffix(dst.suffix+".part")
        with urllib.request.urlopen(url,timeout=180) as r, tmp.open("wb") as w: shutil.copyfileobj(r,w)
        tmp.replace(dst)
    return dst
def copy(src,dst):
    dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,dst)
def normalize_cyberchef_assets(dst):
    """Android asset merging strips .gz suffixes; keep one canonical file."""
    import gzip
    for compressed in sorted(dst.rglob("*.gz")):
        plain=compressed.with_suffix("")
        data=gzip.decompress(compressed.read_bytes())
        if plain.exists():
            if plain.read_bytes()!=data:
                raise RuntimeError("Conflicting CyberChef resources: "+str(plain))
        else:
            plain.write_bytes(data)
        compressed.unlink()
    # Tesseract expects eng.traineddata.gz by default. Supply the raw dictionary.
    ocr=dst/"modules/OCR.js"
    source=ocr.read_text(encoding="utf-8")
    needle='langPath:`${u}tesseract/lang-data`,'
    if source.count(needle)!=1:
        raise RuntimeError("Unexpected CyberChef OCR worker configuration")
    ocr.write_text(source.replace(needle,needle+"gzip:false,"),encoding="utf-8")
    if not (dst/"assets/tesseract/lang-data/eng.traineddata").is_file():
        raise RuntimeError("Missing OCR dictionary")
    if any(dst.rglob("*.gz")):
        raise RuntimeError("Unexpected gzip asset")

with tempfile.TemporaryDirectory() as temp:
    t=pathlib.Path(temp)
    subprocess.run(["npm","install","--ignore-scripts","--no-audit","--no-fund","--prefix",str(t),"eruda@3.4.3","@xterm/xterm@6.0.0","v86@0.5.476"],check=True)
    nm=t/"node_modules"; bridge=OUT/"bridge/vendor"; vm=OUT/"tools/v86"
    copy(nm/"eruda/eruda.js",bridge/"eruda.js")
    for n,src in [("xterm.js",nm/"@xterm/xterm/lib/xterm.js"),("xterm.css",nm/"@xterm/xterm/css/xterm.css")]:
        copy(src,bridge/n);copy(src,vm/n)
    for pkg,name in [(nm/"eruda","eruda"),(nm/"@xterm/xterm","xterm"),(nm/"v86","v86")]:
        licenses=list(pkg.glob("LICENSE*"))
        for i,p in enumerate(licenses):copy(p,OUT/"tools/licenses"/(name+"-"+str(i)+".txt"))
    build=nm/"v86/build"
    js=next((build/n for n in ["libv86.js","libv86.min.js"] if (build/n).is_file()),None)
    if js is None:raise RuntimeError("v86 browser bundle missing")
    copy(js,vm/"libv86.js");copy(build/"v86.wasm",vm/"v86.wasm")
    for name in ["seabios.bin","vgabios.bin"]:fetch("https://copy.sh/v86/bios/"+name,vm/name)
    fetch("https://i.copy.sh/linux.iso",vm/"linux.iso")
    filename="CyberChef_f464d62c7c943cda9749a765932a7548ba9bfb16.zip"
    z=fetch("https://github.com/gchq/CyberChef/releases/download/v11.6.0/"+filename,t/filename)
    if hashlib.sha256(z.read_bytes()).hexdigest()!="6b6518c620d43776c19bf5cea1fd4a505b018bc829f7e88f7f77be38d64598d9":raise RuntimeError("CyberChef SHA256 differs from supplied archive")
    dst=OUT/"tools/cyberchef"
    if dst.exists():shutil.rmtree(dst)
    dst.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(z) as archive:
        for member in archive.infolist():
            p=(dst/member.filename).resolve()
            if not p.is_relative_to(dst.resolve()):raise ValueError("Unsafe ZIP path")
        archive.extractall(dst)
    normalize_cyberchef_assets(dst)
    page=dst/"CyberChef_v11.6.0.html";html=page.read_text(encoding="utf-8");counter=[0]
    def externalize(m):
        attrs,body=m.group(1),m.group(2)
        if re.search(r"\bsrc\s*=",attrs,re.I) or not body.strip():return m.group(0)
        counter[0]+=1;name="aether-inline-"+str(counter[0])+".js";(dst/name).write_text(body,encoding="utf-8")
        return "<script"+attrs+" src="+name+"></script>"
    page.write_text(re.sub(r"<script\b([^>]*)>(.*?)</script\s*>",externalize,html,flags=re.I|re.S),encoding="utf-8")
print("DevSuite assets prepared")
