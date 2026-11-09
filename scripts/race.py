# Prueba de concurrencia (Parte 4): python scripts/race.py same|diff <rondas>
# same: 2 despachos simultaneos del mismo pedido; diff: 2 pedidos distintos con un unico AMR libre.
import http.client, json, threading, sys, collections
H={'Content-Type':'application/json'}
def req(m,p,b=None):
    c=http.client.HTTPConnection('localhost',8080,timeout=60); c.request(m,p,json.dumps(b) if b else None,H)
    r=c.getresponse(); d=r.read().decode(); c.close()
    try: d=json.loads(d)
    except: pass
    return r.status,d
def race(paths):
    n=len(paths); bar=threading.Barrier(n); out=[None]*n
    conns=[http.client.HTTPConnection('localhost',8080,timeout=60) for _ in paths]
    for c in conns: c.connect()
    def w(i):
        bar.wait(); conns[i].request('POST',paths[i],None,H); r=conns[i].getresponse(); d=r.read().decode()
        try: d=json.loads(d)
        except: pass
        out[i]=(r.status,d)
    ts=[threading.Thread(target=w,args=(i,)) for i in range(n)]
    [t.start() for t in ts]; [t.join() for t in ts]; return out
import time
def release(ids):
    for oid in ids:
        for _ in range(40):
            st,d=req('GET','/api/orders/%d'%oid)
            if isinstance(d,dict) and d.get('status')=='DISPATCHED': time.sleep(0.15); continue
            break
        if isinstance(d,dict) and d.get('status')=='IN_TRANSIT': req('POST','/api/orders/%d/transitions'%oid,{'to':'DELIVERED'})
        elif isinstance(d,dict) and d.get('status')=='DISPATCHED': req('POST','/api/orders/%d/transitions'%oid,{'to':'CANCELLED'})
    for r in req('GET','/api/robots')[1]:
        if r['status']=='IDLE': req('POST','/api/robots/%d/charge'%r['id'])
def new(tag): return req('POST','/api/orders',{'customer':tag,'destX':10,'destY':5})[1]['id']
mode=sys.argv[1]; rounds=int(sys.argv[2])
stats=collections.Counter(); samples={}
if mode=='same':
    for i in range(rounds):
        oid=new('same%d'%i)
        res=race(['/api/orders/%d/dispatch'%oid]*2)
        key=tuple(sorted((s,(d.get('error') or ('OK robot='+str(d.get('robot')))) if isinstance(d,dict) else str(d)) for s,d in res))
        stats[key]+=1
        release([oid])
elif mode=='diff':
    # deja exactamente 1 AMR elegible libre
    fill=[new('fill%d'%i) for i in range(3)]
    for f in fill: req('POST','/api/orders/%d/dispatch'%f)
    for i in range(rounds):
        a,b=new('A%d'%i),new('B%d'%i)
        res=race(['/api/orders/%d/dispatch'%a,'/api/orders/%d/dispatch'%b])
        key=tuple(sorted((s,(d.get('error') or ('OK robot='+str(d.get('robot')))) if isinstance(d,dict) else str(d)) for s,d in res))
        stats[key]+=1
        release([a,b])
for k,v in stats.items(): print(v,'x',str(k).encode('ascii','replace').decode())
