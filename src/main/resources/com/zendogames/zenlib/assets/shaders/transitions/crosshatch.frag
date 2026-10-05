#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_texture;
uniform sampler2D u_texture1;
uniform float u_percent;

varying vec4 v_color;
varying vec2 v_texCoord;

const vec2 center = vec2(0.5);
const float threshold = 3.0;
const float fadeEdge = 0.1;


vec4 getFromColor(vec2 p){
   return texture2D(u_texture1, p);
}

vec4 getToColor(vec2 p){
   return texture2D(u_texture, p);
}


float rand(vec2 co) {
  return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
}
vec4 transition(vec2 p) {
  float dist = distance(center, p) / threshold;
  float r = u_percent - min(rand(vec2(p.y, 0.0)), rand(vec2(0.0, p.x)));
  return mix(getFromColor(p), getToColor(p), mix(0.0, mix(step(dist, r), 1.0, smoothstep(1.0-fadeEdge, 1.0, u_percent)), smoothstep(0.0, fadeEdge, u_percent)));
}

void main() {
      vec2 flippedCoord = vec2(v_texCoord.x, 1. - v_texCoord.y);

        gl_FragColor = transition(flippedCoord) * v_color;
}