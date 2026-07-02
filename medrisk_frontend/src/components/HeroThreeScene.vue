<template>
  <div class="hero-three-scene" aria-hidden="true">
    <div v-if="!webglReady" class="hero-three-fallback"></div>
    <div ref="hostRef" class="hero-three-canvas"></div>
  </div>
</template>

<script setup lang="ts">
import * as THREE from 'three'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { canUseWebGL, disposeObject } from '../utils/threeSupport'

const hostRef = ref<HTMLDivElement>()
const webglReady = ref(false)

let renderer: THREE.WebGLRenderer | undefined
let scene: THREE.Scene | undefined
let camera: THREE.PerspectiveCamera | undefined
let frame = 0
let points: THREE.Points | undefined
let helix: THREE.Group | undefined

function initScene() {
  if (!hostRef.value || !canUseWebGL()) return
  webglReady.value = true
  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(45, 1, 0.1, 100)
  camera.position.set(0, 0.4, 8)

  renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.8))
  hostRef.value.appendChild(renderer.domElement)

  const particles = new Float32Array(420 * 3)
  for (let index = 0; index < 420; index += 1) {
    particles[index * 3] = (Math.random() - 0.5) * 11
    particles[index * 3 + 1] = (Math.random() - 0.5) * 6
    particles[index * 3 + 2] = (Math.random() - 0.5) * 5
  }
  const pointGeometry = new THREE.BufferGeometry()
  pointGeometry.setAttribute('position', new THREE.BufferAttribute(particles, 3))
  points = new THREE.Points(
    pointGeometry,
    new THREE.PointsMaterial({ color: '#d8f8ff', size: 0.035, transparent: true, opacity: 0.72 })
  )
  scene.add(points)

  helix = new THREE.Group()
  const strandMaterial = new THREE.MeshStandardMaterial({ color: '#94f5dc', roughness: 0.42, metalness: 0.15 })
  const markerMaterial = new THREE.MeshStandardMaterial({ color: '#c7d2fe', roughness: 0.36, metalness: 0.2 })
  for (let index = 0; index < 28; index += 1) {
    const angle = index * 0.48
    const y = (index - 14) * 0.145
    const left = new THREE.Mesh(new THREE.SphereGeometry(0.06, 16, 16), strandMaterial)
    const right = new THREE.Mesh(new THREE.SphereGeometry(0.06, 16, 16), strandMaterial)
    left.position.set(Math.cos(angle) * 1.2, y, Math.sin(angle) * 1.2)
    right.position.set(Math.cos(angle + Math.PI) * 1.2, y, Math.sin(angle + Math.PI) * 1.2)
    const bridge = new THREE.Mesh(new THREE.CylinderGeometry(0.012, 0.012, 2.4, 8), markerMaterial)
    bridge.position.set(0, y, 0)
    bridge.rotation.z = Math.PI / 2
    bridge.rotation.y = -angle
    helix.add(left, right, bridge)
  }
  helix.position.set(1.2, 0.2, 0)
  helix.rotation.z = -0.28
  scene.add(helix)

  scene.add(new THREE.AmbientLight('#ffffff', 1.1))
  const light = new THREE.DirectionalLight('#dff7ff', 1.8)
  light.position.set(3, 4, 5)
  scene.add(light)
  resize()
  animate()
}

function resize() {
  if (!hostRef.value || !renderer || !camera) return
  const { width, height } = hostRef.value.getBoundingClientRect()
  renderer.setSize(Math.max(width, 1), Math.max(height, 1), false)
  camera.aspect = Math.max(width, 1) / Math.max(height, 1)
  camera.updateProjectionMatrix()
}

function animate() {
  if (!renderer || !scene || !camera) return
  frame = window.requestAnimationFrame(animate)
  if (points) points.rotation.y += 0.0009
  if (helix) {
    helix.rotation.y += 0.006
    helix.rotation.x = Math.sin(Date.now() * 0.0007) * 0.08
  }
  renderer.render(scene, camera)
}

onMounted(() => {
  initScene()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  if (frame) window.cancelAnimationFrame(frame)
  if (scene) disposeObject(scene)
  renderer?.dispose()
  renderer?.domElement.remove()
})
</script>
