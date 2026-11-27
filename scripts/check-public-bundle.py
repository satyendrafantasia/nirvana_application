"""Check emitted browser assets before publication. Only report filenames, never matching values."""
import re
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else "frontend/dist")
if not (root / "index.html").is_file():
    sys.exit("Build the production frontend first.")

private_markers = re.compile(
    rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"
    rb"|(?:AKIA|ASIA)[0-9A-Z]{16}"
    rb"|(?:JWT_SECRET|AWS_SECRET_ACCESS_KEY|SPA_DB_PASSWORD|RAZORPAY_KEY_SECRET)"
    rb"""\s*["']?\s*[:=]\s*["'][^"'$\s]{10,}""",
    re.IGNORECASE,
)
files = [p for p in root.rglob("*") if p.is_file()]
failures = []
for path in files:
    if path.suffix.lower() == ".map":
        failures.append(path)
    elif path.suffix.lower() in {".html", ".js", ".css", ".json", ".mjs"}:
        contents = path.read_bytes()
        if private_markers.search(contents) or re.search(rb"(?m)^\s*(?://|/\*)[#@]\s*sourceMappingURL=", contents):
            failures.append(path)
if failures:
    sys.exit("Review public files: " + ", ".join(str(p.relative_to(root)) for p in failures))
print(f"Checked {len(files)} production files for private credential signatures and source maps.")
