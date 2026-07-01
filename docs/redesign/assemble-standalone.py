#!/usr/bin/env python3
import re, pathlib

src = pathlib.Path("/Users/lawrencequan/forge-mac/docs/redesign/Forge.dc.html").read_text()

# Strip Claude-Design injected runtime + support.js (needs their environment).
src = re.sub(r'<style data-omelette-injected>.*?</style>', '', src, flags=re.S)
src = re.sub(r'<script data-omelette-injected>.*?</script>', '', src, flags=re.S)
src = re.sub(r'<script src="\./support\.js"></script>', '', src)

RUNTIME = r"""
<style>x-dc,#dc-mount{display:block;height:100vh;}</style>
<script>
(function(){
  function evalExpr(expr, scope){
    try { return Function('s','with(s){return ('+expr+');}')(scope); }
    catch(e){ return undefined; }
  }
  function hasB(str){ return /\{\{[\s\S]*?\}\}/.test(str); }
  function interp(str, scope){
    return str.replace(/\{\{([\s\S]*?)\}\}/g, function(_,e){ var v=evalExpr(e.trim(),scope); return v==null?'':v; });
  }
  window.DCLogic = class {
    constructor(){ this.state={}; }
    setState(patch){
      if(typeof patch==='function') patch = patch(this.state);
      if(patch) Object.assign(this.state, patch);
      window.__dcRender();
    }
    componentDidMount(){}
  };
  function process(node, scope, out){
    if(node.nodeType===3){ var t=node.nodeValue; out.appendChild(document.createTextNode(hasB(t)?interp(t,scope):t)); return; }
    if(node.nodeType!==1) return;
    var tag=node.tagName.toLowerCase();
    if(tag==='sc-for'){
      var m=(node.getAttribute('list')||'').match(/\{\{([\s\S]*?)\}\}/);
      var list=m?evalExpr(m[1].trim(),scope):[];
      var as=node.getAttribute('as')||'item';
      (list||[]).forEach(function(item){
        var s2=Object.create(scope); s2[as]=item;
        Array.prototype.forEach.call(node.childNodes,function(ch){ process(ch,s2,out); });
      });
      return;
    }
    if(tag==='sc-if'){
      var mm=(node.getAttribute('value')||'').match(/\{\{([\s\S]*?)\}\}/);
      if(mm && evalExpr(mm[1].trim(),scope)){
        Array.prototype.forEach.call(node.childNodes,function(ch){ process(ch,scope,out); });
      }
      return;
    }
    var el=document.createElement(tag);
    for(var i=0;i<node.attributes.length;i++){
      var a=node.attributes[i], name=a.name, val=a.value;
      if(name==='onclick'||name==='onchange'){
        var mo=val.match(/\{\{([\s\S]*?)\}\}/); var fn=mo?evalExpr(mo[1].trim(),scope):null;
        if(typeof fn==='function'){ el.addEventListener(name==='onclick'?'click':'change',(function(f){return function(e){f(e);};})(fn)); }
        continue;
      }
      if(name==='style-hover'){
        var hov=hasB(val)?interp(val,scope):val;
        (function(elem,css){ var orig=elem.getAttribute('style')||'';
          elem.addEventListener('mouseenter',function(){elem.setAttribute('style',orig+';'+css);});
          elem.addEventListener('mouseleave',function(){elem.setAttribute('style',orig);}); })(el,hov);
        continue;
      }
      if(name.indexOf('hint-')===0) continue;
      var res=hasB(val)?interp(val,scope):val;
      if(name==='value'){ el.value=res; el.setAttribute('value',res); } else { el.setAttribute(name,res); }
    }
    Array.prototype.forEach.call(node.childNodes,function(ch){ process(ch,scope,el); });
    out.appendChild(el);
  }
  window.addEventListener('DOMContentLoaded', function(){
    var xdc=document.querySelector('x-dc'); if(!xdc) return;
    var helmet=xdc.querySelector('helmet');
    if(helmet){ Array.prototype.slice.call(helmet.childNodes).forEach(function(n){ if(n.nodeType===1) document.head.appendChild(n); }); helmet.remove(); }
    var tpl=document.createElement('div');
    Array.prototype.slice.call(xdc.childNodes).forEach(function(n){ tpl.appendChild(n); });
    var mount=document.createElement('div'); mount.id='dc-mount'; xdc.appendChild(mount);
    var scriptEl=document.querySelector('script[type="text/x-dc"]');
    var ComponentClass=(0,eval)(scriptEl.textContent + '\n; Component');
    var comp=new ComponentClass();
    window.__dcComp=comp;
    var mounted=false;
    window.__dcRender=function(){
      var props; try{ props=comp.renderVals(); }catch(e){ console.error(e); props={}; }
      var frag=document.createDocumentFragment();
      Array.prototype.forEach.call(tpl.childNodes,function(n){ process(n, props, frag); });
      mount.innerHTML=''; mount.appendChild(frag);
      if(!mounted){ mounted=true; try{ comp.componentDidMount(); }catch(e){} }
    };
    window.__dcRender();
  });
})();
</script>
"""

# Insert runtime right before the component script.
src = src.replace('<script type="text/x-dc"', RUNTIME + '\n<script type="text/x-dc"', 1)

out = pathlib.Path("/Users/lawrencequan/forge-mac/forge-gui/res/redesign/forge.standalone.html")
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(src)
print("wrote", out, len(src), "bytes")
