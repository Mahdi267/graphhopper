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
package com.graphhopper.util.shapes;

import com.github.javafaker.Faker;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.PointList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class BBoxTest {
    @Test
    public void testCreate() {
        DistanceCalc c = new DistanceCalcEarth();
        BBox b = c.createBBox(52, 10, 100000);

        // The calculated bounding box has no negative values (also for southern hemisphere and negative meridians)
        // and the ordering is always the same (top to bottom and left to right)
        assertEquals(52.8993, b.maxLat, 1e-4);
        assertEquals(8.5393, b.minLon, 1e-4);

        assertEquals(51.1007, b.minLat, 1e-4);
        assertEquals(11.4607, b.maxLon, 1e-4);
    }

    @Test
    public void testContains() {
        assertTrue(new BBox(1, 2, 0, 1).contains(new BBox(1, 2, 0, 1)));
        assertTrue(new BBox(1, 2, 0, 1).contains(new BBox(1.5, 2, 0.5, 1)));
        assertFalse(new BBox(1, 2, 0, 0.5).contains(new BBox(1.5, 2, 0.5, 1)));
    }

    @Test
    public void testIntersect() {
        //    ---
        //    | |
        // ---------
        // |  | |  |
        // --------
        //    |_|
        //

        // use ISO 19115 standard (minLon, maxLon followed by minLat(south!),maxLat)
        assertTrue(new BBox(12, 15, 12, 15).intersects(new BBox(13, 14, 11, 16)));
        // assertFalse(new BBox(15, 12, 12, 15).intersects(new BBox(16, 15, 11, 14)));

        // DOES NOT WORK: use bottom to top coord for lat
        // assertFalse(new BBox(6, 2, 11, 6).intersects(new BBox(5, 3, 12, 5)));
        // so, use bottom-left and top-right corner!
        assertTrue(new BBox(2, 6, 6, 11).intersects(new BBox(3, 5, 5, 12)));

        // DOES NOT WORK: use bottom to top coord for lat and right to left for lon
        // assertFalse(new BBox(6, 11, 11, 6).intersects(new BBox(5, 10, 12, 7)));
        // so, use bottom-right and top-left corner
        assertTrue(new BBox(6, 11, 6, 11).intersects(new BBox(7, 10, 5, 12)));
    }

    @Test
    public void testPointListIntersect() {
        BBox bbox = new BBox(-0.5, 1, 1, 2);
        PointList pointList = new PointList();
        pointList.add(5, 5);
        pointList.add(5, 0);
        assertFalse(bbox.intersects(pointList));

        pointList.add(-5, 0);
        assertTrue(bbox.intersects(pointList));

        pointList = new PointList();
        pointList.add(5, 1);
        pointList.add(-1, 0);
        assertTrue(bbox.intersects(pointList));

        pointList = new PointList();
        pointList.add(5, 0);
        pointList.add(-1, 3);
        assertFalse(bbox.intersects(pointList));

        pointList = new PointList();
        pointList.add(5, 0);
        pointList.add(-1, 2);
        assertTrue(bbox.intersects(pointList));

        pointList = new PointList();
        pointList.add(1.5, -2);
        pointList.add(1.5, 2);
        assertTrue(bbox.intersects(pointList));
    }

    @Test
    public void testCalculateIntersection() {
        BBox b1 = new BBox(0, 2, 0, 1);
        BBox b2 = new BBox(-1, 1, -1, 2);
        BBox expected = new BBox(0, 1, 0, 1);

        assertEquals(expected, b1.calculateIntersection(b2));

        //No intersection
        b2 = new BBox(100, 200, 100, 200);
        assertNull(b1.calculateIntersection(b2));

        //Real Example
        b1 = new BBox(8.8591,9.9111,48.3145,48.8518);
        b2 = new BBox(5.8524,17.1483,46.3786,55.0653);

        assertEquals(b1, b1.calculateIntersection(b2));
    }

    @Test
    public void testParseTwoPoints() {
        assertEquals(new BBox(2, 4, 1, 3), BBox.parseTwoPoints("1,2,3,4"));
        // stable parsing, i.e. if first point is in north or south it does not matter:
        assertEquals(new BBox(2, 4, 1, 3), BBox.parseTwoPoints("3,2,1,4"));
    }

    @Test
    public void testParseBBoxString() {
        assertEquals(new BBox(2, 4, 1, 3), BBox.parseBBoxString("2,4,1,3"));
    }

    /**
     * @author Walid Bouhazza
     */
    @Test
    public void testInvalidBBox() {
        // minLon > maxLon et minLat > maxLat => bbox incohérente
        BBox invalid = new BBox(10, -10, 5, -5);

        // Intention : isValid() doit détecter que la bbox n'est pas réaliste
        assertFalse(invalid.isValid());
    }

    @Test
    public void testUpdateWithRandomCoordinates_Faker () {
        Faker faker = new Faker();
        // Point initial
        double lat1 = faker.number().randomDouble(6, -90, 90);
        double lon1 = faker.number().randomDouble(6, -180, 180);
        BBox bbox = new BBox(lon1, lon1, lat1, lat1);

        // Nouveau point aléatoire
        double lat2 = faker.number().randomDouble(6, -90, 90);
        double lon2 = faker.number().randomDouble(6, -180, 180);
        bbox.update(lat2, lon2);

        // Intention : après update, la bbox doit contenir les deux points
        assertTrue(bbox.contains(lat1, lon1));
        assertTrue(bbox.contains(lat2, lon2));
    }

    /**
     * Teste la gestion de l'élévation dans BBox, incluant la création inverse,
     * la mise à jour avec élévation, et le clonage.
     *
     * <p>Ce test vérifie que les BBox avec élévation fonctionnent correctement
     * pour toutes les opérations de base : création, mise à jour, clonage et validation.</p>
     *
     * <p><strong>Configuration du test :</strong></p>
     * <ul>
     *   <li>Crée une BBox inverse avec élévation pour permettre l'expansion</li>
     *   <li>Met à jour avec plusieurs points ayant différentes élévations</li>
     *   <li>Vérifie le clonage et l'indépendance des copies</li>
     *   <li>Teste la validation avec des élévations valides et invalides</li>
     * </ul>
     *
     * <p><strong>Assertions du test :</strong></p>
     * <ul>
     *   <li>La BBox inverse a les bonnes valeurs initiales extrêmes</li>
     *   <li>update() avec élévation met à jour correctement min/max elevation</li>
     *   <li>clone() crée une copie indépendante avec les mêmes valeurs</li>
     *   <li>hasElevation() retourne true pour les BBox 3D</li>
     *   <li>isValid() détecte les configurations invalides d'élévation</li>
     * </ul>
     *
     * <p><strong>Couverture de code :</strong></p>
     * <ul>
     *   <li>Teste createInverse(true) - jamais testé auparavant</li>
     *   <li>Teste update(lat, lon, elev) - jamais testé</li>
     *   <li>Teste clone() avec élévation - jamais testé</li>
     *   <li>Teste hasElevation() - jamais testé</li>
     *   <li>Teste les conditions de validation d'élévation dans isValid()</li>
     * </ul>
     *
     * <p><strong>Mutants ciblés :</strong></p>
     * Ce test détecte spécifiquement les mutants suivants :
     * <ul>
     *   <li>Changements de comparaison dans update() pour élévation (>, <)</li>
     *   <li>Mutation du flag elevation dans clone()</li>
     *   <li>Conditions de validation dans isValid() pour minEle/maxEle</li>
     *   <li>Valeurs initiales dans createInverse()</li>
     * </ul>
     *
     * @throws AssertionError si la gestion de l'élévation est incorrecte
     */
    @Test
    public void testElevationHangLing() {
        BBox bbox = BBox.createInverse(true);

        assertTrue(bbox.hasElevation(),
                "Une BBox créée avec createInverse(true) devrait avoir l'élévation activée");

        assertEquals(Double.MAX_VALUE, bbox.minLon,
                "minLon devrait être initialisé à MAX_VALUE pour une BBox inverse");
        assertEquals(-Double.MAX_VALUE, bbox.maxLon,
                "maxLon devrait être initialisé à -MAX_VALUE pour une BBox inverse");
        assertEquals(Double.MAX_VALUE, bbox.minLat,
                "minLat devrait être initialisé à MAX_VALUE pour une BBox inverse");
        assertEquals(-Double.MAX_VALUE, bbox.maxLat,
                "maxLat devrait être initialisé à -MAX_VALUE pour une BBox inverse");
        assertEquals(Double.MAX_VALUE, bbox.minEle,
                "minEle devrait être initialisé à MAX_VALUE pour une BBox inverse");
        assertEquals(-Double.MAX_VALUE, bbox.maxEle,
                "maxEle devrait être initialisé à -MAX_VALUE pour une BBox inverse");

        // Mise à jour avec des points ayant des élévations

        bbox.update(50.0, 10.0, 100.00);

        assertEquals(50.0, bbox.minLat, "minLat devrait être mis à jour à 50");
        assertEquals(50.0, bbox.maxLat, "minLat devrait être mis à jour à 50");
        assertEquals(10.0, bbox.minLon, "minLat devrait être mis à jour à 10");
        assertEquals(10.0, bbox.maxLon, "minLat devrait être mis à jour à 10");
        assertEquals(100.0, bbox.minEle, "minLat devrait être mis à jour à 100");
        assertEquals(100.0, bbox.maxEle, "minLat devrait être mis à jour à 100");

        bbox.update(52.0, 12.0, 150.0);

        assertEquals(50.0, bbox.minLat, "minLat devrait rester 50");
        assertEquals(52.0, bbox.maxLat, "minLat devrait être mis à jour à 52");
        assertEquals(10.0, bbox.minLon, "minLat devrait rester 10");
        assertEquals(12.0, bbox.maxLon, "minLat devrait être mis à jour à 12");
        assertEquals(100.0, bbox.minEle, "minLat devrait rester 100");
        assertEquals(150.0, bbox.maxEle, "minLat devrait être mis à jour à 150");

        bbox.update(48.0, 8.0, 50.0);

        assertEquals(48.0, bbox.minLat, "minLat devrait être mis à jour à 48");
        assertEquals(52.0, bbox.maxLat, "minLat devrait rester 52");
        assertEquals(8.0, bbox.minLon, "minLat devrait être mis à jour à 8");
        assertEquals(12.0, bbox.maxLon, "minLat devrait rester 12");
        assertEquals(50.0, bbox.minEle, "minLat devrait être mis à jour à 50");
        assertEquals(150.0, bbox.maxEle, "minLat devrait rester 150");

        // Test de clonage
        BBox cloned = bbox.clone();

        assertEquals(bbox.minLon, cloned.minLon, "Clone devrait avoir le même minLon");
        assertEquals(bbox.maxLon, cloned.maxLon, "Clone devrait avoir le même maxLon");
        assertEquals(bbox.minLat, cloned.minLat, "Clone devrait avoir le même minLat");
        assertEquals(bbox.maxLat, cloned.maxLat, "Clone devrait avoir le même maxLat");
        assertEquals(bbox.minEle, cloned.minEle, "Clone devrait avoir le même minEle");
        assertEquals(bbox.maxEle, cloned.maxEle, "Clone devrait avoir le même maxEle");
        assertEquals(bbox.hasElevation(), cloned.hasElevation(),
                "Clone devrait avoir le même flag elevation");

        // Vérifier que modifier le clone ne modifie pas l'oroginal
        cloned.update(60.0, 20.0, 200.0);

        assertEquals(52.0, bbox.maxLat,
                "L'original ne devrait pas être affecté par la modification du clone");
        assertEquals(60.0, cloned.maxLat,
                "Le clone devrait avoir la nouvelle valeur");

        // Test de validation
        BBox validBBox = new BBox(8.0, 12.0, 48.0, 52.0, 50.0, 150.0);
        assertTrue(validBBox.isValid(), "Une BBox bien formée avec élévation devrait être valide");

        BBox invalidElevation = new BBox(8.0, 12.0, 48.0, 52.0, 150.0, 50.0);
        assertFalse(invalidElevation.isValid(),
                "Une BBox avec minEle > maxEle devrait être invalide");

        BBox equalElevation = new BBox(8.0, 12.0, 48.0, 52.0, 100.0, 100.0);
        assertTrue(equalElevation.isValid(),
                "Une BBox avec minEle == maxEle devrait être valide");

        BBox bboxNoElevation = new BBox(8.0, 12.0, 48.0, 52.0);
        assertFalse(bboxNoElevation.hasElevation(),
                "Une BBox sans élévation devrait retourner false pour hasElevation");

        IllegalStateException ise = assertThrows(IllegalStateException.class,
                () -> bboxNoElevation.update(50.0, 10.0, 100.0),
                "update() avec élévation devrait lever IllegalArgumentException sur une BBox sans élévation");

        assertTrue(ise.getMessage().contains("No BBox with elevation"),
                "Le message d'exception devrait mentionner 'No BBox with elevation'");
    }
}
