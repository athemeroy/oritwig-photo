#!/usr/bin/env python3
"""Mechanical extraction of Telegram's crop state and bitmap transform. No new geometry."""
from pathlib import Path
import hashlib,json,difflib,argparse
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--upstream-dir',required=True,help='Directory holding the two pinned original Java files');a=p.parse_args();upstream=Path(a.upstream_dir)
EXPECTED={'MediaController.java': 'ac2837023846c52f1d0bf09cbc913d1fc723a94ae029a1437711e5092afbe459', 'PhotoViewer.java': 'a032559f2b5630b5590c92250dc27c4bcb3b4b1d05089bc4b1e7be90343affc4'}
for name, expected in EXPECTED.items():
 if hashlib.sha256((upstream/name).read_bytes()).hexdigest()!=expected: raise SystemExit("Upstream hash mismatch: "+name)
def block(s,token):
 start=s.index(token);brace=s.index('{',start);depth=1;i=brace+1
 while depth:
  depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[start:i]
media=(upstream/'MediaController.java').read_text();viewer=(upstream/'PhotoViewer.java').read_text()
state=block(media,'    public static class CropState extends TLObject')
state=state[:state.index('        @Override\n        public void readParams')].rstrip()+'\n    }'
state=state.replace('    public static class CropState extends TLObject','public class CropState').replace('        public static final int constructor = 0x44a3abcd;\n','')
header=media[:media.index('package ')]
state=header+'\n// Adapted 2026-10-01: lift state from MediaController; remove protocol serialization.\npackage dev.oritwig.crop;\nimport android.graphics.Matrix;\n\n'+state+'\n'
method=block(viewer,'    public static Bitmap createCroppedBitmap(')
adapted=method.replace('MediaController.CropState','CropState').replace('FileLog.e(e);','android.util.Log.e("TelegramCrop", "Crop render failed", e);')
out=viewer[:viewer.index('package ')]+'\n// Adapted 2026-10-01: isolated PhotoViewer.createCroppedBitmap; only model/logging names changed.\npackage dev.oritwig.crop;\nimport android.graphics.Bitmap;\nimport android.graphics.Canvas;\nimport android.graphics.Matrix;\nimport android.graphics.Paint;\n\npublic final class TelegramCrop {\n    private TelegramCrop() {}\n'+adapted+'\n}\n'
files={'CropState.java':state,'TelegramCrop.java':out}
for name,text in files.items():(root/'crop/src/main/java/dev/oritwig/crop'/name).write_text(text)
ledger={'upstream':'https://github.com/DrKLO/Telegram','commit':'f2908b14133bbffbf7ab04f641ecb5bfaf533242','original_paths':{'MediaController.java':'TMessagesProj/src/main/java/org/telegram/messenger/MediaController.java','PhotoViewer.java':'TMessagesProj/src/main/java/org/telegram/ui/PhotoViewer.java'},'sources':{n:hashlib.sha256((upstream/n).read_bytes()).hexdigest() for n in ['MediaController.java','PhotoViewer.java']},'outputs':{n:hashlib.sha256(t.encode()).hexdigest() for n,t in files.items()},'changes':['CropState: lift fields/clone/isEmpty, remove TLObject and Telegram protocol serialization','createCroppedBitmap: preserve all bitmap geometry; replace model qualification and logging only'],'limits':'Caller supplies validated orthogonal rotation and bounded nonzero crop dimensions. UI only offers center presets; freeform crop is not advertised.'}
(root/'docs/crop-provenance.json').write_text(json.dumps(ledger,indent=2)+'\n')
(root/'docs/crop-method.patch').write_text(''.join(difflib.unified_diff(method.splitlines(True),adapted.splitlines(True),fromfile='PhotoViewer.createCroppedBitmap',tofile='TelegramCrop.createCroppedBitmap')))
