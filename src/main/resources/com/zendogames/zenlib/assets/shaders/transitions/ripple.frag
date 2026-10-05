#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_texture;
uniform sampler2D u_texture1;
uniform float u_percent;

varying vec4 v_color;
varying vec2 v_texCoord;

const float amplitude = 100.0;
const float speed = 50.;


vec4 getFromColor(vec2 p){
   return texture2D(u_texture1, p);
}

vec4 getToColor(vec2 p){
   return texture2D(u_texture, p);
}

vec4 transition (vec2 uv) {
  vec2 dir = uv - vec2(.5);
  float dist = length(dir);
  vec2 offset = dir * (sin(u_percent * dist * amplitude - u_percent * speed) + .5) / 30.;
  return mix(
    getFromColor(uv + offset),
    getToColor(uv),
    smoothstep(0.2, 1.0, u_percent)
  );
}

void main() {
      vec2 flippedCoord = vec2(v_texCoord.x, 1. - v_texCoord.y);

        gl_FragColor = transition(flippedCoord)*v_color;
}