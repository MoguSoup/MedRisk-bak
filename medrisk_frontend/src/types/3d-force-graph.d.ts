declare module '3d-force-graph' {
  type Accessor<T = unknown> = T | ((value: any) => T)
  type GraphData = { nodes: any[]; links: any[] }
  type ForceGraphInstance = {
    (element: HTMLElement): ForceGraphInstance
    graphData(data?: GraphData): ForceGraphInstance | GraphData
    backgroundColor(value: string): ForceGraphInstance
    nodeLabel(value: Accessor<string>): ForceGraphInstance
    nodeColor(value: Accessor<string>): ForceGraphInstance
    nodeVal(value: Accessor<number>): ForceGraphInstance
    nodeThreeObject(value: Accessor<object>): ForceGraphInstance
    linkLabel(value: Accessor<string>): ForceGraphInstance
    linkColor(value: Accessor<string>): ForceGraphInstance
    linkOpacity(value: number): ForceGraphInstance
    linkDirectionalParticleColor(value: Accessor<string>): ForceGraphInstance
    linkDirectionalParticleWidth(value: Accessor<number>): ForceGraphInstance
    linkDirectionalParticles(value: Accessor<number>): ForceGraphInstance
    showNavInfo(value: boolean): ForceGraphInstance
    cooldownTicks(value: number): ForceGraphInstance
    width(value: number): ForceGraphInstance
    height(value: number): ForceGraphInstance
  }

  export default function ForceGraph3D(options?: Record<string, unknown>): ForceGraphInstance
}
