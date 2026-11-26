#!/usr/bin/env python3
"""AEB1 delta: copy unchanged ZIP compressed data, gzip remaining literals."""
import argparse, gzip, hashlib, struct, zipfile
from pathlib import Path
MAX = 512 * 1024 * 1024
def digest(data): return hashlib.sha256(data).digest()
def ranges(data, path):
    result = {}
    with zipfile.ZipFile(path) as z:
        for entry in z.infolist():
            off = entry.header_offset
            if data[off:off+4] != b'PK\x03\x04': raise ValueError("invalid local header")
            n, e = struct.unpack_from('<HH', data, off+26)
            start = off+30+n+e
            end = start+entry.compress_size
            if end > len(data): raise ValueError("invalid entry length")
            if entry.compress_size >= 4096:
                result[entry.filename] = (start,end)
    return result
def create(old, new, patch):
    a,b = Path(old).read_bytes(),Path(new).read_bytes()
    if max(len(a),len(b)) > MAX: raise ValueError("APK exceeds limit")
    ar,br = ranges(a,old),ranges(b,new)
    matches=[]
    for name,(start,end) in br.items():
        if name in ar:
            x,y=ar[name]
            if y-x==end-start and a[x:y]==b[start:end]:
                matches.append((start,end,x))
    cursor=0
    with gzip.open(patch,'wb',compresslevel=6) as f:
        f.write(b'AEB1'+digest(a)+digest(b)+struct.pack('>Q',len(b)))
        def literal(data):
            if data: f.write(b'\x01'+struct.pack('>I',len(data))+data)
        for start,end,x in sorted(matches):
            if start<cursor: continue
            literal(b[cursor:start])
            f.write(b'\x02'+struct.pack('>QI',x,end-start))
            cursor=end
        literal(b[cursor:])
        f.write(b'\x00')
    print(f"Patch: {Path(patch).stat().st_size} bytes; full APK: {len(b)} bytes")
def apply(old, patch, output):
    a=Path(old).read_bytes()
    with gzip.open(patch,'rb') as f:
        if f.read(4)!=b'AEB1' or f.read(32)!=digest(a): raise ValueError("wrong base")
        expected=f.read(32); size=struct.unpack('>Q',f.read(8))[0]
        if not 0<size<=MAX: raise ValueError("invalid size")
        out=bytearray()
        while True:
            tag=f.read(1)
            if tag==b'\0': break
            if tag==b'\1':
                n=struct.unpack('>I',f.read(4))[0]
                if n>size-len(out): raise ValueError("literal too large")
                data=f.read(n)
                if len(data)!=n: raise ValueError("truncated")
                out.extend(data)
            elif tag==b'\2':
                pos,n=struct.unpack('>QI',f.read(12))
                if pos+n>len(a) or n>size-len(out): raise ValueError("bad copy")
                out.extend(a[pos:pos+n])
            else: raise ValueError("invalid operation")
        if len(out)!=size or digest(out)!=expected or f.read(1): raise ValueError("bad result")
    Path(output).write_bytes(out)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('operation',choices=['create','apply']);p.add_argument('base');p.add_argument('input');p.add_argument('output');a=p.parse_args()
    (create if a.operation=='create' else apply)(a.base,a.input,a.output)
