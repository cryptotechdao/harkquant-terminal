import os, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
_tmp = tempfile.mkdtemp(prefix="hqt-test-")
os.environ["HQT_STATE"] = _tmp                     # never touch the real state/ folder
os.environ["HQ_NEWS_DB"] = os.path.join(_tmp, "news.db")
os.environ["HQT_NO_NEWS"] = "1"                    # no background pollers during tests
for k in ("HQT_TOKEN", "HQT_X_BEARER"):
    os.environ.pop(k, None)
