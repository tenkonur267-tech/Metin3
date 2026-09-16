package com.karargah.survival.engine;

import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Wavefront OBJ/MTL yükleyici: hazır 3B model paketlerini oyuna takmak için.
 *
 * <p>Oyunun bütün modelleri kodla üretilir ({@link MeshBuilder}); bu yükleyici
 * bunun yerine değil, <em>yanına</em> durur. {@code assets/models/} altına bir
 * model paketi konulduğunda kodu değiştirmeden dünyaya girebilsin diye vardır.
 *
 * <p>Motorun köşe düzeni konum + normal + <b>köşe rengi</b> olduğu için doku
 * okunmaz: her yüzün rengi MTL dosyasındaki {@code Kd} (dağınık renk)
 * değerinden gelir. Bu, oyunun low-poly görünümüyle zaten uyumludur; dokulu
 * paketler de düz renkli ama doğru biçimli görünür.
 *
 * <p>Desteklenen: {@code v}, {@code vn}, {@code f} (üçgen ve çokgen, negatif
 * indeksler dahil), {@code usemtl}, {@code mtllib}, MTL içinde {@code newmtl}
 * ve {@code Kd}. Normal verilmemişse yüz normali hesaplanır.
 */
public final class ObjLoader {

    private ObjLoader() {
    }

    /** Yüklenemeyen model için fırlatılır; çağıran yedeğe düşebilsin diye. */
    public static class LoadException extends RuntimeException {
        public LoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Varlık klasöründen bir OBJ yükler.
     *
     * @param assets  uygulamanın varlık yöneticisi
     * @param path    "models/agac.obj" gibi varlık yolu
     * @param scale   modeli oyun ölçeğine getiren çarpan
     * @param tint    modelin bütününe uygulanacak renk çarpanı (0xRRGGBB);
     *                renk değiştirmek istemiyorsan 0xFFFFFF
     */
    public static Mesh load(AssetManager assets, String path, float scale, int tint) {
        if (assets == null) throw new LoadException("Varlık yöneticisi yok: " + path, null);
        try (InputStream in = assets.open(path)) {
            return parse(assets, path, in, scale, tint);
        } catch (IOException e) {
            throw new LoadException("Model okunamadı: " + path, e);
        }
    }

    /** Model var mı? Paket eklenmemişse kodla üretilen modele düşülebilsin. */
    public static boolean exists(AssetManager assets, String path) {
        if (assets == null) return false;
        try (InputStream in = assets.open(path)) {
            return in != null;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    // ---- ayrıştırma -----------------------------------------------------

    private static Mesh parse(AssetManager assets, String path, InputStream in,
                              float scale, int tint) throws IOException {
        ArrayList<float[]> positions = new ArrayList<>();
        ArrayList<float[]> normals = new ArrayList<>();
        HashMap<String, float[]> materials = new HashMap<>();
        float[] current = {0.8f, 0.8f, 0.8f};

        // Çıktı: her üçgen köşesi için pos(3) + normal(3) + renk(3) + kemik(1)
        ArrayList<float[]> verts = new ArrayList<>();
        ArrayList<Integer> faceStart = new ArrayList<>();

        float tr = ((tint >> 16) & 0xFF) / 255f;
        float tg = ((tint >> 8) & 0xFF) / 255f;
        float tb = (tint & 0xFF) / 255f;

        BufferedReader r = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16);
        String line;
        ArrayList<int[]> face = new ArrayList<>();
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            String[] t = line.split("\\s+");
            switch (t[0]) {
                case "v":
                    positions.add(new float[]{
                            parse(t, 1) * scale, parse(t, 2) * scale, parse(t, 3) * scale});
                    break;
                case "vn":
                    normals.add(new float[]{parse(t, 1), parse(t, 2), parse(t, 3)});
                    break;
                case "mtllib":
                    if (t.length > 1) readMaterials(assets, path, t[1], materials);
                    break;
                case "usemtl": {
                    float[] c = t.length > 1 ? materials.get(t[1]) : null;
                    current = c != null ? c : new float[]{0.8f, 0.8f, 0.8f};
                    break;
                }
                case "f": {
                    face.clear();
                    for (int i = 1; i < t.length; i++) {
                        int[] idx = parseVertexRef(t[i], positions.size(), normals.size());
                        if (idx != null) face.add(idx);
                    }
                    // Çokgeni yelpaze şeklinde üçgenlere böl
                    for (int i = 2; i < face.size(); i++) {
                        faceStart.add(verts.size());
                        emit(verts, positions, normals, face.get(0), current, tr, tg, tb);
                        emit(verts, positions, normals, face.get(i - 1), current, tr, tg, tb);
                        emit(verts, positions, normals, face.get(i), current, tr, tg, tb);
                    }
                    break;
                }
                default:
                    break;
            }
        }
        if (verts.isEmpty()) {
            throw new LoadException("Model boş: " + path, null);
        }

        // Normali olmayan üçgenler için yüz normali hesapla
        for (int i = 0; i < faceStart.size(); i++) {
            int s = faceStart.get(i);
            float[] a = verts.get(s), b = verts.get(s + 1), c = verts.get(s + 2);
            if (a[3] != 0f || a[4] != 0f || a[5] != 0f) continue;
            float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
            float vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
            float nx = uy * vz - uz * vy;
            float ny = uz * vx - ux * vz;
            float nz = ux * vy - uy * vx;
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-6f) {
                ny = 1f;
                l = 1f;
            }
            nx /= l;
            ny /= l;
            nz /= l;
            for (int k = 0; k < 3; k++) {
                float[] vtx = verts.get(s + k);
                vtx[3] = nx;
                vtx[4] = ny;
                vtx[5] = nz;
            }
        }

        int n = verts.size();
        float[] data = new float[n * Mesh.FLOATS_PER_VERTEX];
        int[] indices = new int[n];
        float radius = 0f, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float[] v = verts.get(i);
            System.arraycopy(v, 0, data, i * Mesh.FLOATS_PER_VERTEX, Mesh.FLOATS_PER_VERTEX);
            indices[i] = i;
            float d = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            if (d > radius) radius = d;
            if (v[1] < minY) minY = v[1];
            if (v[1] > maxY) maxY = v[1];
        }
        return new Mesh(data, indices, radius, minY, maxY);
    }

    private static void emit(ArrayList<float[]> out, ArrayList<float[]> positions,
                             ArrayList<float[]> normals, int[] ref, float[] color,
                             float tr, float tg, float tb) {
        float[] v = new float[Mesh.FLOATS_PER_VERTEX];
        float[] p = positions.get(ref[0]);
        v[0] = p[0];
        v[1] = p[1];
        v[2] = p[2];
        if (ref[1] >= 0 && ref[1] < normals.size()) {
            float[] nn = normals.get(ref[1]);
            v[3] = nn[0];
            v[4] = nn[1];
            v[5] = nn[2];
        }
        v[6] = color[0] * tr;
        v[7] = color[1] * tg;
        v[8] = color[2] * tb;
        v[9] = 0f;                       // kemik yok: statik model
        out.add(v);
    }

    /** "12", "12/3", "12//5", "12/3/5" ve negatif indeksler. */
    private static int[] parseVertexRef(String s, int posCount, int normCount) {
        String[] parts = s.split("/");
        int pi = toIndex(parts[0], posCount);
        if (pi < 0) return null;
        int ni = parts.length >= 3 && !parts[2].isEmpty()
                ? toIndex(parts[2], normCount) : -1;
        return new int[]{pi, ni};
    }

    private static int toIndex(String s, int count) {
        if (s.isEmpty()) return -1;
        try {
            int i = Integer.parseInt(s);
            // OBJ indeksleri 1'den başlar; negatifse sondan sayılır
            return i > 0 ? i - 1 : count + i;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static float parse(String[] t, int i) {
        try {
            return i < t.length ? Float.parseFloat(t[i]) : 0f;
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    /** MTL dosyasından malzeme adı -> dağınık renk eşlemesi. */
    private static void readMaterials(AssetManager assets, String objPath, String mtlName,
                                      HashMap<String, float[]> out) {
        if (assets == null) return;
        int slash = objPath.lastIndexOf('/');
        String dir = slash < 0 ? "" : objPath.substring(0, slash + 1);
        try (InputStream in = assets.open(dir + mtlName)) {
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            String name = null;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] t = line.split("\\s+");
                if (t[0].equals("newmtl") && t.length > 1) {
                    name = t[1];
                    out.put(name, new float[]{0.8f, 0.8f, 0.8f});
                } else if (t[0].equals("Kd") && name != null && t.length >= 4) {
                    out.put(name, new float[]{parse(t, 1), parse(t, 2), parse(t, 3)});
                }
            }
        } catch (IOException | RuntimeException e) {
            // MTL yoksa varsayılan gri kullanılır; model yine de yüklenir.
        }
    }
}
