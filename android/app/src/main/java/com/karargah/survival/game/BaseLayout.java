package com.karargah.survival.game;

/**
 * Üssün yerleşim düzeni. Üs iç içe halkalardan oluşur ve yer daraldıkça
 * dışarı doğru büyür:
 *
 * <pre>
 *   0 .. CORE_CLEAR      reaktör ve etrafındaki boşluk — buraya asla inşa edilmez
 *   CORE_CLEAR .. yard   avlu: kışla, cephanelik, tamir, tıbbi, jeneratör
 *   yard .. sur-1        kule kuşağı
 *   sur                  sur hattı (her kenarın ortasında geniş kapı)
 *   sur+2 .. sur+4       kapı önü tuzakları
 * </pre>
 *
 * Halka uzaklığı Chebyshev'dir (kare çerçeve), yani {@code max(|x|,|z|)}.
 * Hücre merkezleri tek sayılarda olduğu için sur yarıçapı da tek sayıdır ve
 * genişleme {@link #STEP} kadar, yine tek sayıda kalacak şekilde yapılır.
 */
public final class BaseLayout {

    private BaseLayout() {
    }

    /**
     * Reaktörün etrafında hiçbir şeyin kurulamayacağı boşluk. Reaktör
     * dünyada -4..4 arasını kaplar; 5 birim, çevresinde tam bir yürüme
     * halkası bırakır.
     */
    public static final float CORE_CLEAR = 5f;
    /**
     * Başlangıç sur yarıçapı (tek sayı). 17 birim, reaktörle sur arasında
     * dört halkalık gerçek bir avlu bırakır — eski 9 birimlik hat reaktörü
     * boğuyordu.
     */
    public static final float START_RADIUS = 17f;
    /** Her genişlemede sur kaç birim dışarı çıkar. */
    public static final float STEP = 6f;
    /** Kule kuşağının sur hattından içeri kalınlığı. */
    public static final float TURRET_BELT = 4f;
    /** Kapının yarı genişliği (birim): 3.1 -> her kenarda 3 hücrelik geçit. */
    public static final float GATE_HALF = 3.1f;

    public static float maxRadius() {
        // Sur + tuzak bandı inşa alanının içinde kalmalı
        return Balance.BUILD_RADIUS - 8f;
    }

    /** Chebyshev halka uzaklığı. */
    public static float ring(float x, float z) {
        return Math.max(Math.abs(x), Math.abs(z));
    }

    public static float ringOfCell(int gx, int gz) {
        return ring(BuildGrid.cellToWorld(gx), BuildGrid.cellToWorld(gz));
    }

    /** Sur yarıçapını hücre merkezlerine oturacak tek sayıya yuvarlar. */
    public static float snap(float radius) {
        float half = Balance.CELL * 0.5f;                 // 1
        float snapped = Math.round((radius - half) / Balance.CELL) * Balance.CELL + half;
        return Math.max(START_RADIUS, Math.min(maxRadius(), snapped));
    }

    /** Reaktör boşluğu: buraya hiçbir yapı kurulmaz. */
    public static boolean isCoreClear(float x, float z) {
        return ring(x, z) <= CORE_CLEAR;
    }

    /** Bu nokta sur hattının üstünde mi (yarıçap verildiğinde)? */
    public static boolean isWallRing(float x, float z, float radius) {
        return Math.abs(ring(x, z) - radius) < Balance.CELL * 0.5f;
    }

    /**
     * Kapı hücresi mi? Her kenarın tam ortasında {@link #GATE_HALF} yarı
     * genişliğinde bir geçit açık bırakılır; yoksa ekip de oyuncu da içeride
     * kapalı kalır.
     */
    public static boolean isGate(float x, float z) {
        float ax = Math.abs(x), az = Math.abs(z);
        // Kenarın hangi eksende olduğuna göre karşı eksenin ortası kapıdır
        return az >= ax ? ax <= GATE_HALF : az <= GATE_HALF;
    }

    /** Avlu (destek yapıları) bandı. */
    public static boolean isYard(float x, float z, float radius) {
        float r = ring(x, z);
        return r > CORE_CLEAR && r <= radius - TURRET_BELT;
    }

    /** Kule kuşağı. */
    public static boolean isTurretBelt(float x, float z, float radius) {
        float r = ring(x, z);
        return r > radius - TURRET_BELT && r < radius - Balance.CELL * 0.5f;
    }

    /** Kapı önü tuzak bandı (surun dışı). */
    public static boolean isTrapBand(float x, float z, float radius) {
        float r = ring(x, z);
        return r > radius + Balance.CELL && r <= radius + Balance.CELL * 2.5f;
    }

    /** Avludaki toplam hücre sayısı (genişleme kararı için). */
    public static int yardCapacity(float radius) {
        int inner = (int) Math.floor((radius - TURRET_BELT + 1f) / Balance.CELL) * 2;
        int core = (int) Math.floor((CORE_CLEAR + 1f) / Balance.CELL) * 2;
        return Math.max(0, inner * inner - core * core);
    }
}
