"""Offline single-substitution hypotheses from measured key rectangles, per layout.
No engine mutation; exact input is always first. Distances are normalized by key width.
"""
import math

def alternatives(text, keys, max_distance=1.25, limit=16):
    if not isinstance(limit,int) or limit < 0 or not 0 < max_distance <= 2:
        raise ValueError("invalid bounds")
    centers={}
    for key in keys:
        letter=key["text"]
        if len(letter)!=1 or not ("a"<=letter<="z"): continue
        x,y,w,h=(key[k] for k in ("x","y","width","height"))
        if not all(math.isfinite(v) for v in (x,y,w,h)) or w<=0 or h<=0 or letter in centers:
            raise ValueError("invalid/duplicate measured key")
        centers[letter]=(x+w/2,y+h/2,w)
    result={}
    for i,letter in enumerate(text):
        if letter not in centers: continue
        x,y,w=centers[letter]
        for target,(tx,ty,tw) in centers.items():
            if target==letter: continue
            distance=math.hypot(tx-x,ty-y)/((w+tw)/2)
            if 0 < distance <= max_distance:
                candidate=text[:i]+target+text[i+1:]
                result[candidate]=min(result.get(candidate,math.inf),distance)
    return [{"input":text,"distance_penalty":0,"exact":True}] + [
        {"input":s,"distance_penalty":d,"exact":False} for s,d in sorted(result.items(),key=lambda v:(v[1],v[0]))[:limit]]
