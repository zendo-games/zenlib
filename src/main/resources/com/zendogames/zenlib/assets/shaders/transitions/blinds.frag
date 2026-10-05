#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_texture;
uniform sampler2D u_texture1;

uniform float u_percent;

varying vec4 v_color;
varying vec2 v_texCoord;

void main() {

     float t = u_percent;
     vec2 flippedCoord = vec2(v_texCoord.x, 1. - v_texCoord.y);
     vec4 color = texture2D(u_texture, flippedCoord);
     vec4 color1 = texture2D(u_texture1, flippedCoord);

     if (mod(floor(flippedCoord.y*100.*u_percent),2.)==0.)
       t*=2.-.5;

     gl_FragColor = mix(color1, color, mix(t, u_percent, smoothstep(0.8, 1.0, u_percent))) * v_color;

//    vec4 color = texture2D(u_texture, v_texCoord);
//    float x = mod(v_texCoord.x, .1) * 10.;
//    float draw = 1.0 - smoothstep(u_percent - .05, u_percent, x);
//
//    gl_FragColor = vec4(color.rgb, draw);
}