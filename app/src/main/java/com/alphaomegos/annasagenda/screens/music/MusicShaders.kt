package com.alphaomegos.annasagenda.screens.music

/*
 * The visualizer's shaders (30.10), GLSL ES 1.00 for OpenGL ES 2.0. Kept short;
 * every moving part arrives as a phase already wrapped within a turn, so hours of
 * music lose no precision. A shader the device refuses is shown as its log, not a
 * crash (MusicGlRenderer).
 */
internal object MusicShaders {

    /** A rectangle over the whole view, with its 0..1 coordinates. */
    const val QUAD_VERTEX = """
attribute vec2 aPos;
varying vec2 vUv;
void main() {
    vUv = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

    /** Coloured triangles: the classic spectrum's bars and peaks. */
    const val COLOR_VERTEX = """
attribute vec2 aPos;
attribute vec4 aColor;
varying vec4 vColor;
void main() {
    vColor = aColor;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

    const val COLOR_FRAGMENT = """
precision mediump float;
varying vec4 vColor;
void main() {
    gl_FragColor = vColor;
}
"""

    /** A texture onto the view: the feedback picture shown. */
    const val COPY_FRAGMENT = """
precision mediump float;
uniform sampler2D uTex;
varying vec2 vUv;
void main() {
    gl_FragColor = texture2D(uTex, vUv);
}
"""

    /** The vortex's filaments: strips whose edges fade (aSide −1..1 across). */
    const val FILAMENT_VERTEX = """
attribute vec2 aPos;
attribute float aSide;
varying float vSide;
void main() {
    vSide = aSide;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

    const val FILAMENT_FRAGMENT = """
precision mediump float;
uniform vec4 uColor;
varying float vSide;
void main() {
    float a = 1.0 - smoothstep(0.25, 1.0, abs(vSide));
    gl_FragColor = vec4(uColor.rgb * (a * uColor.a), 1.0);
}
"""

    private const val PRECISION = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
"""

    /**
     * The spectrum flame: the live spectrum as a rising curtain of fine strands —
     * cyan at the base, green, yellow at the edges, orange and red at the tips — a
     * warm halo over the contour, and the last frame drifting up and fading behind.
     * uBands: the bands as a 64×1 texture (48 used), read with linear filtering so
     * the strands between two bands take what lies between them.
     */
    const val FLAME_FRAGMENT = PRECISION + """
uniform sampler2D uBands;
uniform sampler2D uPrev;
uniform vec2 uSize;
uniform vec4 uPhase;
uniform float uTreble;
uniform float uRetain;
uniform float uRise;
uniform float uBandsUsed;
varying vec2 vUv;

float bandAt(float x) {
    float u = (clamp(x, 0.0, 1.0) * (uBandsUsed - 1.0) + 0.5) / 64.0;
    return texture2D(uBands, vec2(u, 0.5)).r;
}

void main() {
    vec2 uv = vUv;
    float h = bandAt(uv.x) * 0.9;
    float edge = h + 0.012 * uTreble * h * sin(uv.x * 190.0 + uPhase.x);
    float inside = 1.0 - smoothstep(edge - 0.006, edge + 0.006, uv.y);
    float t = clamp(uv.y / max(edge, 0.02), 0.0, 1.0);
    float strands = 0.7 + 0.3 * sin(gl_FragCoord.x * 1.7 + 2.5 * sin(uv.x * 31.0 + uPhase.y));
    vec3 cyan = vec3(0.0, 0.85, 0.9);
    vec3 green = vec3(0.2, 0.95, 0.15);
    vec3 yellow = vec3(1.0, 0.9, 0.1);
    vec3 red = vec3(1.0, 0.3, 0.05);
    vec3 ramp = t < 0.35 ? mix(cyan, green, t / 0.35)
        : (t < 0.75 ? mix(green, yellow, (t - 0.35) / 0.4) : mix(yellow, red, (t - 0.75) / 0.25));
    float body = inside * strands * (0.55 + 0.45 * t) * step(0.004, h);
    float above = max(uv.y - edge, 0.0);
    float halo = exp(-above * 45.0) * (1.0 - inside) * h * 0.55;
    vec3 colour = ramp * body + vec3(1.0, 0.42, 0.08) * halo;
    vec3 trail = texture2D(uPrev, uv - vec2(0.0, uRise)).rgb * uRetain * vec3(1.0, 0.82, 0.62);
    gl_FragColor = vec4(max(colour, trail), 1.0);
}
"""

    /**
     * The crimson vortex's background: a tunnel of red and rose light streaks in
     * polar coordinates (round on any screen), a dark opening in the middle that the
     * bass widens, the flow outward driven by the bass; the last frame, zoomed out a
     * little and turned, fades behind as the trails.
     */
    const val VORTEX_FRAGMENT = PRECISION + """
uniform sampler2D uPrev;
uniform vec2 uSize;
uniform vec4 uPhase;
uniform vec2 uCenter;
uniform float uBass;
uniform float uTreble;
uniform float uEnergy;
uniform float uSurge;
uniform float uRetain;
uniform float uZoom;
uniform float uTurn;
varying vec2 vUv;

void main() {
    float aspect = uSize.x / uSize.y;
    vec2 p = (vUv - 0.5) * vec2(aspect, 1.0) - uCenter;
    float r = max(length(p), 0.0005);
    float a = atan(p.y, p.x);
    float z = min(0.32 / r, 40.0) + uPhase.x;
    float s1 = sin(a * 7.0 + z * 2.0 + 1.3 * sin(z * 0.5 + uPhase.y));
    float s2 = sin(a * 13.0 - z * 3.0 + 2.0 * sin(a * 3.0 + uPhase.z));
    float s3 = sin(a * 29.0 + z * 5.0) * uTreble;
    float streak = clamp(0.5 + 0.25 * s1 + 0.17 * s2 + 0.08 * s3, 0.0, 1.0);
    streak = streak * streak * streak;
    float open = smoothstep(0.04 + 0.07 * uBass, 0.22 + 0.12 * uBass, r);
    float fall = exp(-r * 1.3);
    float light = streak * open * (0.3 + 0.7 * fall) * (0.35 + 0.65 * uEnergy) + uSurge * 0.22 * open * fall;
    vec3 deep = vec3(0.10, 0.0, 0.02);
    vec3 red = vec3(0.85, 0.07, 0.12);
    vec3 rose = vec3(1.0, 0.6, 0.66);
    vec3 colour = deep * open * (0.5 + 0.5 * uEnergy) + mix(red, rose, clamp(light * 1.5 - 0.25, 0.0, 1.0)) * light;
    vec2 q = vUv - 0.5;
    float c = cos(uTurn);
    float s = sin(uTurn);
    q = vec2(c * q.x - s * q.y, s * q.x + c * q.y) / uZoom + 0.5;
    vec3 trail = texture2D(uPrev, q).rgb * uRetain;
    gl_FragColor = vec4(min(max(colour, trail), vec3(0.97)), 1.0);
}
"""
}
