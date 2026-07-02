import * as THREE from 'three'

export type ChartRow = {
  name: string
  value: number
  color?: string
}

export function canUseWebGL() {
  if (typeof window === 'undefined' || typeof document === 'undefined') return false
  if (typeof navigator !== 'undefined' && navigator.userAgent.toLowerCase().includes('jsdom')) return false
  try {
    const canvas = document.createElement('canvas')
    return Boolean(canvas.getContext('webgl2') || canvas.getContext('webgl'))
  } catch {
    return false
  }
}

export function disposeObject(object: THREE.Object3D) {
  object.traverse((child) => {
    const mesh = child as THREE.Mesh
    mesh.geometry?.dispose()
    const material = mesh.material as THREE.Material | THREE.Material[] | undefined
    if (Array.isArray(material)) {
      material.forEach((item) => item.dispose())
    } else {
      material?.dispose()
    }
  })
}

export function toFiniteNumber(value: unknown, fallback = 0) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : fallback
}

export function scaleValue(value: number, max: number, minSize = 0.12, maxSize = 2.4) {
  if (!max || max <= 0) return minSize
  return minSize + (Math.max(value, 0) / max) * (maxSize - minSize)
}

export function rowsFromSeries(series: any): ChartRow[] {
  const palette = series?.color || ['#2563eb', '#0f766e', '#f59e0b', '#dc2626', '#7c3aed', '#0891b2']
  const source = Array.isArray(series?.data) ? series.data : []
  return source.map((item: any, index: number) => ({
    name: String(item?.name ?? item?.label ?? index + 1),
    value: toFiniteNumber(item?.value ?? item, 0),
    color: item?.itemStyle?.color || palette[index % palette.length]
  }))
}

export function rowsFromAxisOption(option: Record<string, unknown>) {
  const series = Array.isArray((option as any).series) ? (option as any).series : []
  const xAxis = Array.isArray((option as any).xAxis) ? (option as any).xAxis[0] : (option as any).xAxis
  const labels = Array.isArray(xAxis?.data) ? xAxis.data : []
  return series.map((item: any, seriesIndex: number) => ({
    name: String(item?.name ?? `Series ${seriesIndex + 1}`),
    color: item?.lineStyle?.color || item?.itemStyle?.color || ['#2563eb', '#dc2626', '#0f766e', '#9333ea'][seriesIndex % 4],
    values: (Array.isArray(item?.data) ? item.data : []).map((value: unknown) => toFiniteNumber(value, 0)),
    labels
  }))
}

export function makeTextSprite(text: string, color = '#0f172a') {
  const canvas = document.createElement('canvas')
  canvas.width = 512
  canvas.height = 128
  const context = canvas.getContext('2d')
  if (!context) return undefined
  context.clearRect(0, 0, canvas.width, canvas.height)
  context.font = '600 34px Microsoft YaHei, Arial, sans-serif'
  context.fillStyle = 'rgba(255, 255, 255, 0.88)'
  context.fillRect(0, 0, canvas.width, canvas.height)
  context.fillStyle = color
  context.textBaseline = 'middle'
  context.fillText(text.slice(0, 16), 18, 64)
  const texture = new THREE.CanvasTexture(canvas)
  const material = new THREE.SpriteMaterial({ map: texture, transparent: true })
  const sprite = new THREE.Sprite(material)
  sprite.scale.set(1.6, 0.4, 1)
  return sprite
}
