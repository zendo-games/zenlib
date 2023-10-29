#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_texture;
uniform sampler2D u_texture1;
uniform float u_percent;

varying vec4 v_color;
varying vec2 v_texCoord;

const ivec2 squaresMin = ivec2(20);
const int steps = 50;

vec4 getFromColor(vec2 p){
   return texture2D(u_texture1, p);
}

vec4 getToColor(vec2 p){
   return texture2D(u_texture, p);
}

void main() {
    vec2 flippedCoord = vec2(v_texCoord.x, 1. - v_texCoord.y);

    float d = min(u_percent, 1.0 - u_percent);
    float dist = steps>0 ? ceil(d * float(steps)) / float(steps) : d;
    vec2 squareSize = 2.0 * dist / vec2(squaresMin);


     vec2 p = dist>0.0 ? (floor(flippedCoord / squareSize) + 0.5) * squareSize : flippedCoord;
     gl_FragColor = mix(getFromColor(p), getToColor(p), u_percent) * v_color;

}