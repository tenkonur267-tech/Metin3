package com.karargah.survival.engine;

import android.opengl.GLES30;

/**
 * Oyunun tüm 3B çizimini yöneten ince katman: iki gölgelendirici (statik ve
 * kemikli), gökyüzü kubbesi, blob gölgeler ve saydam geçiş.
 */
public class Renderer3D {
    public static final int MAX_BONES = 10;

    private static final String VS = ""
            + "layout(location=0) in vec3 aPos;\n"
            + "layout(location=1) in vec3 aNormal;\n"
            + "layout(location=2) in vec3 aColor;\n"
            + "layout(location=3) in float aBone;\n"
            + "uniform mat4 uViewProj;\n"
            + "uniform mat4 uModel;\n"
            + "#ifdef SKINNED\n"
            + "uniform mat4 uBones[10];\n"
            + "#endif\n"
            + "out vec3 vNormal;\n"
            + "out vec3 vColor;\n"
            + "out vec3 vWorld;\n"
            + "void main(){\n"
            + "  vec4 p = vec4(aPos,1.0);\n"
            + "  vec3 n = aNormal;\n"
            + "#ifdef SKINNED\n"
            + "  int bi = int(aBone + 0.5);\n"
            + "  mat4 b = uBones[bi];\n"
            + "  p = b * p;\n"
            + "  n = mat3(b) * n;\n"
            + "#endif\n"
            + "  vec4 wp = uModel * p;\n"
            + "  vWorld = wp.xyz;\n"
            + "  vNormal = mat3(uModel) * n;\n"
            + "  vColor = aColor;\n"
            + "  gl_Position = uViewProj * wp;\n"
            + "}\n";

    private static final String FS = ""
            + "precision mediump float;\n"
            + "in vec3 vNormal;\n"
            + "in vec3 vColor;\n"
            + "in vec3 vWorld;\n"
            + "uniform vec4 uTint;\n"
            + "uniform float uEmissive;\n"
            + "uniform vec3 uLightDir;\n"
            + "uniform vec3 uSunColor;\n"
            + "uniform vec3 uSkyColor;\n"
            + "uniform vec3 uGroundColor;\n"
            + "uniform vec3 uFogColor;\n"
            + "uniform float uFogDensity;\n"
            + "uniform vec3 uCamPos;\n"
            + "uniform vec4 uHighlight;\n"
            + "out vec4 fragColor;\n"
            + "void main(){\n"
            + "  vec3 N = normalize(vNormal);\n"
            + "  float d = max(dot(N, uLightDir), 0.0);\n"
            + "  float wrap = d * 0.82 + 0.18;\n"
            + "  vec3 hemi = mix(uGroundColor, uSkyColor, N.y * 0.5 + 0.5);\n"
            + "  vec3 base = vColor * uTint.rgb;\n"
            + "  vec3 col = base * (hemi + uSunColor * wrap);\n"
            + "  col += base * uEmissive;\n"
            + "  col += uHighlight.rgb * uHighlight.a;\n"
            + "  float dist = length(vWorld - uCamPos);\n"
            + "  float f = 1.0 - exp(-pow(dist * uFogDensity, 2.0));\n"
            + "  col = mix(col, uFogColor, clamp(f, 0.0, 1.0));\n"
            + "  fragColor = vec4(col, uTint.a);\n"
            + "}\n";

    private static final String SKY_VS = ""
            + "layout(location=0) in vec3 aPos;\n"
            + "uniform mat4 uViewProj;\n"
            + "uniform vec3 uCamPos;\n"
            + "out vec3 vDir;\n"
            + "void main(){\n"
            + "  vec3 wp = aPos * 400.0 + uCamPos;\n"
            + "  vDir = normalize(aPos);\n"
            + "  gl_Position = uViewProj * vec4(wp, 1.0);\n"
            + "}\n";

    private static final String SKY_FS = ""
            + "precision mediump float;\n"
            + "in vec3 vDir;\n"
            + "uniform vec3 uTop;\n"
            + "uniform vec3 uHorizon;\n"
            + "uniform vec3 uSunDir;\n"
            + "uniform vec3 uSunColor;\n"
            + "out vec4 fragColor;\n"
            + "void main(){\n"
            + "  vec3 d = normalize(vDir);\n"
            + "  float h = clamp(d.y * 1.4 + 0.12, 0.0, 1.0);\n"
            + "  vec3 col = mix(uHorizon, uTop, pow(h, 0.6));\n"
            + "  float sun = max(dot(d, normalize(uSunDir)), 0.0);\n"
            + "  col += uSunColor * pow(sun, 24.0) * 1.6;\n"
            + "  col += uSunColor * pow(sun, 4.0) * 0.12;\n"
            + "  fragColor = vec4(col, 1.0);\n"
            + "}\n";

    private static final String PART_VS = ""
            + "layout(location=0) in vec3 aPos;\n"
            + "layout(location=1) in vec4 aColor;\n"
            + "layout(location=2) in float aSize;\n"
            + "uniform mat4 uViewProj;\n"
            + "uniform float uViewportH;\n"
            + "out vec4 vColor;\n"
            + "void main(){\n"
            + "  vec4 p = uViewProj * vec4(aPos, 1.0);\n"
            + "  gl_Position = p;\n"
            + "  gl_PointSize = clamp(aSize * uViewportH / max(p.w, 0.3), 1.0, 220.0);\n"
            + "  vColor = aColor;\n"
            + "}\n";

    private static final String PART_FS = ""
            + "precision mediump float;\n"
            + "in vec4 vColor;\n"
            + "out vec4 fragColor;\n"
            + "void main(){\n"
            + "  vec2 c = gl_PointCoord - vec2(0.5);\n"
            + "  float r = dot(c, c);\n"
            + "  if (r > 0.25) discard;\n"
            + "  float a = smoothstep(0.25, 0.02, r);\n"
            + "  fragColor = vec4(vColor.rgb, vColor.a * a);\n"
            + "}\n";

    private Shader staticShader, skinShader, skyShader;
    private Shader current;
    private Mesh skyDome, shadowDisc;

    public int viewportW = 1, viewportH = 1;
    public final float[] viewProj = new float[16];
    public final float[] invViewProj = new float[16];
    public float camX, camY, camZ;

    // ışık/atmosfer ayarları (WaveManager gece-gündüz için değiştirir)
    public float lightX = 0.42f, lightY = 0.80f, lightZ = 0.42f;
    public float sunR = 1.0f, sunG = 0.95f, sunB = 0.85f;
    public float skyR = 0.32f, skyG = 0.37f, skyB = 0.45f;
    public float gndR = 0.16f, gndG = 0.15f, gndB = 0.13f;
    public float fogR = 0.52f, fogG = 0.58f, fogB = 0.62f;
    public float fogDensity = 0.0075f;
    public float skyTopR = 0.25f, skyTopG = 0.45f, skyTopB = 0.72f;
    public float skyHorR = 0.72f, skyHorG = 0.76f, skyHorB = 0.78f;

    private final float[] tmpModel = new float[16];

    public void init() {
        staticShader = new Shader(version(false) + VS, version(false) + FS);
        skinShader = new Shader(version(true) + VS, version(false) + FS);
        skyShader = new Shader("#version 300 es\n" + SKY_VS, "#version 300 es\n" + SKY_FS);

        MeshBuilder sb = new MeshBuilder();
        sb.color(0xFFFFFF).sphere(1f, 18, 10);
        skyDome = sb.build();

        MeshBuilder db = new MeshBuilder();
        db.color(0x000000).disc(1f, 16);
        shadowDisc = db.build();

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glEnable(GLES30.GL_CULL_FACE);
        GLES30.glCullFace(GLES30.GL_BACK);
        GLES30.glFrontFace(GLES30.GL_CCW);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static String version(boolean skinned) {
        return "#version 300 es\n" + (skinned ? "#define SKINNED 1\n" : "");
    }

    public void resize(int w, int h) {
        viewportW = Math.max(1, w);
        viewportH = Math.max(1, h);
        GLES30.glViewport(0, 0, viewportW, viewportH);
    }

    /** Kare başlangıcı: ekranı temizler, kamera matrislerini yükler. */
    public void beginFrame(Camera cam) {
        System.arraycopy(cam.viewProj, 0, viewProj, 0, 16);
        M4.invert(invViewProj, viewProj);
        camX = cam.eyeX;
        camY = cam.eyeY;
        camZ = cam.eyeZ;

        GLES30.glViewport(0, 0, viewportW, viewportH);
        GLES30.glClearColor(fogR, fogG, fogB, 1f);
        GLES30.glDepthMask(true);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_BLEND);
        current = null;
    }

    public void drawSky() {
        GLES30.glDepthMask(false);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        skyShader.use();
        skyShader.setMat4("uViewProj", viewProj);
        skyShader.setVec3("uCamPos", camX, camY, camZ);
        skyShader.setVec3("uTop", skyTopR, skyTopG, skyTopB);
        skyShader.setVec3("uHorizon", skyHorR, skyHorG, skyHorB);
        skyShader.setVec3("uSunDir", lightX, lightY, lightZ);
        skyShader.setVec3("uSunColor", sunR, sunG, sunB);
        skyDome.draw();
        GLES30.glEnable(GLES30.GL_CULL_FACE);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthMask(true);
        current = null;
    }

    private void bind(Shader s) {
        if (current == s) return;
        current = s;
        s.use();
        s.setMat4("uViewProj", viewProj);
        s.setVec3("uLightDir", lightX, lightY, lightZ);
        s.setVec3("uSunColor", sunR, sunG, sunB);
        s.setVec3("uSkyColor", skyR, skyG, skyB);
        s.setVec3("uGroundColor", gndR, gndG, gndB);
        s.setVec3("uFogColor", fogR, fogG, fogB);
        s.setFloat("uFogDensity", fogDensity);
        s.setVec3("uCamPos", camX, camY, camZ);
        s.setVec4("uHighlight", 0f, 0f, 0f, 0f);
        s.setFloat("uEmissive", 0f);
    }

    public void draw(Mesh mesh, float[] model, float r, float g, float b, float a) {
        draw(mesh, model, r, g, b, a, 0f);
    }

    public void draw(Mesh mesh, float[] model, float r, float g, float b, float a, float emissive) {
        bind(staticShader);
        staticShader.setMat4("uModel", model);
        staticShader.setVec4("uTint", r, g, b, a);
        staticShader.setFloat("uEmissive", emissive);
        mesh.draw();
    }

    public void drawHighlighted(Mesh mesh, float[] model, float r, float g, float b, float a,
                                float emissive, float hr, float hg, float hb, float hs) {
        bind(staticShader);
        staticShader.setMat4("uModel", model);
        staticShader.setVec4("uTint", r, g, b, a);
        staticShader.setFloat("uEmissive", emissive);
        staticShader.setVec4("uHighlight", hr, hg, hb, hs);
        mesh.draw();
        staticShader.setVec4("uHighlight", 0f, 0f, 0f, 0f);
    }

    public void drawSkinned(Mesh mesh, float[] model, float[] bones, int boneCount,
                            float r, float g, float b, float a, float emissive) {
        bind(skinShader);
        skinShader.setMat4("uModel", model);
        skinShader.setMat4Array("uBones", bones, boneCount);
        skinShader.setVec4("uTint", r, g, b, a);
        skinShader.setFloat("uEmissive", emissive);
        mesh.draw();
    }

    /** Karakter ve yapıların altına düşen yumuşak daire gölge. */
    public void drawBlobShadow(float x, float z, float radius, float alpha) {
        M4.trs(tmpModel, x, 0.035f, z, 0f, radius, 1f, radius);
        draw(shadowDisc, tmpModel, 0f, 0f, 0f, alpha);
    }

    public void beginTransparent() {
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glDepthMask(false);
    }

    public void beginAdditive() {
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE);
        GLES30.glDepthMask(false);
    }

    public void endTransparent() {
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glDepthMask(true);
    }

    public void setCulling(boolean on) {
        if (on) GLES30.glEnable(GLES30.GL_CULL_FACE);
        else GLES30.glDisable(GLES30.GL_CULL_FACE);
    }

    public Shader particleShader() {
        if (partShader == null) {
            partShader = new Shader("#version 300 es\n" + PART_VS, "#version 300 es\n" + PART_FS);
        }
        return partShader;
    }

    private Shader partShader;

    public void dispose() {
        if (staticShader != null) staticShader.dispose();
        if (skinShader != null) skinShader.dispose();
        if (skyShader != null) skyShader.dispose();
        if (partShader != null) partShader.dispose();
        if (skyDome != null) skyDome.dispose();
        if (shadowDisc != null) shadowDisc.dispose();
    }
}
