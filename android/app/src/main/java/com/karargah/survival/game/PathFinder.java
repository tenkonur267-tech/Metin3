package com.karargah.survival.game;

/**
 * Müttefikler için A* yol bulma. Zombiler duvarları kırabildiği için akış
 * alanını kullanır; yoldaşlar kıramaz, bu yüzden gerçek bir yol gerekir.
 * Tek bir örnek paylaşılır (oyun döngüsü tek iş parçacığında koşar).
 */
public class PathFinder {
    private static final int N = Balance.GRID;
    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};
    private static final float[] DCOST = {1f, 1f, 1f, 1f, 1.414f, 1.414f, 1.414f, 1.414f};

    private final float[] gScore = new float[N * N];
    private final int[] cameFrom = new int[N * N];
    private final boolean[] closed = new boolean[N * N];
    private final int[] visited = new int[N * N];
    private int stamp;

    private final int[] heap = new int[N * N];
    private final float[] heapKey = new float[N * N];
    private int heapSize;

    /** Son bulunan yolun hücreleri (baştan sona). */
    public final int[] path = new int[400];
    public int pathLength;
    /** Hedefe tam ulaşılamadıysa true (en yakın erişilebilir noktaya gidilir). */
    public boolean partial;

    public static boolean passable(BuildGrid grid, int gx, int gz) {
        if (!BuildGrid.inBounds(gx, gz)) return false;
        Structure s = grid.at(gx, gz);
        return s == null || !s.blocks();
    }

    /**
     * @return true ise path/pathLength dolduruldu.
     */
    public boolean find(BuildGrid grid, int sx, int sz, int tx, int tz, int maxExpand) {
        pathLength = 0;
        partial = false;
        if (!BuildGrid.inBounds(sx, sz)) return false;
        if (!passable(grid, tx, tz)) {
            long near = nearestFree(grid, tx, tz);
            if (near < 0) return false;
            tx = (int) (near % N);
            tz = (int) (near / N);
        }
        int start = sz * N + sx;
        int goal = tz * N + tx;
        if (start == goal) {
            path[0] = goal;
            pathLength = 1;
            return true;
        }

        stamp++;
        heapSize = 0;
        gScore[start] = 0f;
        cameFrom[start] = -1;
        visited[start] = stamp;
        closed[start] = false;
        push(start, heuristic(sx, sz, tx, tz));

        int expanded = 0;
        int bestNode = start;
        float bestH = heuristic(sx, sz, tx, tz);
        while (heapSize > 0) {
            int cur = pop();
            if (visited[cur] != stamp || closed[cur]) continue;
            closed[cur] = true;
            if (cur == goal) return rebuild(start, goal);
            if (++expanded > maxExpand) break;

            int cx = cur % N, cz = cur / N;
            float h = heuristic(cx, cz, tx, tz);
            if (h < bestH) {
                bestH = h;
                bestNode = cur;
            }
            for (int k = 0; k < 8; k++) {
                int nx = cx + DX[k], nz = cz + DZ[k];
                if (!passable(grid, nx, nz)) continue;
                if (k >= 4 && (!passable(grid, cx + DX[k], cz) || !passable(grid, cx, cz + DZ[k]))) {
                    continue;   // köşeden sıyrılma yok
                }
                int ni = nz * N + nx;
                if (visited[ni] == stamp && closed[ni]) continue;
                float ng = gScore[cur] + DCOST[k];
                if (visited[ni] != stamp || ng < gScore[ni]) {
                    visited[ni] = stamp;
                    closed[ni] = false;
                    gScore[ni] = ng;
                    cameFrom[ni] = cur;
                    push(ni, ng + heuristic(nx, nz, tx, tz));
                }
            }
        }
        // Hedef kapalıysa (örneğin üs tamamen duvarla çevriliyse) en yakın
        // erişilebilir noktaya git; yoldaş duvara toslayıp titremesin.
        if (bestNode != start) {
            partial = true;
            return rebuild(start, bestNode);
        }
        return false;
    }

    private boolean rebuild(int start, int goal) {
        int count = 0;
        int cur = goal;
        while (cur != -1 && count < path.length) {
            path[count++] = cur;
            if (cur == start) break;
            cur = cameFrom[cur];
        }
        // ters çevir
        for (int i = 0; i < count / 2; i++) {
            int t = path[i];
            path[i] = path[count - 1 - i];
            path[count - 1 - i] = t;
        }
        pathLength = count;
        return count > 1;
    }

    /** Hedef kapalıysa çevresindeki en yakın açık hücre. */
    private long nearestFree(BuildGrid grid, int tx, int tz) {
        for (int r = 1; r <= 5; r++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    int nx = tx + dx, nz = tz + dz;
                    if (passable(grid, nx, nz)) return (long) nz * N + nx;
                }
            }
        }
        return -1;
    }

    private static float heuristic(int ax, int az, int bx, int bz) {
        int dx = Math.abs(ax - bx), dz = Math.abs(az - bz);
        int min = Math.min(dx, dz);
        return (dx + dz) + (1.414f - 2f) * min;
    }

    /** İki nokta arasında duvar var mı (ateş hattı için; gövde genişliği yok). */
    public static boolean clearLine(BuildGrid grid, float x0, float z0, float x1, float z1) {
        return clearCorridor(grid, x0, z0, x1, z1, 0f);
    }

    /**
     * Verilen genişlikte bir koridor açık mı? Yürüyüş için gövde yarıçapı
     * verilir: yoksa hat "açık" görünüp karakter duvarın köşesine takılır.
     */
    public static boolean clearCorridor(BuildGrid grid, float x0, float z0,
                                        float x1, float z1, float radius) {
        float dx = x1 - x0, dz = z1 - z0;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.01f) return true;
        float nx = -dz / dist, nz = dx / dist;      // dike doğrultu
        int steps = (int) (dist / 0.4f) + 1;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float px = x0 + dx * t, pz = z0 + dz * t;
            if (blockedAt(grid, px, pz)) return false;
            if (radius > 0.01f) {
                if (blockedAt(grid, px + nx * radius, pz + nz * radius)) return false;
                if (blockedAt(grid, px - nx * radius, pz - nz * radius)) return false;
            }
        }
        return true;
    }

    private static boolean blockedAt(BuildGrid grid, float x, float z) {
        Structure s = grid.atWorld(x, z);
        return s != null && s.blocks();
    }

    /** Hücrenin serbest komşuları arasında hedefe en çok yaklaştıran. */
    public static int bestFreeNeighbor(BuildGrid grid, int gx, int gz, float tx, float tz) {
        int best = -1;
        float bestD = Float.MAX_VALUE;
        for (int k = 0; k < 8; k++) {
            int nx = gx + DX[k], nz = gz + DZ[k];
            if (!passable(grid, nx, nz)) continue;
            if (k >= 4 && (!passable(grid, gx + DX[k], gz) || !passable(grid, gx, gz + DZ[k]))) {
                continue;
            }
            float cx = BuildGrid.cellToWorld(nx), cz = BuildGrid.cellToWorld(nz);
            float d = (cx - tx) * (cx - tx) + (cz - tz) * (cz - tz);
            if (d < bestD) {
                bestD = d;
                best = nz * N + nx;
            }
        }
        return best;
    }

    // ---- ikili yığın ----------------------------------------------------

    private void push(int cell, float key) {
        if (heapSize + 2 >= heap.length) return;
        int i = ++heapSize;
        heap[i] = cell;
        heapKey[i] = key;
        while (i > 1) {
            int p = i >> 1;
            if (heapKey[p] <= heapKey[i]) break;
            swap(p, i);
            i = p;
        }
    }

    private int pop() {
        int top = heap[1];
        heap[1] = heap[heapSize];
        heapKey[1] = heapKey[heapSize];
        heapSize--;
        int i = 1;
        while (true) {
            int l = i << 1, r = l + 1, m = i;
            if (l <= heapSize && heapKey[l] < heapKey[m]) m = l;
            if (r <= heapSize && heapKey[r] < heapKey[m]) m = r;
            if (m == i) break;
            swap(i, m);
            i = m;
        }
        return top;
    }

    private void swap(int a, int b) {
        int t = heap[a];
        heap[a] = heap[b];
        heap[b] = t;
        float tk = heapKey[a];
        heapKey[a] = heapKey[b];
        heapKey[b] = tk;
    }
}
