"""Capture unmodified output from the running REST compiler for regression tests."""
import argparse
import json
from pathlib import Path
import urllib.request

CASES = [
    ("qca_mixed", "QCA", 2, "?mix=1+theta*f1;", False, ""),
    ("qca_multi", "QCA", 2, "work=1+alpha*f1+beta*f2; ?mixed=work+gamma*f1T; ?energy=alpha*beta+gamma; ?constant=7;", False, ""),
    ("qca_cse", "QCA", 2, "v=alpha+beta; ?first=1+sin(v)*f1+cos(v)*f2; ?second=2+sin(v)*f1T+gamma*cos(v)*f2T;", True, ""),
    ("cga_multi", "CGA", 1, "v=1+alpha*e1+beta*e2; ?product=v*v+gamma*e3; ?sum=alpha+beta+gamma; ?constant=7;", True, ""),
    ("qga_mixed", "QGA", 1, "?mixed=1+theta*e1;", False, ""),
    ("qca5_state", "QCA", 5, "j=ei1*ei2; Id=f1*f1T*f2*f2T*f3*f3T*f4*f4T*f5*f5T; ?psi=cos(theta/2)*Id+j*sin(theta/2)*f1T*f2T*f3T*f4T*f5T*Id;", False, ""),
    ("qra_numeric", "QRA", 2, "?mixed=1+theta*e1;", False, "theta=0.37;"),
]
for function in ("sin", "cos", "sqrt", "abs", "exp", "log", "tan", "asin", "acos", "atan", "ceil", "floor", "fact"):
    CASES.append(("service_" + function, "QCA", 2, "?result=" + function + "(x);", False, ""))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--url", default="http://127.0.0.1:5174/api/v1/compile")
    args = parser.parse_args()
    args.directory.mkdir(parents=True, exist_ok=True)
    manifest = []
    for name, algebra, dimension, source, cse, assignments in CASES:
        payload = {"algebraPlugins": "ALGEBRA_" + algebra, "algebraDimension": dimension,
                   "codegenPlugins": "PYTHON", "outputMode": "CODE_ONLY",
                   "optimization": {"cse": cse, "maxima": False},
                   "script": {"functionName": name, "optimizeCode": source,
                              "variableAssignments": assignments, "multivectorsVisualized": ""}}
        request = urllib.request.Request(args.url, data=json.dumps(payload).encode(), headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=90) as response:
            result = json.load(response)
        assert result["statusCode"] == "200", result
        (args.directory / (name + ".py")).write_text(result["optimizeResult"], encoding="utf-8")
        manifest.append(payload)
    (args.directory / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print("Captured", len(manifest), "real REST Python programs in", args.directory)

if __name__ == "__main__":
    main()
