import json, time, subprocess, urllib.request, urllib.error, pathlib, math, re, socket, hashlib
w=pathlib.Path('/work'); cp=(w/'classpath-linux.txt').read_text().strip()
p=subprocess.Popen(['java','-Xmx768m','-Dgaalop.garamon.nativeDir=/work/install','-Dgaalop.app.home=/work/resource-runtime','-cp',cp,'de.gaalop.rest.GaalopRestApplication','--server.port=18080','--server.address=127.0.0.1'],stdout=open(w/'resource-api.log','w'),stderr=subprocess.STDOUT)
def metrics():
    d={}
    for line in pathlib.Path('/proc/%d/status'%p.pid).read_text().splitlines():
        if line.startswith(('VmRSS:','VmHWM:')): d[line.split(':')[0]+'_KiB']=int(line.split()[1])
    for name in ['memory.current','memory.peak','memory.events','memory.max','memory.swap.max']:
        d[name]=(pathlib.Path('/sys/fs/cgroup')/name).read_text().strip()
    return d
rows=[]
try:
    for _ in range(120):
        if p.poll() is not None: raise RuntimeError('API exited during startup')
        try:
            with socket.create_connection(('127.0.0.1',18080),timeout=1): break
        except OSError: time.sleep(1)
    print('BASELINE',json.dumps(metrics()),flush=True)
    for file in sorted(pathlib.Path('/resources').glob('Grover_n*.txt')):
        source=file.read_text(encoding='utf-8-sig'); n=int(re.search(r'_n(\d+)',file.name)[1])
        marked=[int(x) for x in re.search(r'_s([\d_]+)\.txt',file.name)[1].split('_')]
        iterations=int(re.search(r'Iterations needed:\s*(\d+)',source)[1])
        payload={'algebraPlugins':'ALGEBRA_QRA','algebraDimension':n,'codegenPlugins':'JAVA','outputMode':'CODE_AND_VISUALIZATION','visualizationEnabled':True,'optimization':{'cse':False,'maxima':False},'script':{'functionName':'grover_test','optimizeCode':source,'variableAssignments':'','multivectorsVisualized':':res;'}}
        (w/(file.stem+'-request.json')).write_text(json.dumps(payload,indent=2))
        start=time.monotonic(); row={'file':file.name,'n':n,'iterations':iterations,'marked':marked,'sha256':hashlib.sha256(file.read_bytes()).hexdigest()}
        try:
            req=urllib.request.Request('http://127.0.0.1:18080/gaalop/api/v1/compile',json.dumps(payload).encode(),{'Content-Type':'application/json'})
            with urllib.request.urlopen(req,timeout=240) as response: data=json.load(response)
            (w/(file.stem+'-response.json')).write_text(json.dumps(data))
            state=data['quantumResults']['res']; theta=math.asin(math.sqrt(len(marked)/(2**n)))
            expected_success=math.sin((2*iterations+1)*theta)**2
            expected=[expected_success/len(marked) if j in marked else (1-expected_success)/(2**n-len(marked)) for j in range(2**n)]
            error=max(abs(a-b) for a,b in zip(state['probabilities'],expected))
            assert len(state['probabilities'])==2**n
            row.update(successProbability=sum(state['probabilities'][j] for j in marked),expectedSuccessProbability=expected_success,maxProbabilityError=error,totalProbability=state['totalProbability'],residualNorm=state['residualNorm'],generatedCodeCharacters=len(data.get('optimizeResult','')))
            assert error<1e-8 and abs(state['totalProbability']-1)<1e-8 and state['residualNorm']<1e-8
            row['passed']=True
        except Exception as e:
            row.update(passed=False,error=str(e))
            if isinstance(e,urllib.error.HTTPError):row['httpBody']=e.read().decode()
        row.update(seconds=time.monotonic()-start,metrics=metrics()); rows.append(row)
        print(json.dumps(row),flush=True); (w/'resource-results.json').write_text(json.dumps(rows,indent=2))
finally:
    p.terminate()
    try:p.wait(timeout=15)
    except subprocess.TimeoutExpired:p.kill();p.wait()
if not rows or not all(r['passed'] for r in rows): raise SystemExit(1)
