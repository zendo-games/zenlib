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

// Author: gre
// License: MIT
const float persp = 0.5; // = 0.7
const float unzoom = 0.7; // = 0.3
const float reflection = 0.9; // = 0.4
const float floating = 3.0; // = 3.0
const vec4 backgroundColor = vec4(0.1, 0.1, 0.1, 1.0);

vec2 project (vec2 p) {
    return p * vec2(1.0, -1.2) + vec2(0.0, -floating/100.);
}

bool inBounds (vec2 p) {
    return all(lessThan(vec2(0.0), p)) && all(lessThan(p, vec2(1.0)));
}

vec4 bgColor (vec2 p, vec2 pfr, vec2 pto) {
    vec4 c = backgroundColor;
    pfr = project(pfr);
    // FIXME avoid branching might help perf!
    if (inBounds(pfr)) {
        c += mix(vec4(0.0), getFromColor(pfr), reflection * mix(1.0, 0.0, pfr.y));
    }
    pto = project(pto);
    if (inBounds(pto)) {
        c += mix(vec4(0.0), getToColor(pto), reflection * mix(1.0, 0.0, pto.y));
    }
    return c;
}

// p : the position
// persp : the perspective in [ 0, 1 ]
// center : the xcenter in [0, 1] \ 0.5 excluded
vec2 xskew (vec2 p, float persp, float center) {
    float x = mix(p.x, 1.0-p.x, center);
    return (
    (
    vec2( x, (p.y - 0.5*(1.0-persp) * x) / (1.0+(persp-1.0)*x) )
    - vec2(0.5-distance(center, 0.5), 0.0)
    )
    * vec2(0.5 / distance(center, 0.5) * (center<0.5 ? 1.0 : -1.0), 1.0)
    + vec2(center<0.5 ? 0.0 : 1.0, 0.0)
    );
}

vec4 transition(vec2 op) {
    float uz = unzoom * 2.0*(0.5-distance(0.5, u_percent));
    vec2 p = -uz*0.5+(1.0+uz) * op;
    vec2 fromP = xskew(
    (p - vec2(u_percent, 0.0)) / vec2(1.0-u_percent, 1.0),
    1.0-mix(u_percent, 0.0, persp),
    0.0
    );
    vec2 toP = xskew(
    p / vec2(u_percent, 1.0),
    mix(pow(u_percent, 2.0), 1.0, persp),
    1.0
    );
    // FIXME avoid branching might help perf!
    if (inBounds(fromP)) {
        return getFromColor(fromP);
    }
    else if (inBounds(toP)) {
        return getToColor(toP);
    }
    return bgColor(op, fromP, toP);
}

void main() {
    vec2 flippedCoord = vec2(v_texCoord.x, 1. - v_texCoord.y);

    gl_FragColor = transition(flippedCoord) * v_color;
}
