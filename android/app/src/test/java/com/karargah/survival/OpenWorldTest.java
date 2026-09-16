package com.karargah.survival;

import com.karargah.survival.game.Balance;
import com.karargah.survival.game.OpenWorld;
import org.junit.Test;
import static org.junit.Assert.*;

public class OpenWorldTest {
    @Test public void dunyaYuzBinBirimdir() {
        assertEquals(100000f, Balance.WORLD_HALF * 2f, 0.01f);
    }

    @Test public void sehirlerCevresindeZombiTehlikesiArtar() {
        OpenWorld w = new OpenWorld();
        float city = w.dangerAt(OpenWorld.CITIES[0][0], OpenWorld.CITIES[0][1]);
        float empty = w.dangerAt(30000f, 30000f);
        assertTrue(city > empty);
    }

    @Test public void geceGunduzDongusuCalisir() {
        OpenWorld w = new OpenWorld();
        w.timeOfDay = 0.50f;
        float day = w.night();
        w.timeOfDay = 0.02f;
        float night = w.night();
        assertTrue(night > day);
    }
}
