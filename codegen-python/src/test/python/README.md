# Python generator numeric runtime regression

This uv-managed test project validates numeric Python generation with NumPy. Symbolic-input support is scoped to MATLAB; this project has no SymPy or mpmath dependency. Use Python 3.12 or newer.

Generate before/after fixtures with PythonCodeGenerationTest and `-Dgaalop.python.fixtureDir=...`. From the repository root:

```powershell
uv sync --locked --project codegen-python/src/test/python
uv run --locked --project codegen-python/src/test/python codegen-python/src/test/python/run_regression.py --before C:/path/to/before --after C:/path/to/after --report C:/path/to/result.json --strict-baseline
```

The runner checks numerical reference formulas and strictly compares baseline values, types and shapes, including intermediate arrays and multiple inputs/outputs. Omit `--strict-baseline` only when the baseline still has the documented array-reset bug: fixed outputs are then checked against complete reference formulas, and broken baseline arrays are recorded separately.

`capture_service.py` captures 20 actual REST-generated programs; pass `--service` to run_regression.py for those directories. The default endpoint is `http://127.0.0.1:5174/api/v1/compile`, overridable with `--url`.

`stdlib_only.py` is a standard-library script. Run it with Python `-S` and fixture directories as arguments. It requires NumPy and SymPy to be absent from the interpreter search path, then executes all generated scalar programs.

See [maintenance documentation](../../../../docs/python-codegen-compatibility.md) for measured results, scope and build commands.
