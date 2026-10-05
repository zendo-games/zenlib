#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_texture;
uniform sampler2D u_texture1;
uniform float u_percent;

varying vec4 v_color;
varying vec2 v_texCoord;

void main() {
    vec2 flippedCoord = vec2(v_texCoord.x, 1. - v_texCoord.y);
    vec4 color = texture2D(u_texture, flippedCoord);
    vec4 color1 = texture2D(u_texture1, flippedCoord);
    gl_FragColor = vec4(mix(color1.rgb, color.rgb, u_percent), u_percent) * v_color;
}