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

float rand(int num) {
  return fract(mod(float(num) * 67123.313, 12.0) * sin(float(num) * 10.3) * cos(float(num)));
}

float wave(int num, int bars, float frequency) {
  float fn = float(num) * frequency * 0.1 * float(bars);
  return cos(fn * 0.5) * cos(fn * 0.13) * sin((fn+10.0) * 0.3) / 2.0 + 0.5;
}

float drip(int num, int bars, float dripScale) {
  return sin(float(num) / float(bars - 1) * 3.141592) * dripScale;
}

float pos(int num, int bars, float frequency, float noise, float dripScale) {
  return (noise == 0.0 ? wave(num, bars, frequency) : mix(wave(num, bars, frequency), rand(num), noise)) + (dripScale == 0.0 ? 0.0 : drip(num, bars, dripScale));
}

void main() {
    vec2 uv = vec2(v_texCoord.x, 1. - v_texCoord.y);

    // Number of total bars/columns
     int bars = 30;

    // Multiplier for speed ratio. 0 = no variation when going down, higher = some elements go much faster
    float amplitude = 3.0;

    // Further variations in speed. 0 = no noise, 1 = super noisy (ignore frequency)
    float noise = 0.1;

    // Speed variation horizontally. the bigger the value, the shorter the waves
    float frequency = 0.5;

    // How much the bars seem to "run" from the middle of the screen first (sticking to the sides). 0 = no drip, 1 = curved drip
    float dripScale = 0.5;

    int bar = int(uv.x * (float(bars)));
    float scale = 1.0 + pos(bar, bars, frequency, noise, dripScale) * amplitude;
    float phase = u_percent * scale;
    float posY = uv.y / vec2(1.0).y;
    vec2 p;
    vec4 c;
    if (phase + posY < 1.0) {
      p = vec2(uv.x, uv.y + mix(0.0, vec2(1.0).y, phase)) / vec2(1.0).xy;
      c = getFromColor(p);
    } else {
      p = uv.xy / vec2(1.0).xy;
      c = getToColor(p);
    }

      // Finally, apply the color
    gl_FragColor = c * v_color;
}