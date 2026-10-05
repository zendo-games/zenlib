#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_texture;
uniform sampler2D u_texture1;
uniform float u_percent;

varying vec4 v_color;
varying vec2 v_texCoord;

vec4 getFromColor(vec2 p){
    return texture2D(u_texture1, p);
}

vec4 getToColor(vec2 p){
    return texture2D(u_texture, p);
}

vec2 offset(float progress, float x, float theta) {
    float phase = progress*progress + progress + theta;
    float shifty = 0.03*progress*cos(10.0*(progress+x));
    return vec2(0, shifty);
}
vec4 transition(vec2 p) {
    return mix(getFromColor(p + offset(u_percent, p.x, 0.0)), getToColor(p + offset(1.0-u_percent, p.x, 3.14)), u_percent);
}

void main() {
    vec2 flippedCoord = vec2(v_texCoord.x, 1. - v_texCoord.y);

    gl_FragColor = transition(flippedCoord) * v_color;
}