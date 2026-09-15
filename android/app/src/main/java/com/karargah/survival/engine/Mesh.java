package com.karargah.survival.engine;

import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Sabit köşe düzenli mesh: pos(3) + normal(3) + renk(3) + kemik(1) = 10 float.
 * VAO ile saklanır, tek çizim çağrısıyla basılır.
 */
public class Mesh {
    public static final int FLOATS_PER_VERTEX = 10;
    public static final int STRIDE = FLOATS_PER_VERTEX * 4;

    public static final int ATTR_POS = 0;
    public static final int ATTR_NORMAL = 1;
    public static final int ATTR_COLOR = 2;
    public static final int ATTR_BONE = 3;

    private final int vao;
    private final int vbo;
    private final int ibo;
    public final int indexCount;
    /** Modelin merkezden yarıçapı (frustum eleme için). */
    public final float radius;
    public final float minY, maxY;

    public Mesh(float[] vertices, int[] indices, float radius, float minY, float maxY) {
        this.indexCount = indices.length;
        this.radius = radius;
        this.minY = minY;
        this.maxY = maxY;

        FloatBuffer vb = ByteBuffer.allocateDirect(vertices.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        vb.put(vertices).position(0);
        IntBuffer ib = ByteBuffer.allocateDirect(indices.length * 4)
                .order(ByteOrder.nativeOrder()).asIntBuffer();
        ib.put(indices).position(0);

        int[] tmp = new int[1];
        GLES30.glGenVertexArrays(1, tmp, 0);
        vao = tmp[0];
        GLES30.glGenBuffers(1, tmp, 0);
        vbo = tmp[0];
        GLES30.glGenBuffers(1, tmp, 0);
        ibo = tmp[0];

        GLES30.glBindVertexArray(vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.length * 4, vb, GLES30.GL_STATIC_DRAW);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.length * 4, ib, GLES30.GL_STATIC_DRAW);

        GLES30.glEnableVertexAttribArray(ATTR_POS);
        GLES30.glVertexAttribPointer(ATTR_POS, 3, GLES30.GL_FLOAT, false, STRIDE, 0);
        GLES30.glEnableVertexAttribArray(ATTR_NORMAL);
        GLES30.glVertexAttribPointer(ATTR_NORMAL, 3, GLES30.GL_FLOAT, false, STRIDE, 12);
        GLES30.glEnableVertexAttribArray(ATTR_COLOR);
        GLES30.glVertexAttribPointer(ATTR_COLOR, 3, GLES30.GL_FLOAT, false, STRIDE, 24);
        GLES30.glEnableVertexAttribArray(ATTR_BONE);
        GLES30.glVertexAttribPointer(ATTR_BONE, 1, GLES30.GL_FLOAT, false, STRIDE, 36);
        GLES30.glBindVertexArray(0);
    }

    public void draw() {
        GLES30.glBindVertexArray(vao);
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0);
    }

    public void dispose() {
        int[] tmp = new int[]{vbo, ibo};
        GLES30.glDeleteBuffers(2, tmp, 0);
        tmp[0] = vao;
        GLES30.glDeleteVertexArrays(1, tmp, 0);
    }
}
