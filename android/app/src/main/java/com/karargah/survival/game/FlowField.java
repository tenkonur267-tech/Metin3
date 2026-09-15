package com.karargah.survival.game;

/**
 * Reaktöre giden akış alanı. Dijkstra ile her hücrenin "çekirdeğe uzaklığı"
 * hesaplanır; duvarlar geçilmez değil, sadece pahalıdır. Böylece zombiler önce
 * açık yolu arar, yol yoksa en zayıf duvarı kırmayı seçer.
 */
public class FlowField {
    private static final int N = Balance.GRID;
    private static final float INF = Float.MAX_VALUE * 0.5f;

    public final float[] dist = new float[N * N];
    private final boolean[] closed = new boolean[N * N];
    private final int[] heap = new int[N * N * 4 + 8];
    private final float[] heapKey = new float[N * N * 4 + 8];
    private int heapSize;

    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};
    private static final float[] DCOST = {1f, 1f, 1f, 1f, 1.414f, 1.414f, 1.414f, 1.414f};

    private final float[] enterCost = new float[N * N];

    public void compute(BuildGrid grid) {
        for (int gz = 0; gz < N; gz++) {
            for (int gx = 0; gx < N; gx++) {
                int i = gz * N + gx;
                dist[i] = INF;
                closed[i] = false;
                Structure s = grid.at(gx, gz);
                float c = 1f;
                if (s != null && s.alive) {
                    if (s.blocks()) {
                        // Duvarı kırmanın bedeli canıyla orantılı.
                        c = 9f + s.hp / 26f;
                    } else {
                        c = 1.8f; // tuzaklardan geçmek biraz caydırıcı
                    }
                }
                enterCost[i] = c;
            }
        }

        heapSize = 0;
        int c0 = N / 2 - Balance.CORE_CELLS / 2;
        for (int gz = c0; gz < c0 + Balance.CORE_CELLS; gz++) {
            for (int gx = c0; gx < c0 + Balance.CORE_CELLS; gx++) {
                int i = gz * N + gx;
                dist[i] = 0f;
                push(i, 0f);
            }
        }

        while (heapSize > 0) {
            int cur = pop();
            if (closed[cur]) continue;
            closed[cur] = true;
            int cx = cur % N, cz = cur / N;
            float d = dist[cur];
            for (int k = 0; k < 8; k++) {
                int nx = cx + DX[k], nz = cz + DZ[k];
                if (nx < 0 || nz < 0 || nx >= N || nz >= N) continue;
                int ni = nz * N + nx;
                if (closed[ni]) continue;
                float nd = d + DCOST[k] * enterCost[ni];
                if (nd < dist[ni]) {
                    dist[ni] = nd;
                    push(ni, nd);
                }
            }
        }
    }

    public float distAt(int gx, int gz) {
        if (gx < 0 || gz < 0 || gx >= N || gz >= N) return INF;
        return dist[gz * N + gx];
    }

    public boolean reachable(int gx, int gz) {
        return distAt(gx, gz) < INF;
    }

    /**
     * Verilen hücreden çekirdeğe doğru en iyi komşuyu bulur.
     * @return komşu hücre indeksi ya da -1
     */
    public int bestNeighbor(int gx, int gz) {
        float best = distAt(gx, gz);
        int bestIdx = -1;
        for (int k = 0; k < 8; k++) {
            int nx = gx + DX[k], nz = gz + DZ[k];
            if (nx < 0 || nz < 0 || nx >= N || nz >= N) continue;
            float d = dist[nz * N + nx];
            if (d < best) {
                best = d;
                bestIdx = nz * N + nx;
            }
        }
        return bestIdx;
    }

    public static int cellX(int index) {
        return index % N;
    }

    public static int cellZ(int index) {
        return index / N;
    }

    // ---- ikili yığın ----------------------------------------------------

    private void push(int cell, float key) {
        if (heapSize + 2 >= heap.length) return; // taşma koruması (tembel silme yüzünden)
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
