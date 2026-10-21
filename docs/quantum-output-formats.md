# QuantumGAALOP output formats

The quantum toolbar offers 13 generators: Java, C/C++, Python, C#, Rust, Julia, MATLAB, Mathematica, GAALOPScript, C/C++ (gaalet), compressed C/C++, Graphviz DOT and LaTeX.

`QuantumLanguagesTest` checks named generated output for a scalar/vector expression in each of the three algebras and 13 generators. Native execution and probability projection have separate tests. QRA evaluates numeric inputs and emits constants; QCA and quantum bit geometric algebra use TBA code generation.

MATLAB has executable numeric and symbolic fixtures covering intermediate arrays and multiple inputs/outputs. See [MATLAB compatibility](matlab-symbolic-codegen.md). Python has complete numeric math mappings, demand-driven imports and NumPy array fixtures; its web default uses scalar/tuple output and standard-library math. See [Python compatibility](python-codegen-compatibility.md). Symbolic-input integration uses MATLAB.

Geometric rendering belongs to the traditional Ganja preview. The quantum page renders probability data from the backend. GAPP generators use a separate intermediate optimization pipeline, while the legacy Verilog, VIS2D, Visualizer and debugger plugins belong to the desktop build.

Deploy the frontend directories and REST JAR together, using compatible QRA native libraries and the existing backend environment settings.
