package com.karargah.survival.engine;

import android.opengl.GLES30;
import android.util.Log;

import java.util.HashMap;

/** GLSL ES 3.0 program sarmalayıcısı; uniform konumlarını önbellekler. */
public class Shader {
    private static final String TAG = "Shader";

    public final int program;
    private final HashMap<String, Integer> uniforms = new HashMap<>();

    public Shader(String vertexSrc, String fragmentSrc) {
        int vs = compile(GLES30.GL_VERTEX_SHADER, vertexSrc);
        int fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSrc);
        program = GLES30.glCreateProgram();
        GLES30.glAttachShader(program, vs);
        GLES30.glAttachShader(program, fs);
        GLES30.glLinkProgram(program);
        int[] ok = new int[1];
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES30.glGetProgramInfoLog(program);
            GLES30.glDeleteProgram(program);
            throw new RuntimeException("Program bağlanamadı: " + log);
        }
        GLES30.glDeleteShader(vs);
        GLES30.glDeleteShader(fs);
    }

    private static int compile(int type, String src) {
        int id = GLES30.glCreateShader(type);
        GLES30.glShaderSource(id, src);
        GLES30.glCompileShader(id);
        int[] ok = new int[1];
        GLES30.glGetShaderiv(id, GLES30.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES30.glGetShaderInfoLog(id);
            Log.e(TAG, "Shader hatası: " + log + "\n" + src);
            GLES30.glDeleteShader(id);
            throw new RuntimeException("Shader derlenemedi: " + log);
        }
        return id;
    }

    public void use() {
        GLES30.glUseProgram(program);
    }

    public int loc(String name) {
        Integer cached = uniforms.get(name);
        if (cached != null) return cached;
        int l = GLES30.glGetUniformLocation(program, name);
        uniforms.put(name, l);
        return l;
    }

    public void setMat4(String name, float[] m) {
        int l = loc(name);
        if (l >= 0) GLES30.glUniformMatrix4fv(l, 1, false, m, 0);
    }

    public void setMat4Array(String name, float[] data, int count) {
        int l = loc(name);
        if (l >= 0) GLES30.glUniformMatrix4fv(l, count, false, data, 0);
    }

    public void setVec4(String name, float x, float y, float z, float w) {
        int l = loc(name);
        if (l >= 0) GLES30.glUniform4f(l, x, y, z, w);
    }

    public void setVec3(String name, float x, float y, float z) {
        int l = loc(name);
        if (l >= 0) GLES30.glUniform3f(l, x, y, z);
    }

    public void setFloat(String name, float v) {
        int l = loc(name);
        if (l >= 0) GLES30.glUniform1f(l, v);
    }

    public void setInt(String name, int v) {
        int l = loc(name);
        if (l >= 0) GLES30.glUniform1i(l, v);
    }

    public void dispose() {
        GLES30.glDeleteProgram(program);
    }
}
