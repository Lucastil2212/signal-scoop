(function () {
  const SCENE_BG = 0x0a0a0f;
  const legend = document.getElementById('legend');
  legend.innerHTML =
    '<span style="background:#39FF14"></span>Scan ' +
    '<span style="background:#00AEEF"></span>Place ' +
    '<span style="background:#7AE7FF"></span>Signal ' +
    '<span style="background:#7B61FF"></span>EVRUS';

  let scene, camera, renderer, nodes = [], links = [], frameId;
  const clock = new THREE.Clock();
  const raycaster = new THREE.Raycaster();
  const pointer = new THREE.Vector2();
  let selected = null;

  function init() {
    scene = new THREE.Scene();
    scene.fog = new THREE.FogExp2(SCENE_BG, 0.035);
    camera = new THREE.PerspectiveCamera(55, window.innerWidth / window.innerHeight, 0.1, 500);
    camera.position.set(0, 4, 12);

    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.setClearColor(SCENE_BG);
    document.body.appendChild(renderer.domElement);

    const ambient = new THREE.AmbientLight(0x404060, 1.2);
    scene.add(ambient);
    const key = new THREE.DirectionalLight(0x39ff14, 0.55);
    key.position.set(5, 10, 7);
    scene.add(key);
    const fill = new THREE.PointLight(0x00aeef, 0.8, 80);
    fill.position.set(-6, 2, -4);
    scene.add(fill);

    const grid = new THREE.GridHelper(24, 24, 0x1e1e2a, 0x14141c);
    grid.position.y = -3;
    scene.add(grid);

    window.addEventListener('resize', onResize);
    renderer.domElement.addEventListener('pointerdown', onPointerDown);
    animate();
  }

  function onResize() {
    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(window.innerWidth, window.innerHeight);
  }

  function clearGraph() {
    nodes.forEach(function (n) { scene.remove(n.mesh); });
    links.forEach(function (l) { scene.remove(l.line); });
    nodes = [];
    links = [];
  }

  function nodeSize(type) {
    if (type === 'SCAN') return 0.42;
    if (type === 'PLACE') return 0.55;
    if (type === 'SIGNAL') return 0.28;
    if (type === 'EVRUS') return 0.38;
    if (type === 'DEVICE') return 0.32;
    return 0.3;
  }

  function loadGraph(jsonText) {
    clearGraph();
    let data;
    try { data = JSON.parse(jsonText); } catch (e) { return; }
    if (!data.nodes || !data.links) return;

    const nodeById = {};
    data.nodes.forEach(function (n) {
      const color = new THREE.Color(n.color || '#9AA3B2');
      const geo = new THREE.SphereGeometry(nodeSize(n.type), 20, 20);
      const mat = new THREE.MeshPhongMaterial({
        color: color,
        emissive: color,
        emissiveIntensity: 0.35,
        shininess: 80,
      });
      const mesh = new THREE.Mesh(geo, mat);
      mesh.position.set(n.x || 0, n.y || 0, n.z || 0);
      mesh.userData = n;
      scene.add(mesh);
      const entry = { id: n.id, mesh: mesh, data: n, vx: 0, vy: 0, vz: 0 };
      nodes.push(entry);
      nodeById[n.id] = entry;
    });

    data.links.forEach(function (l) {
      const a = nodeById[l.source];
      const b = nodeById[l.target];
      if (!a || !b) return;
      const points = [a.mesh.position.clone(), b.mesh.position.clone()];
      const geo = new THREE.BufferGeometry().setFromPoints(points);
      const mat = new THREE.LineBasicMaterial({
        color: l.relation === 'REPEAT' ? 0xffb020 : 0x3a4a5a,
        transparent: true,
        opacity: 0.65,
      });
      const line = new THREE.Line(geo, mat);
      scene.add(line);
      links.push({ line: line, a: a, b: b, geo: geo });
    });
  }

  function simulate(dt) {
    const repulse = 0.8;
    const attract = 0.015;
    const damp = 0.92;

    for (let i = 0; i < nodes.length; i++) {
      for (let j = i + 1; j < nodes.length; j++) {
        const dx = nodes[j].mesh.position.x - nodes[i].mesh.position.x;
        const dy = nodes[j].mesh.position.y - nodes[i].mesh.position.y;
        const dz = nodes[j].mesh.position.z - nodes[i].mesh.position.z;
        const d2 = dx * dx + dy * dy + dz * dz + 0.4;
        const f = repulse / d2;
        nodes[i].vx -= dx * f; nodes[i].vy -= dy * f; nodes[i].vz -= dz * f;
        nodes[j].vx += dx * f; nodes[j].vy += dy * f; nodes[j].vz += dz * f;
      }
    }

    links.forEach(function (l) {
      const dx = l.b.mesh.position.x - l.a.mesh.position.x;
      const dy = l.b.mesh.position.y - l.a.mesh.position.y;
      const dz = l.b.mesh.position.z - l.a.mesh.position.z;
      l.a.vx += dx * attract; l.a.vy += dy * attract; l.a.vz += dz * attract;
      l.b.vx -= dx * attract; l.b.vy -= dy * attract; l.b.vz -= dz * attract;
      const pts = l.geo.attributes.position.array;
      pts[0] = l.a.mesh.position.x; pts[1] = l.a.mesh.position.y; pts[2] = l.a.mesh.position.z;
      pts[3] = l.b.mesh.position.x; pts[4] = l.b.mesh.position.y; pts[5] = l.b.mesh.position.z;
      l.geo.attributes.position.needsUpdate = true;
    });

    nodes.forEach(function (n) {
      n.vx *= damp; n.vy *= damp; n.vz *= damp;
      n.mesh.position.x += n.vx * dt;
      n.mesh.position.y += n.vy * dt;
      n.mesh.position.z += n.vz * dt;
      if (selected === n) {
        n.mesh.scale.setScalar(1.35);
      } else {
        n.mesh.scale.setScalar(1);
      }
    });
  }

  let orbitTheta = 0;
  let orbitPhi = 0.35;
  let orbitRadius = 12;
  let drag = false;
  let lastX = 0, lastY = 0;

  document.addEventListener('DOMContentLoaded', init);

  function updateCamera() {
    camera.position.x = Math.sin(orbitTheta) * Math.cos(orbitPhi) * orbitRadius;
    camera.position.y = Math.sin(orbitPhi) * orbitRadius + 1.5;
    camera.position.z = Math.cos(orbitTheta) * Math.cos(orbitPhi) * orbitRadius;
    camera.lookAt(0, 0, 0);
  }

  document.addEventListener('touchstart', function (e) {
    if (e.touches.length === 1) { drag = true; lastX = e.touches[0].clientX; lastY = e.touches[0].clientY; }
  }, { passive: true });
  document.addEventListener('touchmove', function (e) {
    if (!drag || e.touches.length !== 1) return;
    const dx = e.touches[0].clientX - lastX;
    const dy = e.touches[0].clientY - lastY;
    orbitTheta -= dx * 0.008;
    orbitPhi = Math.max(-1.2, Math.min(1.2, orbitPhi + dy * 0.008));
    lastX = e.touches[0].clientX; lastY = e.touches[0].clientY;
    updateCamera();
  }, { passive: true });
  document.addEventListener('touchend', function () { drag = false; }, { passive: true });

  function onPointerDown(ev) {
    const rect = renderer.domElement.getBoundingClientRect();
    pointer.x = ((ev.clientX - rect.left) / rect.width) * 2 - 1;
    pointer.y = -((ev.clientY - rect.top) / rect.height) * 2 + 1;
    raycaster.setFromCamera(pointer, camera);
    const hits = raycaster.intersectObjects(nodes.map(function (n) { return n.mesh; }));
    if (hits.length) {
      selected = nodes.find(function (n) { return n.mesh === hits[0].object; });
      if (window.AndroidGraph && selected) {
        window.AndroidGraph.onNodeSelected(selected.data.id, selected.data.label || '');
      }
    } else {
      selected = null;
    }
  }

  function animate() {
    frameId = requestAnimationFrame(animate);
    const dt = Math.min(clock.getDelta(), 0.05);
    if (nodes.length) simulate(dt);
    orbitTheta += 0.0015;
    updateCamera();
    renderer.render(scene, camera);
  }

  window.GraphViewer = {
    load: loadGraph,
    resetCamera: function () {
      orbitTheta = 0; orbitPhi = 0.35; orbitRadius = 12; updateCamera();
    },
  };
})();
