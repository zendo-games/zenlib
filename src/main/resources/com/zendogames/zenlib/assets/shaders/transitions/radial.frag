#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_texture;
uniform sampler2D u_texture1;
uniform float u_percent;

varying vec4 v_color;
varying vec2 v_texCoord;

const float PI = 3.141592653589;

void main() {
    vec2 flippedCoord = vec2(v_texCoord.x, 1. - v_texCoord.y);

    vec2 rp = flippedCoord*2.-1.;

    vec4 color = texture2D(u_texture, flippedCoord);
    vec4 color1 = texture2D(u_texture1, flippedCoord);

     gl_FragColor = mix(color, color1, smoothstep(0.0, 1.0, atan(rp.y,rp.x) - (u_percent-.5) * PI * 2.5)) * v_color;
}