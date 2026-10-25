"""Compile and execute captured fixtures with independent language runtimes."""
import argparse
import ast
import json
import re
import subprocess
import sys
from pathlib import Path

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('language',choices=['python','csharp','java','cpp','rust','julia','opencl','compressed','ganja','matlab_prepare'])
parser.add_argument('--evidence',type=Path,required=True,help='Directory containing generated/{plain,cse}/ fixtures')
parser.add_argument('--tools-container',default='gaalop-zero-runtime-tools',help='Running container with g++, rustc, clang; mount evidence at /work')
parser.add_argument('--julia-image',default='julia:1.10')
options=parser.parse_args()
ROOT=options.evidence.resolve()
FIX=ROOT/'generated'
WORK=ROOT/'runtime'
WORK.mkdir(exist_ok=True)
CASES=['zero','mixed','projected','sparse']
RESULTS=[]


def run(args,cwd=None):
    result=subprocess.run(args,cwd=cwd,text=True,encoding='utf-8',errors='replace',capture_output=True,timeout=180)
    if result.returncode:
        raise RuntimeError(str(args)+'\n'+result.stdout+'\n'+result.stderr)
    return result.stdout


def expected(name,case):
    if name=='z_1' or name.startswith('z$'): return 0
    if name=='a_1' or name.startswith('a$'): return 2
    if name=='v_e1' or name=='v$1': return 3 if case=='sparse' else 0
    if name.startswith('v_') or name.startswith('v$'): return 0
    raise AssertionError(name)


def python():
    import numpy as np
    count=0
    for config in ['plain','cse']:
        for mode in ['PYTHON','PYTHON_ARRAY']:
            for case in CASES:
                source=(FIX/config/mode/(case+'.txt')).read_text()
                scope={}
                exec(compile(source,mode+'/'+case,'exec'),scope)
                function=scope['zero_'+case]
                result=function(3) if case=='sparse' else function()
                if mode=='PYTHON_ARRAY':
                    names=['a','z'] if case=='mixed' else ['v'] if case in ['projected','sparse'] else ['z']
                    arrays=result if isinstance(result,tuple) else (result,)
                    for name,value in zip(names,arrays):
                        target=np.zeros(32)
                        if name=='a': target[0]=2
                        if case=='sparse': target[1]=3
                        assert np.array_equal(value,target),(mode,case,value)
                else:
                    tree=ast.parse(source)
                    ret=next(n.value for n in ast.walk(tree) if isinstance(n,ast.Return))
                    names=[ret.id] if isinstance(ret,ast.Name) else [n.id for n in ret.elts]
                    values=result if isinstance(result,tuple) else (result,)
                    assert len(names)==len(values)
                    for name,value in zip(names,values):
                        assert value==expected(name,case),(name,value)
                count+=1
    return count


def csharp():
    folder=WORK/'csharp'
    folder.mkdir(exist_ok=True)
    (folder/'RuntimeChecks.csproj').write_text('<Project Sdk="Microsoft.NET.Sdk"><PropertyGroup><OutputType>Exe</OutputType><TargetFramework>net8.0</TargetFramework></PropertyGroup></Project>')
    chunks=['using System;','using System.Runtime.CompilerServices;']
    checks=[]
    count=0
    for config in ['plain','cse']:
        for mode in ['CSHARP','CSHARP_ARRAY']:
            for case in CASES:
                name=f'{config}_{mode}_{case}'
                code=(FIX/config/mode/(case+'.txt')).read_text().replace('using System;','')
                code=code.replace('class zero_'+case,'class '+name)
                chunks.append(code)
                prefix=f'test{count}'
                if mode=='CSHARP_ARRAY':
                    outputs=['a','z'] if case=='mixed' else ['v'] if case in ['projected','sparse'] else ['z']
                    for output in outputs:
                        checks.append(f'float[] {prefix}_{output}=new float[32]; Array.Fill({prefix}_{output}, 97f);')
                    args=(['3f'] if case=='sparse' else [])+[prefix+'_'+out for out in outputs]
                    checks.append(f'{name}.Execute({", ".join(args)});')
                    for output in outputs:
                        target='(i==1 ? 3f : 0f)' if case=='sparse' else '(i==0 ? 2f : 0f)' if output=='a' else '0f'
                        checks.append(f'for(int i=0;i<32;i++) if({prefix}_{output}[i]!={target}) throw new Exception("{name}");')
                else:
                    args='3f' if case=='sparse' else ''
                    checks.append(f'var {prefix}={name}.Execute({args});')
                    ret=re.search(r'return\s*\(?([^;]+?)\)?;',code).group(1)
                    names=[s.strip().strip('()') for s in ret.split(',')]
                    for i,component in enumerate(names):
                        expression=prefix if len(names)==1 else f'((ITuple){prefix})[{i}]'
                        checks.append(f'if(Convert.ToDouble({expression})!={expected(component,case)}) throw new Exception("{name}:{component}");')
                count+=1
    chunks.append('class Program { static void Main() { '+'\n'.join(checks)+f'\nConsole.WriteLine("PASS {count}");'+' } }')
    (folder/'Program.cs').write_text('\n'.join(chunks))
    output=run(['dotnet','run','--project',str(folder/'RuntimeChecks.csproj'),'-c','Release','--nologo'])
    assert f'PASS {count}' in output,output
    return count


def java():
    folder=WORK/'java'
    folder.mkdir(exist_ok=True)
    chunks=[]
    checks=[]
    count=0
    for config in ['plain','cse']:
        for case in CASES:
            text=(FIX/config/'JAVA'/(case+'.txt')).read_text()
            files=list(re.finditer(r'/\* ([\w]+\.java) \*/\s*',text))
            name=config+'_'+case
            for i,match in enumerate(files):
                code=text[match.end():files[i+1].start() if i+1<len(files) else len(text)]
                if match[1]=='GAProgram.java': (folder/'GAProgram.java').write_text(code)
                else: (folder/(name+'.java')).write_text(code.replace('class zero_'+case,'class '+name))
            checks.append(f'{name} t{count}=new {name}();')
            if case=='sparse': checks.append(f'if(!t{count}.setValue("x$0",3)) throw new AssertionError("missing input");')
            checks.append(f't{count}.calculate();')
            expected_values={'a$0':2,'z$0':0} if case=='mixed' else {'v$1':3 if case=='sparse' else 0,'v$2':0,'v$3':0} if case in ['sparse','projected'] else {'z$0':0}
            checks.append(f'if(t{count}.getValues().size()!={len(expected_values)}) throw new AssertionError("missing output {name}");')
            for key,value in expected_values.items():
                checks.append(f'if(!t{count}.getValues().containsKey("{key}") || t{count}.getValues().get("{key}")!={value}) throw new AssertionError("{name}:{key}");')
            count+=1
    (folder/'RuntimeChecks.java').write_text('public class RuntimeChecks { public static void main(String[] args) { '+'\n'.join(checks)+f'\nSystem.out.println("PASS {count}");'+' } }')
    run(['javac',*[str(p) for p in folder.glob('*.java')]])
    output=run(['java','-cp',str(folder),'RuntimeChecks'])
    assert f'PASS {count}' in output
    return count


def native(language):
    folder=WORK/language.lower()
    folder.mkdir(exist_ok=True)
    chunks=[]
    checks=[]
    count=0
    for config in ['plain','cse']:
        for case in CASES:
            source=(FIX/config/language/(case+'.txt')).read_text()
            name=config+'_'+case
            if language=='CPP':
                chunks.append(source.replace('zero_'+case,name))
                outputs=['a','z'] if case=='mixed' else ['v'] if case in ['sparse','projected'] else ['z']
                args=['3.0f'] if case=='sparse' else []
                for output in outputs:
                    target=name+'_'+output
                    checks.append(f'float {target}[32]; for(int i=0;i<32;i++) {target}[i]=97;')
                    args.append(target)
                    expected_value='(i==1?3:0)' if case=='sparse' else '(i==0?2:0)' if output=='a' else '0'
                    checks.append(f'/*CHECK*/for(int i=0;i<32;i++) if({target}[i]!={expected_value}) return {count+1};')
                calls=[c for c in checks if not c.startswith('/*CHECK*/')]
                last=[c for c in checks if c.startswith('/*CHECK*/')]
                checks=calls+[f'{name}({", ".join(args)});']+last
            elif language=='RUST':
                chunks.append(source.replace('zero_'+case,name))
                outputs=['a','z'] if case=='mixed' else ['v'] if case in ['sparse','projected'] else ['z']
                for output in outputs: checks.append(f'let mut {name}_{output}=[97.0f32;32];')
                params=(['3.0f32'] if case=='sparse' else [])+[f'&mut {name}_{out}' for out in outputs]
                checks.append(f'{name}({", ".join(params)});')
                for output in outputs:
                    expectation='if i==1 {3.0} else {0.0}' if case=='sparse' else 'if i==0 {2.0} else {0.0}' if output=='a' else '0.0'
                    checks.append(f'for i in 0..32 {{ assert_eq!({name}_{output}[i], {expectation}); }}')
            count+=1
    if language=='CPP':
        src=folder/'check.cpp'
        src.write_text('\n'.join(chunks)+'\nint main(){\n'+'\n'.join(checks)+'\nreturn 0; }')
        run(['docker','exec',options.tools_container,'sh','-c','g++ -std=c++14 -Wall -Wextra /work/runtime/cpp/check.cpp -o /work/runtime/cpp/check && /work/runtime/cpp/check'])
    else:
        (folder/'check.rs').write_text('\n'.join(chunks)+'\nfn main(){\n'+'\n'.join(checks)+'\n}')
        run(['docker','exec',options.tools_container,'sh','-c','rustc /work/runtime/rust/check.rs -o /work/runtime/rust/check && /work/runtime/rust/check'])
    return count


def julia():
    folder=WORK/'julia'
    folder.mkdir(exist_ok=True)
    chunks=[]
    count=0
    for config in ['plain','cse']:
        for case in CASES:
            name=config+'_'+case
            code=(FIX/config/'JULIA'/(case+'.txt')).read_text().replace('zero_'+case,name)
            chunks.append(code)
            outputs=['a','z'] if case=='mixed' else ['v'] if case in ['sparse','projected'] else ['z']
            chunks.append(f'answer={name}({"3.0" if case=="sparse" else ""})')
            for i,out in enumerate(outputs):
                arr=f'answer[{i+1}]' if case=='mixed' else 'answer'
                chunks.append('expected=zeros(32)')
                if out=='a': chunks.append('expected[1]=2')
                if case=='sparse': chunks.append('expected[2]=3')
                chunks.append(f'@assert {arr} == expected')
            count+=1
    chunks.append(f'println("PASS {count}")')
    (folder/'check.jl').write_text('\n'.join(chunks))
    output=run(['docker','run','--name','gaalop-zero-julia-'+__import__('uuid').uuid4().hex[:8],'-v',str(ROOT)+':/work',options.julia_image,'julia','/work/runtime/julia/check.jl'])
    assert f'PASS {count}' in output
    return count


def matlab_prepare():
    folder=WORK/'matlab'
    folder.mkdir(exist_ok=True)
    lines=[]
    count=0
    for config in ['plain','cse']:
        for case in CASES:
            name=config+'_'+case
            code=(FIX/config/'MATLAB'/(case+'.txt')).read_text().replace('zero_'+case,name)
            (folder/(name+'.m')).write_text(code)
            if case=='mixed': lines += [f'[a,z]={name}();','assert(a(1)==2 && z(1)==0);']
            elif case=='sparse': lines += [f'v={name}(3);','assert(isequal(v,[0,3,0,0]));']
            elif case=='projected': lines += [f'v={name}();','assert(isequal(v,[0,0,0,0]));']
            else: lines += [f'z={name}();','assert(z(1)==0);']
            count+=1
    lines.append(f'disp("PASS {count}");')
    (folder/'check_zero_outputs.m').write_text('\n'.join(lines))
    return folder


def opencl():
    folder=WORK/'opencl'
    folder.mkdir(exist_ok=True)
    count=0
    for config in ['plain','cse']:
        for case in CASES:
            code=(FIX/config/'GAPP_OPENCL'/(case+'.txt')).read_text()
            path=folder/(config+'_'+case+'.cl')
            path.write_text('__kernel void probe(__global float* output, float x){\n'+code+'\noutput[0]='+('a+z' if case=='mixed' else 'v' if case in ['sparse','projected'] else 'z')+';\n}')
            run(['docker','exec',options.tools_container,'clang','-x','cl','-cl-std=CL1.2','-fsyntax-only','/work/runtime/opencl/'+path.name])
            count+=1
    return count


def compressed():
    folder=WORK/'compressed'
    folder.mkdir(exist_ok=True)
    chunks=[]
    count=0
    for config in ['plain','cse']:
        for case in CASES:
            code=(FIX/config/'COMPRESSED'/(case+'.txt')).read_text()
            body=['{','float x=3.0f;',code]
            declarations=re.findall(r'^float (\w+)(?:\[(\d+)\])?;',code,re.M)
            for name,size in declarations:
                if name=='a': body.append('if(a!=2.0f) return 1;')
                elif name=='z': body.append('if(z!=0.0f) return 2;')
                elif name=='v':
                    body.append('if(v[0]!='+('3.0f' if case=='sparse' else '0.0f')+' || v[1]!=0.0f || v[2]!=0.0f) return 3;')
                else: raise AssertionError(name)
            assert declarations,code
            body.append('}')
            chunks.extend(body)
            count+=1
    (folder/'check.cpp').write_text('int main(){\n'+'\n'.join(chunks)+'\nreturn 0;}')
    run(['docker','exec',options.tools_container,'sh','-c','g++ -std=c++14 -Wall -Wextra /work/runtime/compressed/check.cpp -o /work/runtime/compressed/check && /work/runtime/compressed/check'])
    return count


def ganja():
    count=0
    for config in ['plain','cse']:
        for case in CASES:
            code=(FIX/config/'GANJA'/(case+'.txt')).read_text()
            target=WORK/(config+'_'+case+'.js')
            target.write_text('new Function('+json.dumps(code)+');')
            run(['node',str(target)])
            count+=1
    return count


if __name__=='__main__':
    name=options.language
    if name=='matlab_prepare': print(matlab_prepare())
    else:
        function={'python':python,'csharp':csharp,'java':java,'cpp':lambda:native('CPP'),'rust':lambda:native('RUST'),'julia':julia,'opencl':opencl,'compressed':compressed,'ganja':ganja}[name]
        count=function()
        report={'language':name,'passed':count,'failed':0}
        (ROOT/('runtime-'+name+'.json')).write_text(json.dumps(report,indent=2))
        print(json.dumps(report))
