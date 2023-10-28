#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_texture;
uniform sampler2D u_texture1;
uniform float u_percent;

varying vec4 v_color;
varying vec2 v_texCoord;

float zoom_quickness = 0.8;
float nQuick = clamp(zoom_quickness,0.2,1.0);

vec2 zoom(vec2 uv, float amount) {
    return 0.5 + ((uv - 0.5) * (1.0-amount));
}

vec4 getFromColor(vec2 p){
    return texture2D(u_texture1, p);
}

vec4 getToColor(vec2 p){
    return texture2D(u_texture, p);
}

vec4 transition (vec2 uv) {
    return mix(
    getFromColor(zoom(uv, smoothstep(0.0, nQuick, u_percent))),
    getToColor(uv),
    smoothstep(nQuick-0.2, 1.0, u_percent)
    );
}

void main() {
    vec2 flippedCoord = vec2(v_texCoord.x, 1. - v_texCoord.y);

    gl_FragColor = transition(flippedCoord)*v_color;
}
