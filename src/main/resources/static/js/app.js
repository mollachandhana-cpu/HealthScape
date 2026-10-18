(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const state = { lat:17.385, lon:78.4867, place:'Hyderabad, India', data:null, map:null, marker:null, searchTimer:null, resizeTimer:null };
  const safe = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const fmt = (n, digits=0) => Number.isFinite(Number(n)) && Number(n) >= 0 ? Number(n).toFixed(digits) : '—';
  const setText = (id, value) => { $(id).textContent = value; };
  const aqiBand = aqi => aqi < 0 ? ['Unavailable','unknown','AQI is not available for this location.'] : aqi <= 50 ? ['Good','good','Air quality is generally satisfactory.'] : aqi <= 100 ? ['Moderate','moderate','Some sensitive people may experience effects.'] : aqi <= 150 ? ['Sensitive groups','high','Sensitive groups may experience health effects.'] : aqi <= 200 ? ['Unhealthy','high','Some members of the public may experience health effects.'] : aqi <= 300 ? ['Very unhealthy','high','Health alert: risk may affect more of the population.'] : ['Hazardous','high','Health warning: reduce outdoor exposure and follow official guidance.'];
  const uvBand = n => n < 0 ? 'Unavailable' : n < 3 ? 'Low' : n < 6 ? 'Moderate' : n < 8 ? 'High' : n < 11 ? 'Very high' : 'Extreme';
  function renderSpark(id, values, color) {
    const el=$(id), valid=values.map(Number).filter(Number.isFinite); if(valid.length<2){el.innerHTML='';return;}
    const min=Math.min(...valid), max=Math.max(...valid), span=max-min||1;
    const points=valid.map((v,i)=>`${(i/(valid.length-1))*100},${20-((v-min)/span)*17}`).join(' ');
    el.innerHTML=`<svg viewBox="0 0 100 23" preserveAspectRatio="none" aria-hidden="true"><polyline points="${points}" fill="none" stroke="${color}" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke"/></svg>`;
  }
  function drawChart(items) {
    const canvas=$('trend-chart'), wrap=canvas.parentElement, ctx=canvas.getContext('2d');
    const width=Math.max(280,wrap.clientWidth), height=Math.max(170,wrap.clientHeight), dpr=window.devicePixelRatio||1;
    canvas.width=width*dpr;canvas.height=height*dpr;canvas.style.width=width+'px';canvas.style.height=height+'px';ctx.setTransform(dpr,0,0,dpr,0,0);ctx.clearRect(0,0,width,height);
    const data=(items||[]).filter(x=>Number.isFinite(Number(x.usAqi))&&Number(x.usAqi)>=0); if(data.length<2){$('chart-empty').hidden=false;return;} $('chart-empty').hidden=true;
    const pad={l:30,r:10,t:12,b:26}, w=width-pad.l-pad.r,h=height-pad.t-pad.b;
    const maxAqi=Math.max(100,Math.ceil(Math.max(...data.map(x=>Number(x.usAqi)))/50)*50), minTemp=Math.min(...data.map(x=>Number(x.temperature)).filter(Number.isFinite)), maxTemp=Math.max(...data.map(x=>Number(x.temperature)).filter(Number.isFinite));
    ctx.font='9px DM Sans, sans-serif';ctx.lineWidth=1;
    for(let i=0;i<4;i++){const y=pad.t+h*i/3;ctx.strokeStyle='#edf2f4';ctx.beginPath();ctx.moveTo(pad.l,y);ctx.lineTo(width-pad.r,y);ctx.stroke();ctx.fillStyle='#93a3ae';ctx.textAlign='right';ctx.fillText(Math.round(maxAqi*(1-i/3)),pad.l-7,y+3);}
    const x=i=>pad.l+(i/(data.length-1))*w, ya=v=>pad.t+h-(Number(v)/maxAqi)*h;
    ctx.beginPath();data.forEach((d,i)=>i?ctx.lineTo(x(i),ya(d.usAqi)):ctx.moveTo(x(i),ya(d.usAqi)));ctx.lineWidth=2.3;ctx.strokeStyle='#1ab38a';ctx.stroke();
    const temps=data.map(d=>Number(d.temperature)).filter(Number.isFinite), span=(maxTemp-minTemp)||1;
    ctx.beginPath();data.forEach((d,i)=>{const y=pad.t+h-((Number(d.temperature)-minTemp)/span)*h*.62-h*.18;i?ctx.lineTo(x(i),y):ctx.moveTo(x(i),y);});ctx.strokeStyle='#efb35d';ctx.lineWidth=1.8;ctx.setLineDash([4,4]);ctx.stroke();ctx.setLineDash([]);
    ctx.fillStyle='#91a0ac';ctx.textAlign='center';const step=Math.max(1,Math.floor(data.length/6));
    data.forEach((d,i)=>{if(i%step===0||i===data.length-1){const raw=String(d.time||'');ctx.fillText(raw.includes('T')?raw.split('T')[1].slice(0,5):raw.slice(11,16),x(i),height-7);}});
    canvas.setAttribute('aria-label',`Next 24 hours. Current forecast US AQI ${Math.round(Number(data[0].usAqi))}; range ${Math.round(Math.min(...data.map(d=>Number(d.usAqi))))} to ${Math.round(Math.max(...data.map(d=>Number(d.usAqi))))}.`);
  }
  function renderInsights(insights) {
    const icon={air:'◉',pm25:'◌',heat:'☼',uv:'✳',humidity:'≈',check:'✓',rain:'↘',wind:'≋',visibility:'◉',dust:'✳',gas:'CO'};
    $('insight-count').textContent=`${insights.length} signal${insights.length===1?'':'s'} detected`;
    $('insight-grid').innerHTML=insights.map(i=>`<article class="insight-card ${safe(i.severity)}"><div class="insight-card-top"><span class="insight-icon">${icon[i.type]||'✳'}</span><span class="severity">${safe(i.severity)} priority</span></div><h3>${safe(i.title)}</h3><p>${safe(i.summary)}</p><div class="action-note"><b>Practical next step</b>${safe(i.action)}</div></article>`).join('');
  }
  function updateMap(lat,lon,place) {
    if(!window.L){$('map').innerHTML='<p style="padding:18px;color:#718594">Map library could not load. Use the OpenStreetMap link instead.</p>';return;}
    if(!state.map){state.map=L.map('map',{zoomControl:false,scrollWheelZoom:false}).setView([lat,lon],11);L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{attribution:'© OpenStreetMap contributors',maxZoom:18}).addTo(state.map);L.control.zoom({position:'bottomright'}).addTo(state.map);}
    state.map.setView([lat,lon],11); if(state.marker)state.map.removeLayer(state.marker);
    state.marker=L.circleMarker([lat,lon],{radius:9,color:'#fff',weight:3,fillColor:'#18aa81',fillOpacity:1}).addTo(state.map).bindPopup(`<b>${safe(place)}</b><br>${lat.toFixed(4)}, ${lon.toFixed(4)}`);
    setText('map-subtitle',`${lat.toFixed(3)}, ${lon.toFixed(3)} · ${place}`);setTimeout(()=>state.map.invalidateSize(),100);
  }
  function updateCinema(data) {
    const root=$('weather-cinema'); if(!root)return;
    const now=new Date(); const hour=now.getHours();
    const greeting=hour<12?'Good morning':hour<17?'Good afternoon':hour<21?'Good evening':'Good night';
    setText('time-greeting',`${greeting}, stay weather-aware`);
    const c=data.current||{}; const temp=Number(c.temperature); const aqi=Number(c.usAqi); const uv=Number(c.uvIndex); const rain=Number(c.precipitationProbability); const wind=Number(c.windSpeed); const place=data.place||state.place;
    const isNight=hour>=20||hour<5; let scene='clear', icon=isNight?'☾':'☀', condition=isNight?'Night conditions': 'Mostly clear';
    if(rain>=55){scene='rain';icon='🌧';condition='Rain likely';}
    else if(aqi>=151||Number(c.pm25)>=55){scene='haze';icon='🌫';condition='Air quality needs attention';}
    else if(temp>=35){scene='heat';icon='☀';condition='High heat';}
    else if(rain>=25||wind>=30){scene='cloudy';icon=rain>=25?'🌦':'☁';condition=rain>=25?'Possible showers':'Windy conditions';}
    else if(isNight){scene='night';icon='☾';condition='Night conditions';}
    else if(rain>=15){scene='cloudy';icon='⛅';condition='Partly cloudy';}
    root.dataset.scene=scene;
    setText('cinema-temp',Number.isFinite(temp)?`${Math.round(temp)}°`:'--°');
    setText('cinema-condition-label',condition);setText('cinema-location',place);setText('scene-weather-icon',icon);setText('scene-caption',`${scene.toUpperCase()} · LIVE MODEL ESTIMATE`);
    const summary=[];
    if(Number.isFinite(aqi)&&aqi>=101)summary.push('Limit strenuous outdoor activity while air quality is elevated.');
    else summary.push('Check conditions again before longer outdoor activity.');
    if(Number.isFinite(temp)&&temp>=35)summary.push('Heat is elevated: rest in shade and drink water regularly.');
    else if(Number.isFinite(uv)&&uv>=6)summary.push('UV is high: seek shade and use sun protection.');
    else if(rain>=35)summary.push('Rain may affect travel: carry protection and watch local advisories.');
    else summary.push('Use the live indicators below to plan your day.');
    setText('cinema-summary',summary.join(' '));
    setText('cinema-source-note',`Updated ${new Date(data.updatedAt||Date.now()).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'})} · forecast estimates, not a warning service`);
    const tips=[];
    if(Number.isFinite(aqi)&&aqi>=101)tips.push('Reduce strenuous outdoor activity while AQI is elevated','Sensitive groups should consider limiting prolonged outdoor exposure');
    else tips.push('Check air quality before strenuous outdoor activity','Keep windows and outdoor plans flexible as conditions change');
    if(Number.isFinite(temp)&&temp>=35)tips.push('Heat precaution: drink water, rest in shade, avoid peak afternoon heat');
    if(Number.isFinite(uv)&&uv>=6)tips.push('High UV: seek shade, wear protective clothing and use sunscreen');
    if(rain>=35)tips.push('Rain likely: carry rain protection and watch for slippery roads');
    if(wind>=35)tips.push('Strong wind: secure loose items and follow local advisories');
    tips.push('Follow official local alerts; this dashboard is informational, not an emergency warning');
    const track=$('precaution-track'); const doubled=[...tips,...tips];
    track.innerHTML=doubled.map(t=>`<span>${safe(t)}</span>`).join('');
    track.style.animationDuration=`${Math.max(25,tips.join('').length*.11)}s`;
  }
  function render(data) {
    state.data=data; const dsn=document.getElementById('data-source-name'); if(dsn&&data.provider)dsn.textContent=String(data.provider).split(' · ')[0].toUpperCase(); const c=data.current||{}; const [label,klass,caption]=aqiBand(Number(c.usAqi));
    setText('aqi-value',fmt(c.usAqi));setText('aqi-label',label);$('aqi-label').className=`status-tag ${klass}`;setText('aqi-caption',caption);
    $('aqi-marker').style.left=`${Math.max(0,Math.min(100,Number(c.usAqi)/500*100))}%`;
    setText('pm25-value',fmt(c.pm25,1));$('pm10-value').innerHTML=`${safe(fmt(c.pm10,1))} <small>µg/m³</small>`;setText('temperature-value',fmt(c.temperature,1));setText('feels-like',`Feels like ${fmt(c.feelsLike,1)}°`);setText('humidity-value',fmt(c.humidity));setText('uv-value',fmt(c.uvIndex,1));setText('uv-tag',uvBand(Number(c.uvIndex)));$('uv-fill').style.left=`${Math.max(0,Math.min(100,Number(c.uvIndex)/11*100))}%`;setText('wind-value',fmt(c.windSpeed,1));
    setText('rain-chance-value', Number(c.precipitationProbability)>=0?`${fmt(c.precipitationProbability)}%`:'—');
    setText('gust-value', Number(c.windGusts)>=0?`${fmt(c.windGusts,1)} km/h`:'—');
    setText('visibility-value', Number(c.visibility)>=0?`${(Number(c.visibility)/1000).toFixed(1)} km`:'Unavailable');
    setText('dust-value', Number(c.dust)>=0?`${fmt(c.dust,1)} µg/m³`:'Unavailable');
    setText('co-value', Number(c.carbonMonoxide)>=0?`${fmt(c.carbonMonoxide,0)} µg/m³`:'Unavailable');
    setText('pollen-value', Number(c.grassPollen)>=0?`${fmt(c.grassPollen,1)} grains/m³`:'Unavailable');
    setText('ozone-value',fmt(c.ozone,1));setText('no2-value',fmt(c.nitrogenDioxide,1));setText('so2-value',fmt(c.sulphurDioxide,1));
    renderSpark('spark-pm25',(data.hourly||[]).slice(0,12).map(x=>x.pm25),'#36bd96');renderSpark('spark-temp',(data.hourly||[]).slice(0,12).map(x=>x.temperature),'#efb35d');drawChart(data.hourly||[]);renderInsights(data.insights||[]);
    const stamp=new Date(data.updatedAt);setText('last-updated',Number.isNaN(stamp.getTime())?'Updated just now':`Fetched ${stamp.toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'})}`);setText('location-timezone',`Local time · ${data.timezone||'location time'}`);setText('side-status','Weather + air quality connected');setText('disclaimer',data.disclaimer||'Environmental data is modelled and can differ from local monitoring stations. This product is for awareness and education, not medical advice or emergency alerts.');
    updateMap(data.latitude,data.longitude,data.place);updateCinema(data);$('error-banner').hidden=true;
    loadHistory();
  }
  async function loadData() {
    const btn=$('refresh');btn.classList.add('disabled');setText('side-status','Refreshing data…');$('error-banner').hidden=true;
    try{const params=new URLSearchParams({lat:String(state.lat),lon:String(state.lon),place:state.place,persist:state.place==='Your current location'?'false':'true'});const response=await fetch(`/api/environment?${params}`,{headers:{Accept:'application/json'}});const data=await response.json();if(!response.ok)throw new Error(data.error||'Unable to load environmental data.');render(data);}
    catch(e){$('error-banner').hidden=false;$('error-banner').textContent=`Live data unavailable: ${e.message} Check your connection and retry. Existing sample dashboards are not substituted for live readings.`;setText('side-status','Provider temporarily unavailable');$('chart-empty').hidden=false;$('chart-empty').textContent='Forecast unavailable — retry when connected.';}
    finally{btn.classList.remove('disabled');}
  }
  async function loadHistory(){if(state.place==='Your current location')return;try{const p=new URLSearchParams({lat:String(state.lat),lon:String(state.lon)});const r=await fetch(`/api/environment/history?${p}`);if(!r.ok)return;const d=await r.json();const foot=document.querySelector('.chart-foot>span');if(foot)foot.innerHTML=`<i class="foot-dot"></i> ${d.count} saved observation${d.count===1?'':'s'} in TiDB · forecast shown above`;}catch(_){}}
  async function searchLocations(query) {
    const box=$('search-results');if(query.trim().length<2){box.hidden=true;return;}
    try{const r=await fetch(`/api/environment/search?q=${encodeURIComponent(query.trim())}`);if(!r.ok)throw new Error('Search unavailable');const d=await r.json();const results=d.results||[];box.innerHTML=results.length?results.map((x,i)=>`<button class="search-result" data-index="${i}"><b>${safe(x.name)}${x.admin1?`, ${safe(x.admin1)}`:''}</b><small>${safe(x.country)} · ${Number(x.latitude).toFixed(3)}, ${Number(x.longitude).toFixed(3)}</small></button>`).join(''):'<div class="search-result"><small>No locations found. Try a nearby city name.</small></div>';box.hidden=false;box.querySelectorAll('[data-index]').forEach(b=>b.addEventListener('click',()=>{const x=results[Number(b.dataset.index)];state.lat=x.latitude;state.lon=x.longitude;state.place=[x.name,x.admin1,x.country].filter(Boolean).join(', ');$('location-search').value=state.place;box.hidden=true;loadData();}));}catch(_){box.hidden=true;}
  }
  $('location-search').addEventListener('input',e=>{clearTimeout(state.searchTimer);state.searchTimer=setTimeout(()=>searchLocations(e.target.value),300);});
  $('location-search').addEventListener('keydown',e=>{if(e.key==='Escape')$('search-results').hidden=true;if(e.key==='Enter'){e.preventDefault();const first=$('search-results').querySelector('[data-index]');if(first)first.click();else searchLocations(e.target.value);}});
  document.addEventListener('click',e=>{if(!e.target.closest('.location-select'))$('search-results').hidden=true;});
  $('search-clear').addEventListener('click',()=>{$('location-search').value='';$('search-results').hidden=true;$('location-search').focus();});
  document.querySelectorAll('.quick-locations button').forEach(b=>b.addEventListener('click',()=>{state.lat=Number(b.dataset.lat);state.lon=Number(b.dataset.lon);state.place=`${b.dataset.place}, India`;$('location-search').value=state.place;loadData();}));
  $('use-location').addEventListener('click',()=>{if(!navigator.geolocation){showGeoError('This browser does not support location access. Search for a city instead.');return;}setText('side-status','Waiting for location permission…');navigator.geolocation.getCurrentPosition(pos=>{state.lat=pos.coords.latitude;state.lon=pos.coords.longitude;state.place='Your current location';$('location-search').value='';loadData();},()=>showGeoError('Location access was unavailable or denied. Search for your city instead.'),{enableHighAccuracy:false,timeout:10000,maximumAge:300000});});
  function showGeoError(message){$('error-banner').hidden=false;$('error-banner').textContent=message;setText('side-status','Choose a location');}
  $('ai-brief').addEventListener('click',async()=>{
    if(!state.data)return;const button=$('ai-brief'),box=$('ai-brief-result');button.disabled=true;button.textContent='Preparing briefing…';box.hidden=false;box.textContent='Generating an explanation from the current environmental estimates…';
    const evidence={place:state.data.place,provider:state.data.provider,current:state.data.current,insights:state.data.insights};
    try{const r=await fetch('/api/environment/ai-brief',{method:'POST',headers:{'Content-Type':'application/json','Accept':'application/json'},body:JSON.stringify(evidence)});const d=await r.json();if(!r.ok)throw new Error(d.error||'AI briefing unavailable');box.innerHTML=`<b>${safe(d.enabled?'✳ AI-generated environmental briefing':'✳ Explainable insight mode')}</b><div>${safe(d.enabled?d.brief:d.message)}</div>${d.disclaimer?`<p style="margin:10px 0 0;color:#78948b;font-size:9px">${safe(d.disclaimer)}</p>`:''}`;}
    catch(e){box.textContent=e.message;}
    finally{button.disabled=false;button.textContent='✳ Generate AI briefing';}
  });
  $('refresh').addEventListener('click',loadData);$('show-next').addEventListener('click',loadData);$('open-map').addEventListener('click',()=>window.open(`https://www.openstreetmap.org/?mlat=${state.lat}&mlon=${state.lon}#map=12/${state.lat}/${state.lon}`,'_blank','noopener,noreferrer'));
  window.addEventListener('resize',()=>{clearTimeout(state.resizeTimer);state.resizeTimer=setTimeout(()=>{if(state.data)drawChart(state.data.hourly||[]);if(state.map)state.map.invalidateSize();},120);});
  $('pause-precautions')?.addEventListener('click',()=>{const root=$('weather-cinema');const paused=root.classList.toggle('paused');$('pause-precautions').textContent=paused?'▶':'Ⅱ';$('pause-precautions').setAttribute('aria-label',paused?'Resume animated precautions':'Pause animated precautions');});
  loadData();
})();
