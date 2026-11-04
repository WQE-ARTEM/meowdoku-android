#!/data/data/com.termux/files/usr/bin/bash
set -e
echo "=== AutoCityBlock 1.3.5 deploy ==="
echo "Создайте пустой репозиторий на github.com (без README)."
echo "PAT scope: repo (Settings → Developer settings → PAT classic)."
echo ""
read -p "GitHub username: " GH_USER
read -p "Repo name [AutoCityBlock]: " GH_REPO
GH_REPO=${GH_REPO:-AutoCityBlock}
read -s -p "GitHub PAT: " GH_TOKEN
echo ""
read -p "Git email [${GH_USER}@users.noreply.github.com]: " GH_EMAIL
GH_EMAIL=${GH_EMAIL:-${GH_USER}@users.noreply.github.com}
echo ""
mkdir -p ~/AutoCityBLock_1.0.0
cd ~/AutoCityBLock_1.0.0
command -v git >/dev/null || pkg install -y git
mkdir -p locale/ru
# ---------- info.json ----------
cat > info.json <<'EOF'
{
  "name": "AutoCityBlock",
  "version": "1.3.5",
  "title": "AutoCityBlock",
  "author": "YourName",
  "factorio_version": "2.0",
  "dependencies": ["base >= 2.0", "space-age"]
}
EOF

# ---------- locale ----------
cat > locale/ru/locale.cfg <<'EOF'
[autocityblock]
gui.title=AutoCityBlock
gui.build=Создать чертёж
gui.close=Закрыть

[autocityblock.command]
cb_help=Открыть AutoCityBlock
EOF

# ---------- control.lua ----------
cat > control.lua <<'LUA_EOF'
-- AutoCityBlock 1.3.5 (fluid-aware + geometry-fix + fwd-ref-fix)
local function SG(o,k,d) if not o then return d end local ok,v=pcall(function() return o[k] end) if ok and v~=nil then return v end return d end
local function SS(o,k,v) if not o then return false end return pcall(function() o[k]=v end) end
local function SC(o,m,a,d) if not o then return d end local ok,v=pcall(function() local f=o[m] if type(f)~="function" then return nil end if a~=nil then return f(a) end return f() end) if ok and v~=nil then return v end return d end
local function SA(p,s) if not p then return nil end local ok,e=pcall(function() return p.add(s) end) if ok and e then return e end local f={} for k,v in pairs(s) do if k~="style" then f[k]=v end end local ok2,e2=pcall(function() return p.add(f) end) return (ok2 and e2) or nil end
local function FE(p,n) if not p or not SG(p,"valid",false) then return nil end if SG(p,"name","")==n then return p end for _,c in pairs(SG(p,"children",{})) do local f=FE(c,n) if f then return f end end return nil end

local PR={nauvis={["iron-ore"]=1,["copper-ore"]=1,["coal"]=1,["stone"]=1,["uranium-ore"]=1,["crude-oil"]=1,["water"]=1,["steam"]=1,["wood"]=1},vulcanus={["lava"]=1,["calcite"]=1,["coal"]=1,["tungsten-ore"]=1,["sulfuric-acid"]=1,["stone"]=1,["water"]=1},fulgora={["scrap"]=1,["heavy-oil"]=1,["water"]=1},gleba={["yumako"]=1,["jellynut"]=1,["stone"]=1,["water"]=1,["spoilage"]=1},aquilo={["lithium-brine"]=1,["fluorine"]=1,["ammoniacal-solution"]=1,["crude-oil"]=1,["water"]=1,["iron-ore"]=1,["copper-ore"]=1}}
local PL={"nauvis","vulcanus","fulgora","gleba","aquilo"}
local QM={1.0,1.3,1.6,1.9,2.5}
local MM={{p=0,s=0},{p=0.4,s=-0.6},{p=0,s=2.0},{p=0,s=-0.2}}

local CP={["crafting"]={"assembling-machine-3","assembling-machine-2"},["basic-crafting"]={"assembling-machine-3","assembling-machine-2"},["advanced-crafting"]={"assembling-machine-3","assembling-machine-2"},["crafting-with-fluid"]={"assembling-machine-3","assembling-machine-2"},["electronics"]={"electromagnetic-plant","assembling-machine-3"},["electronics-or-assembling"]={"electromagnetic-plant","assembling-machine-3"},["pressing"]={"assembling-machine-3","assembling-machine-2"},["metallurgy"]={"foundry","electric-furnace"},["smelting"]={"foundry","electric-furnace","steel-furnace"},["chemistry"]={"cryogenic-plant","chemical-plant"},["chemistry-or-cryogenics"]={"cryogenic-plant","chemical-plant"},["oil-processing"]={"oil-refinery"},["organic"]={"biochamber","assembling-machine-3"},["organic-or-assembling"]={"biochamber","assembling-machine-3"},["pumpjack"]={"pumpjack"}}
local MS={["assembling-machine-1"]={3,0.5,0},["assembling-machine-2"]={3,0.75,0},["assembling-machine-3"]={3,1.25,0},["electric-furnace"]={3,2.0,0},["steel-furnace"]={2,2.0,0},["chemical-plant"]={3,1.0,0},["biochamber"]={3,2.0,0.5},["electromagnetic-plant"]={4,2.0,0.5},["foundry"]={4,4.0,0.5},["cryogenic-plant"]={5,2.0,0},["oil-refinery"]={5,1.0,0},["pumpjack"]={3,1.0,0}}

local N,E,S,W=0,4,8,12

local function HasE(n) local e=SG(prototypes,"entity",nil) return e and SG(e,n,nil)~=nil end
local function HasI(n) local i=SG(prototypes,"item",nil) return i and SG(i,n,nil)~=nil end
local function IsF(n) local f=SG(prototypes,"fluid",nil) return f and SG(f,n,nil)~=nil end
local function Belt() if HasE("turbo-transport-belt") then return "turbo-transport-belt","turbo-underground-belt","turbo-splitter" elseif HasE("express-transport-belt") then return "express-transport-belt","express-underground-belt","express-splitter" end return "fast-transport-belt","fast-underground-belt","fast-splitter" end
local function Ins() if HasE("stack-inserter") then return "stack-inserter" end if HasE("bulk-inserter") then return "bulk-inserter" end return "fast-inserter" end
local function SpdM() if HasI("speed-module-3") then return "speed-module-3" end if HasI("speed-module-2") then return "speed-module-2" end if HasI("speed-module") then return "speed-module" end return nil end

-- ВАЖНО: st объявлен ДО Calc/Build/GUI (иначе forward-reference → nil())
local function st(pi)
  if not storage.player_state then storage.player_state={} end
  if not storage.player_state[pi] then storage.player_state[pi]={item="battery",rate=120,qi=1,pl=1,mi=2,beacons=true,stations=true,calc=nil} end
  return storage.player_state[pi]
end

local function MI(n) local sp=MS[n] or {3,1.0,0} local p=HasE(n) and prototypes.entity[n] or nil local s=SC(p,"get_crafting_speed","normal",nil) or SG(p,"crafting_speed",nil) return tonumber(s) or sp[2],sp[1],sp[3] end
local function BUM(f,c,uw) local cs=CP[c] or {"assembling-machine-3","assembling-machine-2"} local fr=SG(f,"recipes",{}) for _,m in ipairs(cs) do if HasE(m) then if not uw then return m end local r=SG(fr,m,nil) if not r or SG(r,"enabled",true) then return m end end end return "assembling-machine-2" end
local function FRI(f,it,uw) local fr=SG(f,"recipes",{}) local pr=SG(prototypes,"recipe",{}) local dp=SG(pr,it,nil) if dp then return dp,SG(fr,it,nil) end for n,p in pairs(pr) do for _,o in pairs(SG(p,"products",{})) do if SG(o,"name",nil)==it then return p,SG(fr,n,nil) end end end return nil,nil end

local function CC(f,it,rps,s,steps,ri,rf,d)
  d=d or 0
  local rm=PR[PL[s.pl]] or PR.nauvis
  local bk=IsF(it) and rf or ri
  if d>10 or rm[it] then bk[it]=(bk[it] or 0)+rps return end
  local p,fr=FRI(f,it,s.uw) if not p then bk[it]=(bk[it] or 0)+rps return end
  if s.uw and fr and not SG(fr,"enabled",true) then bk[it]=(bk[it] or 0)+rps return end
  local rn=SG(p,"name",it)
  local bo=1 for _,pp in pairs(SG(p,"products",{})) do if SG(pp,"name",nil)==it then local a=SG(pp,"amount",nil) or ((SG(pp,"amount_min",1)+SG(pp,"amount_max",1))/2) bo=a*SG(pp,"probability",1) break end end
  local cat=SG(p,"category","crafting") local mn=BUM(f,cat,s.uw) local bs,msz,ip=MI(mn)
  local md=MM[s.mi] or MM[1] local pb=ip+(md.p or 0)
  local spm=math.max(0.25,1.0+(md.s or 0)+(s.beacons and 1.5 or 0))
  local fs=bs*QM[s.qi]*spm
  local rpc=bo*(1+pb) local cps=rps/math.max(0.001,rpc)
  local en=tonumber(SG(p,"energy",0.5)) or 0.5
  local em=(cps*en)/math.max(0.01,fs)
  local ii,fi={},{} for _,ing in pairs(SG(p,"ingredients",{})) do local inn=SG(ing,"name",nil) local ia=tonumber(SG(ing,"amount",1)) or 1 if inn then if IsF(inn) then fi[#fi+1]={name=inn,amount=ia} else ii[#ii+1]={name=inn,amount=ia} end end end
  if not steps[rn] then steps[rn]={rn=rn,it=it,mn=mn,ms=msz,em=0,ops=0,cps=0,pb=pb,sp=fs,ing=ii,fing=fi,ord=d} end
  local sp=steps[rn] sp.em=sp.em+em sp.ops=sp.ops+rps sp.cps=sp.cps+cps
  for _,g in ipairs(ii) do CC(f,g.name,cps*g.amount,s,steps,ri,rf,d+1) end
  for _,g in ipairs(fi) do CC(f,g.name,cps*g.amount,s,steps,ri,rf,d+1) end
end

local function Calc(p)
  local s=st(p.index) if not s.item then s.item="battery" end
  local steps,ri,rf={},{},{} CC(p.force,s.item,((s.rate*1.01)/60.0),s,steps,ri,rf,1)
  if next(steps)==nil then if IsF(s.item) then rf[s.item]=s.rate/60 else ri[s.item]=s.rate/60 end end
  local sd={} for _,x in pairs(steps) do sd[#sd+1]=x end
  table.sort(sd,function(a,b) return a.ord>b.ord end)
  s.calc={steps=sd,ri=ri,rf=rf}
end

local function Build(p)
  local s=st(p.index) Calc(p) local c=s.calc if not c then return end
  local bl,bug,bsp=Belt() local ins=Ins() local spm=SpdM()
  local ens,tls={},{}
  local function AE(t) if not HasE(t.name) then return end
    if t.recipe then local r=prototypes.recipe[t.recipe] local fr=p.force.recipes[t.recipe] if not r or not fr or not fr.enabled then p.print("[ACB] '"..t.recipe.."' заблокирован — пропуск") return end end
    t.no_quality=nil t.trains_limit=nil ens[#ens+1]=t end
  local function AT(n,x,y) tls[#tls+1]={name=n,position={x=x,y=y}} end

  local HALF=48 local busy=-HALF+8 local ctop=busy+8 local cmax=HALF-14 local tapy=busy+3

  for lane=0,3 do for x=-HALF+4,HALF-5 do AE({name=bl,position={x=x,y=busy+lane},direction=E}) end end

  local cx=-HALF+6 local placed=0 local skipped=0
  for _,sp in ipairs(c.steps) do
    local msz=sp.ms or 3 local mh=math.floor(msz/2) local pc=math.max(1,math.ceil(math.ceil(sp.em)/2))
    local lmx=cx+mh local ccx=lmx+mh+2 local rmx=ccx+mh+2 local cw=math.max(msz*2+9,15)
    if rmx+mh+3>HALF-4 then skipped=skipped+1 else
      placed=placed+1
      AE({name=bsp,position={x=ccx-3,y=tapy},direction=E})
      AE({name=bug,position={x=ccx-1,y=tapy},direction=E})
      AE({name=bl, position={x=ccx,y=tapy},direction=S})
      AE({name=bug,position={x=ccx+1,y=tapy},direction=E})
      AE({name=bl, position={x=ccx+2,y=tapy},direction=E})
      local cb=ctop+pc*(msz+2)+2 if cb>cmax then cb=cmax end
      for y=tapy+1,cb do AE({name=bl,position={x=ccx,y=y},direction=S}) end
      local hf=#sp.fing>0
      for i=0,pc-1 do
        local my=ctop+i*(msz+2)+mh if my+mh>cmax then break end
        AE({name=sp.mn,position={x=lmx,y=my},recipe=sp.rn,direction=W})
        AE({name=ins,  position={x=lmx+mh+1,y=my},direction=W})
        AE({name=sp.mn,position={x=rmx,y=my},recipe=sp.rn,direction=E})
        AE({name=ins,  position={x=rmx-mh-1,y=my},direction=E})
        if i%2==0 then AE({name="medium-electric-pole",position={x=ccx,y=my}}) end
        if s.beacons and spm then
          AE({name="beacon",position={x=lmx-mh-1,y=my},items={[spm]=2}})
          AE({name="beacon",position={x=rmx+mh+1,y=my},items={[spm]=2}})
        end
        if hf then
          AE({name="pipe-to-ground",position={x=lmx-mh-2,y=my},direction=S})
          AE({name="pipe-to-ground",position={x=rmx+mh+2,y=my},direction=N})
        end
      end
      cx=cx+cw
    end
  end
  if skipped>0 then p.print("[ACB] "..skipped.." колонн не влезло — уменьшите rate") end

  for x=-HALF,HALF-1 do for y=-HALF,HALF-1 do
    local ed=(x==-HALF or y==-HALF or x==HALF-1 or y==HALF-1)
    AT(ed and "refined-hazard-concrete-left" or "refined-concrete",x,y)
  end end

  if s.stations and HasE("straight-rail") then
    local rt=busy-6-((busy-6)%2) local rb=HALF-8-((HALF-8)%2) local rl=-HALF+4-((-HALF+4)%2) local rr=HALF-4-((HALF-4)%2)
    for rx=rl,rr,2 do AE({name="straight-rail",position={x=rx,y=rt},direction=E}) AE({name="straight-rail",position={x=rx,y=rb},direction=E}) end
    for ry=rt+2,rb-2,2 do AE({name="straight-rail",position={x=rl,y=ry},direction=N}) AE({name="straight-rail",position={x=rr,y=ry},direction=N}) end
    AE({name="rail-chain-signal",position={x=rl+4,y=rt+2}})
    AE({name="rail-signal",position={x=rr-4,y=rt+2}})
    AE({name="train-stop",position={x=rl+16,y=rt+2},direction=E,station=string.format("[out] %s %d/м",s.item,s.rate),control_behavior={trains_limit_enabled=true,trains_limit=2}})
    local idx=0
    for rn in pairs(c.ri) do idx=idx+1 if idx>4 then break end
      local sx=rl+6+(idx-1)*14
      AE({name="train-stop",position={x=sx,y=rb-2},direction=W,station="[in] "..rn,control_behavior={trains_limit_enabled=true,trains_limit=2}})
      AE({name="steel-chest",position={x=sx+3,y=rb-2}})
      AE({name=ins,position={x=sx+2,y=rb-2},direction=E})
      AE({name=ins,position={x=sx+4,y=rb-2},direction=W})
    end
    for rn in pairs(c.rf) do idx=idx+1 if idx>6 then break end
      local sx=rl+6+(idx-1)*14
      AE({name="train-stop",position={x=sx,y=rb-2},direction=W,station="[in] "..rn,control_behavior={trains_limit_enabled=true,trains_limit=2}})
      AE({name="storage-tank",position={x=sx+4,y=rb-2}})
      AE({name="pump",position={x=sx+2,y=rb-2},direction=W})
    end
  end

  for i,e in ipairs(ens) do e.entity_number=i end
  p.clear_cursor()
  p.cursor_stack.set_stack({name="blueprint",count=1})
  local ok,err=pcall(function() p.cursor_stack.set_blueprint_entities(ens) end)
  if not ok then p.print("[ACB] Ошибка: "..tostring(err)) return end
  pcall(function() p.cursor_stack.set_blueprint_tiles(tls) end)
  pcall(function() p.cursor_stack.label=string.format("ACB 96×96 [%s]",s.item) end)
  p.print(string.format("[ACB] Готово: %d объектов, %d шагов, %d колонн",#ens,#c.steps,placed))
end

local function GUI(p)
  if not p or not SG(p,"valid",false) then return end
  local scr=SG(p.gui,"screen",nil) if not scr then return end
  local old=SG(scr,"cb_frame",nil) if old then pcall(function() old.destroy() end) end
  local s=st(p.index) Calc(p) local c=s.calc
  local f=SA(scr,{type="frame",name="cb_frame",direction="vertical",caption="AutoCityBlock 1.3.5"})
  if not f then return end SS(f,"auto_center",true) SS(f.style,"minimal_width",900)

  local t=SA(f,{type="flow",direction="horizontal"})
  SA(t,{type="label",caption="Предмет:"})
  SA(t,{type="choose-elem-button",name="cb_item",elem_type="item",item=s.item})
  SA(t,{type="label",caption="  Кол-во/м:"})
  local tf=SA(t,{type="textfield",name="cb_rate",text=tostring(s.rate),numeric=true}) if tf then SS(tf.style,"width",65) end

  local qcap={"Обычное","+30%","+60%","+90%","+150%"}
  SA(t,{type="label",caption="  Качество:"}) SA(t,{type="drop-down",name="cb_q",items=qcap,selected_index=s.qi})
  SA(t,{type="label",caption="  Планета:"}) SA(t,{type="drop-down",name="cb_pl",items={"Наувис","Вулкан","Фульгора","Глеба","Аквило"},selected_index=s.pl})

  local t2=SA(f,{type="flow",direction="horizontal"})
  local mcap={"Без модулей","4x Продуктивность","4x Скорость","4x Качество"}
  SA(t2,{type="label",caption="Модули:"}) SA(t2,{type="drop-down",name="cb_m",items=mcap,selected_index=s.mi})
  SA(t2,{type="checkbox",name="cb_b",caption=" ⚡ Маяки",state=s.beacons})
  SA(t2,{type="checkbox",name="cb_st",caption=" 🚂 ЖД",state=s.stations})

  local sc=SA(f,{type="scroll-pane",direction="vertical"}) if sc then SS(sc.style,"maximal_height",320) end
  local tbl=SA(sc or f,{type="table",column_count=5,draw_horizontal_lines=true})
  if tbl then
    for _,h in ipairs({"Этап","Станок","Заводов","Прод.","Выход"}) do SA(tbl,{type="label",caption=" "..h.." "}) end
    if c and c.steps then for _,st2 in ipairs(c.steps) do
      SA(tbl,{type="label",caption=st2.rn})
      SA(tbl,{type="label",caption=st2.mn})
      SA(tbl,{type="label",caption=string.format("%d",math.ceil(st2.em))})
      SA(tbl,{type="label",caption=string.format("+%d%%",math.floor(st2.pb*100))})
      SA(tbl,{type="label",caption=string.format("%.1f/м",st2.ops*60)})
    end end
  end

  local bot=SA(f,{type="flow",direction="horizontal"})
  SA(bot,{type="button",name="cb_recalc",caption="🔄 Пересчитать"})
  SA(bot,{type="button",name="cb_build",caption="📐 Создать чертёж",style="confirm_button"})
  SA(bot,{type="button",name="cb_close",caption="Закрыть"})
end

local function topbtn(p) if not p or not SG(p,"valid",false) then return end local s=SG(p.gui,"screen",nil) if s and not SG(s,"cb_top_toggle_btn",nil) then SA(s,{type="button",name="cb_top_toggle_btn",caption="⚙️ AutoCityBlock 1.3.5"}) end end

script.on_init(function() storage.player_state={} for _,p in pairs(game.players) do topbtn(p) end end)
script.on_configuration_changed(function() if not storage.player_state then storage.player_state={} end for _,p in pairs(game.players) do topbtn(p) end end)
script.on_event(defines.events.on_player_created,function(e) topbtn(game.get_player(e.player_index)) end)

commands.add_command("cb","Открыть AutoCityBlock",function(e) GUI(game.get_player(e.player_index)) end)

script.on_event(defines.events.on_gui_click,function(e) pcall(function()
  local el=e.element if not el or not SG(el,"valid",false) then return end
  local n=SG(el,"name","") local p=game.get_player(e.player_index)
  local f=SG(p.gui.screen,"cb_frame",nil)
  if f then local ri=FE(f,"cb_rate") if ri then st(p.index).rate=tonumber(SG(ri,"text","120")) or 120 end end
  if n=="cb_top_toggle_btn" or n=="cb_recalc" then GUI(p)
  elseif n=="cb_build" then Build(p) if f and f.valid then f.destroy() end
  elseif n=="cb_close" then if f and f.valid then f.destroy() end end
end) end)

script.on_event(defines.events.on_gui_elem_changed,function(e) pcall(function()
  local el=e.element if el and SG(el,"valid",false) and SG(el,"name","")=="cb_item" and SG(el,"elem_value",nil) then
    st(e.player_index).item=SG(el,"elem_value",nil) GUI(game.get_player(e.player_index))
  end
end) end)

script.on_event(defines.events.on_gui_selection_state_changed,function(e) pcall(function()
  local el=e.element if not el or not SG(el,"valid",false) then return end
  local s=st(e.player_index) local n=SG(el,"name","")
  if n=="cb_q" then s.qi=SG(el,"selected_index",1)
  elseif n=="cb_pl" then s.pl=SG(el,"selected_index",1)
  elseif n=="cb_m" then s.mi=SG(el,"selected_index",1) end
  GUI(game.get_player(e.player_index))
end) end)

script.on_event(defines.events.on_gui_checked_state_changed,function(e) pcall(function()
  local el=e.element if not el or not SG(el,"valid",false) then return end
  local s=st(e.player_index) local n=SG(el,"name","")
  if n=="cb_b" then s.beacons=SG(el,"state",true)
  elseif n=="cb_st" then s.stations=SG(el,"state",true) end
  GUI(game.get_player(e.player_index))
end) end)
LUA_EOF

echo "OK: control.lua = $(wc -l < control.lua) строк"
# ---------- git push ----------
if [ ! -d .git ]; then git init -q; git branch -M main; fi
git add -A
git -c user.email="$GH_EMAIL" -c user.name="$GH_USER" commit -m "v1.3.5: fluid-aware + geometry + fwd-ref fix" || echo "(уже закоммичено)"
git remote remove origin 2>/dev/null || true
git remote add origin "https://${GH_USER}:${GH_TOKEN}@github.com/${GH_USER}/${GH_REPO}.git"
git push -u origin main
