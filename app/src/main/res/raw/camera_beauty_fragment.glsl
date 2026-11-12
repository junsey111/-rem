precision mediump float;
varying vec2 vUv;
uniform sampler2D uImage;
uniform sampler2D uFilteredImage;
uniform vec2 uPixel;
uniform float uEnabled;
uniform vec4 uSkin; // smooth, blemish, whiten, rosy
uniform vec4 uDetail; // clarity, sharpen, legacy brightness, legacy saturation
uniform vec4 uLocal; // bright eyes, white teeth, dark circles, smile lines
uniform vec4 uFace; // center, width, height
uniform vec4 uEyes;
uniform vec4 uMouth;
uniform float uHasFace;
uniform float uQuality;
uniform int uWarpCount;
uniform vec4 uRegion[40];
uniform vec4 uWarp[40];
uniform float uFilterStrength;

float mask(vec2 uv, vec2 center, vec2 radius) {
    return 1.0 - smoothstep(0.2, 1.0, length((uv - center) / max(radius, vec2(0.001))));
}
float light(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
vec3 neighbor(vec2 uv, vec2 offset, vec3 original, inout float total) {
    vec3 c = texture2D(uImage, clamp(uv + offset, 0.0, 1.0)).rgb;
    float weight = exp(-abs(light(c) - light(original)) * 22.0);
    total += weight;
    return c * weight;
}
void main() {
    if (uEnabled < 0.5) { gl_FragColor = texture2D(uImage, vUv); return; }
    vec2 uv = vUv;
    for (int i = 0; i < 40; i++) {
        if (i >= uWarpCount) break;
        float weight = mask(uv, uRegion[i].xy, uRegion[i].zw);
        uv += (uWarp[i].xy + (uv - uRegion[i].xy) * uWarp[i].zw) * weight;
    }
    uv = clamp(uv, 0.001, 0.999);
    vec3 original = texture2D(uImage, uv).rgb;
    vec3 color = mix(original, texture2D(uFilteredImage, uv).rgb, uFilterStrength);
    float skin = smoothstep(-0.02, 0.09, original.r - original.b)
        * smoothstep(-0.04, 0.05, original.r - original.g) * smoothstep(0.08, 0.3, light(original));
    float smoothing = max(uSkin.x * 0.72, uSkin.y * 0.86);
    vec2 stepSize = uPixel * (1.5 + uSkin.y * 2.0);
    float total = 1.0;
    vec3 blur = original;
    blur += neighbor(uv, vec2(stepSize.x, 0.0), original, total);
    blur += neighbor(uv, vec2(-stepSize.x, 0.0), original, total);
    blur += neighbor(uv, vec2(0.0, stepSize.y), original, total);
    blur += neighbor(uv, vec2(0.0, -stepSize.y), original, total);
    if (uQuality > 0.5) {
        blur += neighbor(uv, stepSize, original, total);
        blur += neighbor(uv, -stepSize, original, total);
        blur += neighbor(uv, vec2(stepSize.x, -stepSize.y), original, total);
        blur += neighbor(uv, vec2(-stepSize.x, stepSize.y), original, total);
    }
    blur /= total;
    color += (blur - original) * smoothing * skin;
    color += (original - blur) * (uDetail.x * 0.35 + uDetail.y * 0.8) * (1.0 - smoothing * 0.3);
    color = mix(color, pow(max(color, 0.0), vec3(0.75)), uSkin.z * 0.55);
    color += vec3(0.045, -0.013, -0.006) * uSkin.w * skin;
    if (uHasFace > 0.5) {
        vec2 eyeRadius = vec2(uFace.z * 0.14, uFace.w * 0.065);
        float eyes = max(mask(uv, uEyes.xy, eyeRadius), mask(uv, uEyes.zw, eyeRadius));
        color += vec3(0.10) * uLocal.x * eyes;
        vec2 under = vec2(0.0, uFace.w * 0.055);
        float circles = max(mask(uv, uEyes.xy - under, eyeRadius), mask(uv, uEyes.zw - under, eyeRadius));
        color += vec3(0.12, 0.095, 0.075) * uLocal.z * circles;
        float mouth = mask(uv, uMouth.xy, vec2(uFace.z * 0.17, uFace.w * 0.043));
        float teeth = mouth * smoothstep(0.35, 0.7, light(original)) * (1.0 - smoothstep(0.12, 0.25, original.r - original.g));
        color = mix(color, vec3(light(color) + 0.1), teeth * uLocal.y * 0.55);
        vec2 side = vec2(uFace.z * 0.19, uFace.w * 0.055);
        float lines = max(mask(uv, uMouth.xy + side, vec2(uFace.z * 0.07, uFace.w * 0.16)),
            mask(uv, uMouth.xy + vec2(-side.x, side.y), vec2(uFace.z * 0.07, uFace.w * 0.16)));
        color = mix(color, blur + vec3(0.025), lines * uLocal.w * 0.55);
    }
    color = mix(vec3(light(color)), color, uDetail.w);
    color += uDetail.z;
    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
