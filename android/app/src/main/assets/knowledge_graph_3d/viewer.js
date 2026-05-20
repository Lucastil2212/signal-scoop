(function () {
  'use strict';

  var SCENE_BG = 0x0a0a0f;
  var LABEL_MIN_DIST = 52;
  var MAX_LABELS = 14;

  var hudStats = document.getElementById('stats');
  var labelLayer = document.getElementById('labels');
  var emptyEl = document.getElementById('empty');
  var legend = document.getElementById('legend');

  legend.innerHTML =
    '<span class="item"><span class="dot" style="color:#39FF14;background:#39FF14"></span>Scan</span>' +
    '<span class="item"><span class="dot" style="color:#00AEEF;background:#00AEEF"></span>Place</span>' +
    '<span class="item"><span class="dot" style="color:#7AE7FF;background:#7AE7FF"></span>Signal</span>' +
    '<span class="item"><span class="dot" style="color:#7B61FF;background:#7B61FF"></span>EVRUS</span>';

  var scene, camera, renderer;
  var nodes = [];
  var links = [];
  var clock = null;
  var selected = null;
  var simulationFrozen = false;
  var graphCenter = { x: 0, y: 0, z: 0 };

  var orbitTheta = 0.6;
  var orbitPhi = 0.42;
  var orbitRadius = 14;
  var drag = false;
  var lastX = 0;
  var lastY = 0;

  function setStats(text) {
    if (hudStats) hudStats.innerHTML = text;
  }

  function setEmpty(show) {
    if (emptyEl) emptyEl.className = show ? 'visible' : '';
  }

  function nodeRadius(type) {
    if (type === 'SCAN') return 0.55;
    if (type === 'PLACE') return 0.7;
    if (type === 'SIGNAL') return 0.38;
    if (type === 'EVRUS') return 0.48;
    if (type === 'DEVICE') return 0.42;
    if (type === 'USER') return 0.4;
    return 0.36;
  }

  function linkColor(relation) {
    if (relation === 'REPEAT') return 0xffb020;
    if (relation === 'AT_PLACE') return 0x00aeef;
    if (relation === 'EVRUS_ID') return 0x7b61ff;
    if (relation === 'USER_NOTE') return 0xffb020;
    return 0x5a7a9a;
  }

  function truncateLabel(text, max) {
    if (!text) return '';
    text = String(text);
    return text.length <= max ? text : text.slice(0, max - 1) + '…';
  }

  function labelPriority(type, isSelected) {
    if (isSelected) return 0;
    if (type === 'SCAN') return 1;
    if (type === 'PLACE') return 2;
    if (type === 'EVRUS' || type === 'USER') return 3;
    if (type === 'DEVICE') return 4;
    return 5;
  }

  function init() {
    if (typeof THREE === 'undefined') {
      setStats('WebGL library failed to load. Reinstall the app or open <em>Graph hub → Refresh</em>.');
      setEmpty(true);
      return;
    }

    clock = new THREE.Clock();
    scene = new THREE.Scene();
    scene.fog = new THREE.FogExp2(SCENE_BG, 0.018);

    camera = new THREE.PerspectiveCamera(50, window.innerWidth / Math.max(window.innerHeight, 1), 0.1, 400);
    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false, powerPreference: 'high-performance' });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.setClearColor(SCENE_BG);
    document.body.insertBefore(renderer.domElement, document.body.firstChild);

    scene.add(new THREE.AmbientLight(0x8899bb, 1.35));
    var key = new THREE.DirectionalLight(0xffffff, 0.9);
    key.position.set(8, 14, 10);
    scene.add(key);
    var rim = new THREE.PointLight(0x39ff14, 0.65, 60);
    rim.position.set(-8, 6, -6);
    scene.add(rim);
    var fill = new THREE.PointLight(0x00aeef, 0.5, 60);
    fill.position.set(6, -2, 8);
    scene.add(fill);

    var grid = new THREE.GridHelper(28, 28, 0x252535, 0x161620);
    grid.position.y = -4;
    scene.add(grid);

    window.addEventListener('resize', onResize);
    renderer.domElement.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('touchstart', onTouchStart, { passive: true });
    document.addEventListener('touchmove', onTouchMove, { passive: true });
    document.addEventListener('touchend', onTouchEnd, { passive: true });

    updateCamera();
    animate();
    setStats('Waiting for graph data…');
  }

  function onResize() {
    if (!camera || !renderer) return;
    var w = window.innerWidth;
    var h = Math.max(window.innerHeight, 1);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    renderer.setSize(w, h);
  }

  function clearGraph() {
    nodes.forEach(function (n) {
      scene.remove(n.mesh);
      if (n.glow) scene.remove(n.glow);
    });
    links.forEach(function (l) { scene.remove(l.line); });
    nodes = [];
    links = [];
    labelLayer.innerHTML = '';
    selected = null;
    simulationFrozen = false;
  }

  function parseGraph(input) {
    if (!input) return null;
    if (typeof input === 'string') {
      try { return JSON.parse(input); } catch (e) { return null; }
    }
    return input;
  }

  function loadGraph(input) {
    if (!scene) return false;
    var data = parseGraph(input);
    if (!data || !data.nodes || !data.links) {
      setEmpty(true);
      setStats('No graph data yet.');
      return false;
    }

    clearGraph();
    if (!data.nodes.length) {
      setEmpty(true);
      setStats('No nodes — save a scan first.');
      return false;
    }

    setEmpty(false);

    var nodeById = {};
    var cx = 0, cy = 0, cz = 0;

    data.nodes.forEach(function (n, i) {
      var color = new THREE.Color(n.color || '#9AA3B2');
      var radius = nodeRadius(n.type);
      var geo = new THREE.SphereGeometry(radius, 24, 24);
      var mat = new THREE.MeshPhongMaterial({
        color: color,
        emissive: color,
        emissiveIntensity: 0.55,
        shininess: 100,
        specular: 0x444466,
      });
      var mesh = new THREE.Mesh(geo, mat);
      var x = typeof n.x === 'number' ? n.x : 0;
      var y = typeof n.y === 'number' ? n.y : 0;
      var z = typeof n.z === 'number' ? n.z : 0;
      mesh.position.set(x, y, z);
      mesh.userData = n;

      var glowGeo = new THREE.SphereGeometry(radius * 1.35, 16, 16);
      var glowMat = new THREE.MeshBasicMaterial({
        color: color,
        transparent: true,
        opacity: 0.14,
      });
      var glow = new THREE.Mesh(glowGeo, glowMat);
      glow.position.copy(mesh.position);
      scene.add(glow);
      scene.add(mesh);

      var entry = {
        id: n.id,
        mesh: mesh,
        glow: glow,
        data: n,
        baseX: x,
        baseY: y,
        baseZ: z,
        phase: i * 0.7,
      };
      nodes.push(entry);
      nodeById[n.id] = entry;
      cx += x; cy += y; cz += z;
    });

    graphCenter = {
      x: cx / data.nodes.length,
      y: cy / data.nodes.length,
      z: cz / data.nodes.length,
    };

    var linked = 0;
    data.links.forEach(function (l) {
      var a = nodeById[l.source];
      var b = nodeById[l.target];
      if (!a || !b) return;
      linked++;
      var points = [a.mesh.position.clone(), b.mesh.position.clone()];
      var geo = new THREE.BufferGeometry().setFromPoints(points);
      var mat = new THREE.LineBasicMaterial({
        color: linkColor(l.relation),
        transparent: true,
        opacity: 0.82,
        linewidth: 2,
      });
      var line = new THREE.Line(geo, mat);
      scene.add(line);
      links.push({ line: line, a: a, b: b, geo: geo });
    });

    fitCameraToGraph();
    window.setTimeout(function () { simulationFrozen = true; }, 2200);

    setStats(
      '<em>' + data.nodes.length + '</em> nodes · <em>' + linked + '</em> links · drag to orbit'
    );
    return true;
  }

  function fitCameraToGraph() {
    if (!nodes.length) return;
    var maxR = 4;
    nodes.forEach(function (n) {
      var dx = n.mesh.position.x - graphCenter.x;
      var dy = n.mesh.position.y - graphCenter.y;
      var dz = n.mesh.position.z - graphCenter.z;
      maxR = Math.max(maxR, Math.sqrt(dx * dx + dy * dy + dz * dz) + nodeRadius(n.data.type));
    });
    orbitRadius = Math.max(10, Math.min(28, maxR * 2.4));
    orbitTheta = 0.65;
    orbitPhi = 0.38;
    updateCamera();
  }

  function updateLinks() {
    links.forEach(function (l) {
      var pts = l.geo.attributes.position.array;
      pts[0] = l.a.mesh.position.x;
      pts[1] = l.a.mesh.position.y;
      pts[2] = l.a.mesh.position.z;
      pts[3] = l.b.mesh.position.x;
      pts[4] = l.b.mesh.position.y;
      pts[5] = l.b.mesh.position.z;
      l.geo.attributes.position.needsUpdate = true;
    });
  }

  function gentleLayout(dt) {
    if (simulationFrozen) {
      var t = clock.getElapsedTime();
      nodes.forEach(function (n) {
        var bob = Math.sin(t * 0.9 + n.phase) * 0.06;
        n.mesh.position.x = n.baseX;
        n.mesh.position.y = n.baseY + bob;
        n.mesh.position.z = n.baseZ;
        if (n.glow) n.glow.position.copy(n.mesh.position);
        if (selected === n) {
          n.mesh.scale.setScalar(1.28);
          if (n.glow) n.glow.scale.setScalar(1.28);
        } else {
          n.mesh.scale.setScalar(1);
          if (n.glow) n.glow.scale.setScalar(1);
        }
      });
      updateLinks();
      return;
    }

    var damp = 0.9;
    var attract = 0.045;
    var repulse = 0.35;
    var center = 0.08;

    for (var i = 0; i < nodes.length; i++) {
      for (var j = i + 1; j < nodes.length; j++) {
        var dx = nodes[j].mesh.position.x - nodes[i].mesh.position.x;
        var dy = nodes[j].mesh.position.y - nodes[i].mesh.position.y;
        var dz = nodes[j].mesh.position.z - nodes[i].mesh.position.z;
        var d2 = Math.max(dx * dx + dy * dy + dz * dz, 1.2);
        var f = repulse / d2;
        nodes[i].vx = (nodes[i].vx || 0) - dx * f;
        nodes[i].vy = (nodes[i].vy || 0) - dy * f;
        nodes[i].vz = (nodes[i].vz || 0) - dz * f;
        nodes[j].vx = (nodes[j].vx || 0) + dx * f;
        nodes[j].vy = (nodes[j].vy || 0) + dy * f;
        nodes[j].vz = (nodes[j].vz || 0) + dz * f;
      }
    }

    links.forEach(function (l) {
      var dx = l.b.mesh.position.x - l.a.mesh.position.x;
      var dy = l.b.mesh.position.y - l.a.mesh.position.y;
      var dz = l.b.mesh.position.z - l.a.mesh.position.z;
      l.a.vx = (l.a.vx || 0) + dx * attract;
      l.a.vy = (l.a.vy || 0) + dy * attract;
      l.a.vz = (l.a.vz || 0) + dz * attract;
      l.b.vx = (l.b.vx || 0) - dx * attract;
      l.b.vy = (l.b.vy || 0) - dy * attract;
      l.b.vz = (l.b.vz || 0) - dz * attract;
    });

    nodes.forEach(function (n) {
      n.vx = (n.vx || 0) - (n.mesh.position.x - n.baseX) * center;
      n.vy = (n.vy || 0) - (n.mesh.position.y - n.baseY) * center;
      n.vz = (n.vz || 0) - (n.mesh.position.z - n.baseZ) * center;
      n.vx *= damp; n.vy *= damp; n.vz *= damp;
      n.mesh.position.x += n.vx * dt;
      n.mesh.position.y += n.vy * dt;
      n.mesh.position.z += n.vz * dt;
      var lim = 12;
      n.mesh.position.x = Math.max(-lim, Math.min(lim, n.mesh.position.x));
      n.mesh.position.y = Math.max(-lim, Math.min(lim, n.mesh.position.y));
      n.mesh.position.z = Math.max(-lim, Math.min(lim, n.mesh.position.z));
      if (n.glow) n.glow.position.copy(n.mesh.position);
      n.baseX = n.mesh.position.x;
      n.baseY = n.mesh.position.y;
      n.baseZ = n.mesh.position.z;
    });
    updateLinks();
  }

  function updateLabels() {
    if (!labelLayer || !camera || !nodes.length) return;
    labelLayer.innerHTML = '';
    var w = window.innerWidth;
    var h = window.innerHeight;
    var placed = [];

    var sorted = nodes.slice().sort(function (a, b) {
      var pa = labelPriority(a.data.type, selected === a);
      var pb = labelPriority(b.data.type, selected === b);
      if (pa !== pb) return pa - pb;
      return a.mesh.position.distanceTo(camera.position) - b.mesh.position.distanceTo(camera.position);
    });

    var count = 0;
    for (var i = 0; i < sorted.length && count < MAX_LABELS; i++) {
      var n = sorted[i];
      var isSel = selected === n;
      var type = n.data.type || '';
      if (!isSel && type === 'SIGNAL' && nodes.length > 8 && count > MAX_LABELS - 4) continue;

      var pos = n.mesh.position.clone();
      pos.project(camera);
      if (pos.z > 1) continue;

      var sx = (pos.x * 0.5 + 0.5) * w;
      var sy = (-pos.y * 0.5 + 0.5) * h - 10;
      var ok = true;
      for (var p = 0; p < placed.length; p++) {
        var dx = placed[p].x - sx;
        var dy = placed[p].y - sy;
        if (dx * dx + dy * dy < LABEL_MIN_DIST * LABEL_MIN_DIST) {
          ok = false;
          break;
        }
      }
      if (!ok && !isSel) continue;

      placed.push({ x: sx, y: sy });
      var el = document.createElement('div');
      el.className = 'node-label ' + (type === 'SCAN' ? 'scan' : type === 'PLACE' ? 'place' : 'signal');
      if (isSel) el.className += ' selected';
      el.style.left = sx + 'px';
      el.style.top = sy + 'px';
      el.textContent = truncateLabel(n.data.label || n.data.rawLabel || n.id, isSel ? 22 : 16);
      labelLayer.appendChild(el);
      count++;
    }
  }

  function updateCamera() {
    if (!camera) return;
    camera.position.x = graphCenter.x + Math.sin(orbitTheta) * Math.cos(orbitPhi) * orbitRadius;
    camera.position.y = graphCenter.y + Math.sin(orbitPhi) * orbitRadius + 1.2;
    camera.position.z = graphCenter.z + Math.cos(orbitTheta) * Math.cos(orbitPhi) * orbitRadius;
    camera.lookAt(graphCenter.x, graphCenter.y, graphCenter.z);
  }

  function onTouchStart(e) {
    if (e.touches.length === 1) {
      drag = true;
      lastX = e.touches[0].clientX;
      lastY = e.touches[0].clientY;
    }
  }

  function onTouchMove(e) {
    if (!drag || e.touches.length !== 1) return;
    var dx = e.touches[0].clientX - lastX;
    var dy = e.touches[0].clientY - lastY;
    orbitTheta -= dx * 0.007;
    orbitPhi = Math.max(-1.1, Math.min(1.1, orbitPhi + dy * 0.007));
    lastX = e.touches[0].clientX;
    lastY = e.touches[0].clientY;
    updateCamera();
  }

  function onTouchEnd() { drag = false; }

  function onPointerDown(ev) {
    if (!renderer) return;
    var rect = renderer.domElement.getBoundingClientRect();
    var pointer = new THREE.Vector2(
      ((ev.clientX - rect.left) / rect.width) * 2 - 1,
      -((ev.clientY - rect.top) / rect.height) * 2 + 1
    );
    var raycaster = new THREE.Raycaster();
    raycaster.setFromCamera(pointer, camera);
    var hits = raycaster.intersectObjects(nodes.map(function (n) { return n.mesh; }));
    if (hits.length) {
      selected = nodes.find(function (n) { return n.mesh === hits[0].object; }) || null;
      if (window.AndroidGraph && selected) {
        window.AndroidGraph.onNodeSelected(
          selected.data.id || '',
          selected.data.label || selected.data.rawLabel || ''
        );
      }
    } else {
      selected = null;
    }
  }

  function animate() {
    requestAnimationFrame(animate);
    if (!renderer || !scene || !camera || !clock) return;
    var dt = Math.min(clock.getDelta(), 0.05);
    if (nodes.length) gentleLayout(dt);
    if (!drag) orbitTheta += 0.0012;
    updateCamera();
    updateLabels();
    renderer.render(scene, camera);
  }

  function loadFromBase64(b64) {
    try {
      var json = atob(b64);
      return loadGraph(json);
    } catch (e) {
      setStats('Could not decode graph.');
      return false;
    }
  }

  function loadFromAndroid() {
    try {
      if (window.GraphPayload && window.GraphPayload.getGraphPayload) {
        var raw = window.GraphPayload.getGraphPayload();
        if (raw && raw.length > 2) return loadGraph(raw);
      }
    } catch (e) { /* fallback */ }
    return false;
  }

  window.GraphViewer = {
    load: loadGraph,
    loadFromBase64: loadFromBase64,
    loadFromAndroid: loadFromAndroid,
    resetCamera: fitCameraToGraph,
    onResize: onResize,
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
