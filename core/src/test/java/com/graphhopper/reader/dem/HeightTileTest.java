/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.reader.dem;

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.RAMDirectory;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/**
 * @author Peter Karich
 */
public class HeightTileTest {
    @Test
    public void testGetHeight() {
        // data access has same coordinate system as graphical or UI systems have (or the original DEM data has).
        // But HeightTile has lat,lon system ('mathematically')
        int width = 10;
        int height = 20;
        HeightTile instance = new HeightTile(0, 0, width, height, 1e-6, 10, 20);
        DataAccess heights = new RAMDirectory().create("tmp");
        heights.create(2 * width * height);
        instance.setHeights(heights);
        init(heights, width, height, 1);

        // x,y=1,7
        heights.setShort(2 * (17 * width + 1), (short) 70);

        // x,y=2,9
        heights.setShort(2 * (19 * width + 2), (short) 90);

        assertEquals(1, instance.getHeight(5, 5), 1e-3);
        assertEquals(70, instance.getHeight(2.5, 1.5), 1e-3);
        // edge cases for one tile with the boundaries [min,min+degree/width) for lat and lon
        assertEquals(1, instance.getHeight(3, 2), 1e-3);
        assertEquals(70, instance.getHeight(2, 1), 1e-3);

        // edge cases for the whole object
        assertEquals(1, instance.getHeight(+1.0, 2), 1e-3);
        assertEquals(90, instance.getHeight(0.5, 2.5), 1e-3);
        assertEquals(90, instance.getHeight(0.0, 2.5), 1e-3);
        assertEquals(1, instance.getHeight(+0.0, 3), 1e-3);
        assertEquals(1, instance.getHeight(-0.5, 3.5), 1e-3);
        assertEquals(1, instance.getHeight(-0.5, 3.0), 1e-3);
        // fall back to "2,9" if within its boundaries
        assertEquals(90, instance.getHeight(-0.5, 2.5), 1e-3);

        assertEquals(1, instance.getHeight(0, 0), 1e-3);
        assertEquals(1, instance.getHeight(9, 10), 1e-3);
        assertEquals(1, instance.getHeight(10, 9), 1e-3);
        assertEquals(1, instance.getHeight(10, 10), 1e-3);

        // no error
        assertEquals(1, instance.getHeight(10.5, 5), 1e-3);
        assertEquals(1, instance.getHeight(-0.5, 5), 1e-3);
        assertEquals(1, instance.getHeight(1, -0.5), 1e-3);
        assertEquals(1, instance.getHeight(1, 10.5), 1e-3);
    }

    @Test
    public void testGetHeightForNegativeTile() {
        int width = 10;
        HeightTile instance = new HeightTile(-20, -20, width, width, 1e-6, 10, 10);
        DataAccess heights = new RAMDirectory().create("tmp");
        heights.create(2 * 10 * 10);
        instance.setHeights(heights);
        init(heights, width, width, 1);

        // x,y=1,7
        heights.setShort(2 * (7 * width + 1), (short) 70);

        // x,y=2,9
        heights.setShort(2 * (9 * width + 2), (short) 90);

        assertEquals(1, instance.getHeight(-15, -15), 1e-3);
        assertEquals(70, instance.getHeight(-17.5, -18.5), 1e-3);
        // edge cases for one tile with the boundaries [min,min+degree/width) for lat and lon
        assertEquals(1, instance.getHeight(-17, -18), 1e-3);
        assertEquals(70, instance.getHeight(-18, -19), 1e-3);
    }

    @Test
    public void testInterpolate() {
        HeightTile instance = new HeightTile(0, 0, 2, 2, 1e-6, 10, 10).setInterpolate(true);
        DataAccess heights = new RAMDirectory().create("tmp");
        heights.create(2 * 2 * 2);
        instance.setHeights(heights);
        double topLeft = 0;
        double topRight = 1;
        double bottomLeft = 2;
        double bottomRight = 3;
        set(heights, 2, 0, 0, (short) topLeft);
        set(heights, 2, 1, 0, (short) topRight);
        set(heights, 2, 0, 1, (short) bottomLeft);
        set(heights, 2, 1, 1, (short) bottomRight);

        // corners
        assertEquals(bottomLeft, instance.getHeight(0, 0), 1e-3);
        assertEquals(topLeft, instance.getHeight(10, 0), 1e-3);
        assertEquals(bottomRight, instance.getHeight(0, 10), 1e-3);
        assertEquals(topRight, instance.getHeight(10, 10), 1e-3);

        // midpoints
        assertEquals(avg(topLeft, topRight), instance.getHeight(10, 5), 1e-3);
        assertEquals(avg(bottomLeft, bottomRight), instance.getHeight(0, 5), 1e-3);
        assertEquals(avg(topLeft, bottomLeft), instance.getHeight(5, 0), 1e-3);
        assertEquals(avg(topRight, bottomRight, topLeft, bottomLeft), instance.getHeight(5, 5), 1e-3);

        // missing data uses whatever remains
        set(heights, 2, 1, 0, Short.MIN_VALUE);
        set(heights, 2, 0, 1, Short.MIN_VALUE);
        set(heights, 2, 1, 1, Short.MIN_VALUE);
        assertEquals(topLeft, instance.getHeight(0, 0), 1e-3);
        assertEquals(topLeft, instance.getHeight(10, 0), 1e-3);
        assertEquals(topLeft, instance.getHeight(0, 10), 1e-3);
        assertEquals(topLeft, instance.getHeight(10, 10), 1e-3);

        // when all data missing, returns NaN
        set(heights, 2, 0, 0, Short.MIN_VALUE);
        assertEquals(Double.NaN, instance.getHeight(5, 5), 1e-3);
    }

    private void init(DataAccess da, int width, int height, int i) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                set(da, width, x, y, (short) 1);
            }
        }
    }

    private void set(DataAccess da, int width, int x, int y, short height) {
        da.setShort(2 * (y * width + x), height);
    }

    private double avg(double... ns) {
        double sum = 0;
        for (double n : ns) {
            sum += n;
        }
        return sum / ns.length;
    }

    /**
     * Test des limites d'élévation avec mock pour tuer les mutations dans isValidElevation()
     *
     * Classes simulées:
     * - DataAccess: permet de contrôler exactement les valeurs d'élévation retournées
     *
     * Justification du mock:
     * - Évite la complexité de RAMDirectory et DataAccess réels
     * - Permet de tester EXACTEMENT les valeurs limites (-12000, 9000)
     * - Isole la logique de validation d'élévation
     *
     * Mutations tuées:
     * - Changements d'opérateurs: > vers >=, < vers <=
     * - Négations de conditions
     * - Remplacement du retour NaN par la valeur
     * - Modifications des constantes MIN/MAX
     */
    @Test
    public void testElevationValidationBoundaries() {
        DataAccess mockDataAccess = mock(DataAccess.class);

        int width = 2;
        int height = 2;
        HeightTile tile = new HeightTile(0, 0, width, height, 1e-6, 1, 1);

        when(mockDataAccess.getHeader(0)).thenReturn(0);
        tile.setHeights(mockDataAccess);
        tile.setInterpolate(false); // --> la valeur renvoyée provient directement de l’échantillon

        // Test 1: Valeur juste EN DESSOUS de MIN (-12001)
        when(mockDataAccess.getShort(2L)).thenReturn((short) -12001);
        double result1 = tile.getHeight(0.5, 0.5);
        assertTrue(Double.isNaN(result1),
                "Élévation -12001 < MIN_ELEVATION (-12000) doit retourner NaN");

        // Test 2: Valeur EXACTEMENT MIN (-12000)
        when(mockDataAccess.getShort(2L)).thenReturn((short) -12000);
        double result2 = tile.getHeight(0.5, 0.5);
        assertTrue(Double.isNaN(result2),
                "Élévation -12000 = MIN_ELEVATION (non strictement supérieur) doit retourner NaN");

        // Test 3: Valeur juste AU DESSUS de MIN (-11999)
        when(mockDataAccess.getShort(2L)).thenReturn((short) -11999);
        double result3 = tile.getHeight(0.5, 0.5);
        assertFalse(Double.isNaN(result3),
                "Élévation -11999 > MIN_ELEVATION doit être valide");
        assertEquals(-11999.0, result3, 1e-3);

        // Test 4: Valeur juste AU DESSUS de MAX (9001)
        when(mockDataAccess.getShort(2L)).thenReturn((short) 9001);
        double result4 = tile.getHeight(0.5, 0.5);
        assertTrue(Double.isNaN(result4),
                "Élévation 9001 > MAX_ELEVATION (9000) doit retourner NaN");

        // Test 5: Valeur EXACTEMENT MAX (9000)
        when(mockDataAccess.getShort(2L)).thenReturn((short) 9000);
        double result5 = tile.getHeight(0.5, 0.5);
        assertTrue(Double.isNaN(result5),
                "Élévation 9000 = MAX_ELEVATION (non strictement inférieur) doit retourner NaN");

        // Test 6: Valeur juste EN DESSOUS de MAX (8999)
        when(mockDataAccess.getShort(2L)).thenReturn((short) 8999);
        double result6 = tile.getHeight(0.5, 0.5);
        assertFalse(Double.isNaN(result6),
                "Élévation 8999 < MAX_ELEVATION doit être valide");
        assertEquals(8999.0, result6, 1e-3);

        // Test 7: Valeur au centre de la plage valide
        when(mockDataAccess.getShort(2L)).thenReturn((short) 500);
        double result7 = tile.getHeight(0.5, 0.5);
        assertFalse(Double.isNaN(result7),
                "Élévation 500 dans plage valide doit retourner la valeur");
        assertEquals(500.0, result7, 1e-3);

        // Test 8: Zéro (cas important)
        when(mockDataAccess.getShort(2L)).thenReturn((short) 0);
        double result8 = tile.getHeight(0.5, 0.5);
        assertFalse(Double.isNaN(result8),
                "Élévation 0 doit être valide");
        assertEquals(0.0, result8, 1e-3);

        verify(mockDataAccess, atLeast(8)).getShort(2L);
    }
}
