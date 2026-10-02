"""Import the supplied evidence unchanged; derive runnable cases separately."""
import json
from pathlib import Path
import sys
import zipfile


def main():
    root = Path(__file__).resolve().parents[2]
    with zipfile.ZipFile(sys.argv[1]) as archive:
        evidence = json.loads(archive.read("t9-regression.json"))
    destination = root / "app/src/androidTest/assets/ime_lab/t9_reported_cases.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
