import json, time, subprocess, urllib.request, pathlib, traceback
w=pathlib.Path('/work')
cp=(w/'classpath-linux.txt').read_text().strip()
log=open(w/'memory-api.log','w')
p=subprocess.Popen(['java','-Xmx768m','-Dgaalop.garamon.nativeDir=/work/install','-Dgaalop.app.home=/work/memory-runtime','-cp',cp,'de.gaalop.rest.GaalopRestApplication','--server.port=18080','--server.address=127.0.0.1'],stdout=log,stderr=subprocess.STDOUT)
def metrics():
    d={}
    for l in pathlib.Path('/proc/%d/status'%p.pid).read_text().splitlines():
        if l.startswith(('VmRSS:','VmHWM:')): d[l.split(':')[0]+'_KiB']=int(l.split()[1])
    for f in ['memory.current','memory.peak','memory.events','memory.max','memory.swap.max']:
        q=pathlib.Path('/sys/fs/cgroup')/f
        if q.exists(): d[f]=q.read_text().strip()
    return d
results=[]
try:
    for attempt in range(120):
        if p.poll() is not None: raise RuntimeError('API exited')
        try:
            urllib.request.urlopen('http://127.0.0.1:18080/gaalop/api/v1/compile',timeout=1)
        except urllib.error.HTTPError: break
        except Exception: time.sleep(1)
    print('BASELINE',json.dumps(metrics()),flush=True)
    prefix='i=er1*er2;\n'+''.join('f%d=0.5*(e%d+i*e%d); f%dT=0.5*(e%d-i*e%d);\n'%(k,k,9+k,k,k,9+k) for k in range(1,10))
    prefix+='Id='+'*'.join('f%d*f%dT'%(k,k) for k in range(1,10))+';\n'
    cases=[('basis', '?psi=Id;', [0]),('ghz','?psi=(Id+'+'*'.join('f%dT'%k for k in range(1,10))+'*Id)/sqrt(2);',[0,511]),('uniform','psi=Id;\n'+''.join('psi=(psi+f%dT*psi)/sqrt(2);\n'%k for k in range(1,10))+'?psi;',list(range(512)))]
    for name,tail,indices in cases:
        payload={'algebraPlugins':'ALGEBRA_QRA','algebraDimension':9,'codegenPlugins':'JAVA','outputMode':'CODE_AND_VISUALIZATION','visualizationEnabled':True,'optimization':{'cse':False,'maxima':False},'script':{'functionName':'memory_'+name,'optimizeCode':prefix+tail,'variableAssignments':'','multivectorsVisualized':':psi;'}}
        (w/('memory-'+name+'-request.json')).write_text(json.dumps(payload,indent=2))
        start=time.monotonic()
        req=urllib.request.Request('http://127.0.0.1:18080/gaalop/api/v1/compile',json.dumps(payload).encode(),{'Content-Type':'application/json'})
        try:
            with urllib.request.urlopen(req,timeout=240) as response: data=json.load(response)
            (w/('memory-'+name+'-response.json')).write_text(json.dumps(data))
            state=data['quantumResults']['psi']; probs=state['probabilities']
            error=max(abs(v-(1/len(indices) if j in indices else 0)) for j,v in enumerate(probs))
            row={'case':name,'seconds':time.monotonic()-start,'probabilityError':error,'totalProbability':state['totalProbability'],'residualNorm':state['residualNorm'],'metrics':metrics()}
            assert error<1e-8 and abs(state['totalProbability']-1)<1e-8 and state['residualNorm']<1e-8
        except Exception as e:
            row={'case':name,'seconds':time.monotonic()-start,'error':str(e),'metrics':metrics()}
        results.append(row); print(json.dumps(row),flush=True)
        (w/'memory-results.json').write_text(json.dumps(results,indent=2))
finally:
    p.terminate()
    try:p.wait(timeout=15)
    except subprocess.TimeoutExpired:p.kill()
