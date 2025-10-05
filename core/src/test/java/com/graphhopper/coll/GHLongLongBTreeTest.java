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
package com.graphhopper.coll;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Peter Karich
 */
public class GHLongLongBTreeTest {

    @Test
    public void testThrowException_IfPutting_NoNumber() {
        GHLongLongBTree instance = new GHLongLongBTree(2, 4, -1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> instance.put(1, -1));
        assertTrue(ex.getMessage().contains("Value cannot be the 'empty value' -1"));
    }

    @Test
    public void testEmptyValueIfMissing() {
        GHLongLongBTree instance = new GHLongLongBTree(2, 4, -1);
        long key = 9485854858458484L;
        assertEquals(-1, instance.put(key, 21));
        assertEquals(21, instance.get(key));
        assertEquals(-1, instance.get(404));
    }

    @Test
    public void testTwoSplits() {
        GHLongLongBTree instance = new GHLongLongBTree(3, 4, -1);
        instance.put(1, 2);
        instance.put(2, 4);
        instance.put(3, 6);

        assertEquals(1, instance.height());
        instance.put(4, 8);
        assertEquals(2, instance.height());

        instance.put(5, 10);
        instance.put(6, 12);
        instance.put(7, 14);
        instance.put(8, 16);
        instance.put(9, 18);

        assertEquals(2, instance.height());
        instance.put(10, 20);
        assertEquals(3, instance.height());

        assertEquals(3, instance.height());
        assertEquals(10, instance.getSize());
        assertEquals(0, instance.getMemoryUsage());

        check(instance, 1);
    }

    @Test
    public void testSplitAndOverwrite() {
        GHLongLongBTree instance = new GHLongLongBTree(3, 4, -1);
        instance.put(1, 2);
        instance.put(2, 4);
        instance.put(3, 6);
        instance.put(2, 5);

        assertEquals(3, instance.getSize());
        assertEquals(1, instance.height());

        assertEquals(5, instance.get(2));
        assertEquals(6, instance.get(3));
    }

    void check(GHLongLongBTree instance, int from) {
        for (int i = from; i < instance.getSize(); i++) {
            assertEquals(i * 2L, instance.get(i), "idx:" + i);
        }
    }

    @Test
    public void testPut() {
        GHLongLongBTree instance = new GHLongLongBTree(3, 4, -1);
        instance.put(2, 4);
        assertEquals(4, instance.get(2));

        instance.put(7, 14);
        assertEquals(4, instance.get(2));
        assertEquals(14, instance.get(7));

        instance.put(5, 10);
        instance.put(6, 12);
        instance.put(3, 6);
        instance.put(4, 8);
        instance.put(9, 18);
        instance.put(0, 0);
        instance.put(1, 2);
        instance.put(8, 16);

        check(instance, 0);

        instance.put(10, 20);
        instance.put(11, 22);

        assertEquals(12, instance.getSize());
        assertEquals(3, instance.height());

        assertEquals(12, instance.get(6));
        check(instance, 0);
    }

    @Test
    public void testUpdate() {
        GHLongLongBTree instance = new GHLongLongBTree(2, 4, -1);
        long result = instance.put(100, 10);
        assertEquals(instance.getEmptyValue(), result);

        result = instance.get(100);
        assertEquals(10, result);

        result = instance.put(100, 9);
        assertEquals(10, result);

        result = instance.get(100);
        assertEquals(9, result);
    }

    @Test
    public void testNegativeValues() {
        GHLongLongBTree instance = new GHLongLongBTree(2, 5, -1);

        // negative => two's complement
        byte[] bytes = instance.fromLong(-3);
        assertEquals(-3, instance.toLong(bytes));

        instance.put(0, -3);
        instance.put(4, -2);
        instance.put(3, Integer.MIN_VALUE);
        instance.put(2, 2L * Integer.MIN_VALUE);
        instance.put(1, 4L * Integer.MIN_VALUE);

        assertEquals(-3, instance.get(0));
        assertEquals(-2, instance.get(4));
        assertEquals(4L * Integer.MIN_VALUE, instance.get(1));
        assertEquals(2L * Integer.MIN_VALUE, instance.get(2));
        assertEquals(Integer.MIN_VALUE, instance.get(3));
    }

    @Test
    public void testNegativeKey() {
        GHLongLongBTree instance = new GHLongLongBTree(2, 5, -1);

        instance.put(-3, 0);
        instance.put(-2, 4);
        instance.put(Integer.MIN_VALUE, 3);
        instance.put(2L * Integer.MIN_VALUE, 2);
        instance.put(4L * Integer.MIN_VALUE, 1);

        assertEquals(0, instance.get(-3));
        assertEquals(4, instance.get(-2));
        assertEquals(1, instance.get(4L * Integer.MIN_VALUE));
        assertEquals(2, instance.get(2L * Integer.MIN_VALUE));
        assertEquals(3, instance.get(Integer.MIN_VALUE));
    }

    @Test
    public void testInternalFromToLong() {
        Random rand = new Random(0);
        for (int byteCnt = 4; byteCnt < 9; byteCnt++) {
            for (int i = 0; i < 1000; i++) {
                GHLongLongBTree instance = new GHLongLongBTree(2, byteCnt, -1);
                long val = rand.nextLong() % instance.getMaxValue();
                byte[] bytes = instance.fromLong(val);
                assertEquals(val, instance.toLong(bytes));
            }
        }
    }

    @Test
    public void testDifferentEmptyValue() {
        GHLongLongBTree instance = new GHLongLongBTree(2, 3, -2);
        instance.put(123, -1);
        instance.put(12, 2);
        assertEquals(-2, instance.get(1234));
        assertEquals(-1, instance.get(123));
        assertEquals(2, instance.get(12));
    }

    @Test
    public void testLargeValue() {
        GHLongLongBTree instance = new GHLongLongBTree(2, 5, -1);
        for (int key = 0; key < 100; key++) {
            long val = 1L << 32 - 1;
            for (int i = 0; i < 8; i++) {
                instance.put(key, val);
                assertEquals(val, instance.get(key), "i:" + i + ", key:" + key + ", val:" + val);
                val *= 2;
            }
        }
    }

    @Test
    public void testRandom() {
        final long seed = System.nanoTime();
        Random rand = new Random(seed);
        final int size = 10_000;
        for (int bytesPerValue = 4; bytesPerValue <= 8; bytesPerValue++) {
            for (int j = 3; j < 12; j += 4) {
                GHLongLongBTree instance = new GHLongLongBTree(j, bytesPerValue, -1);
                Set<Integer> addedValues = new LinkedHashSet<>(size);
                for (int i = 0; i < size; i++) {
                    int val = rand.nextInt();
                    addedValues.add(val);
                    try {
                        instance.put(val, val);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        fail(j + "| Problem with " + i + ", seed: " + seed + " " + ex);
                    }

                    assertEquals(addedValues.size(), instance.getSize(), j + "| Size not equal to set! In " + i + " added " + val);
                }
                int i = 0;
                for (int val : addedValues) {
                    assertEquals(val, instance.get(val), j + "| Problem with " + i);
                    i++;
                }
                instance.optimize();
                i = 0;
                for (int val : addedValues) {
                    assertEquals(val, instance.get(val), j + "| Problem with " + i);
                    i++;
                }
            }
        }
    }

    /**
     * Teste les comportements aux limites et cas critiques pour détecter les mutants survivants.
     *
     * <p>Ce test combine plusieurs scénarios critiques dans un seul test exhaustif pour maximiser
     * la détection de mutants dans les zones sensibles du code : validation des limites, recherche
     * binaire, gestion des valeurs de retour, et intégrité après exceptions.</p>
     *
     * <p><strong>Configuration du test :</strong></p>
     * <ul>
     *   <li>Utilise bytesPerValue=4 pour tester les limites de maxValue</li>
     *   <li>Teste les insertions aux limites exactes (maxValue, maxValue-1, maxValue+1)</li>
     *   <li>Vérifie la recherche de clés inexistantes à différentes positions</li>
     *   <li>Teste les valeurs de retour de put() dans tous les scénarios</li>
     * </ul>
     *
     * <p><strong>Assertions du test :</strong></p>
     * <ul>
     *   <li>Validation stricte de maxValue et rejet de valeurs dépassant cette limite</li>
     *   <li>Vérification des valeurs de retour de put() pour insertions et mises à jour</li>
     *   <li>Test de recherche binaire avec clés avant, entre et après les clés existantes</li>
     *   <li>Intégrité de l'arbre après tentative d'insertion invalide</li>
     *   <li>Cohérence de getSize() dans tous les scénarios</li>
     * </ul>
     *
     * <p><strong>Couverture de code :</strong></p>
     * <ul>
     *   <li>Conditions de validation dans put() : {@code if (value > maxValue)}</li>
     *   <li>Logique de binarySearch avec différentes positions de retour</li>
     *   <li>Retour de put() : {@code rv.oldValue == null ? emptyValue : toLong(rv.oldValue)}</li>
     *   <li>Gestion des exceptions et état après exception</li>
     *   <li>get() sur clés inexistantes avec différents index négatifs</li>
     * </ul>
     *
     * <p><strong>Mutants ciblés :</strong></p>
     * Ce test détecte spécifiquement les mutants suivants :
     * <ul>
     *   <li>Changement de {@code >} en {@code >=} dans la validation maxValue</li>
     *   <li>Changement de {@code ==} en {@code !=} dans la vérification rv.oldValue</li>
     *   <li>Mutation des valeurs de retour (emptyValue vs oldValue)</li>
     *   <li>Off-by-one dans binarySearch (high, low, guess)</li>
     *   <li>Mutation de size++ en situations spécifiques</li>
     * </ul>
     *
     * @throws AssertionError si un comportement aux limites est incorrect
     */
    @Test
    public void testBoundaryConditionsAndReturnValues() {
        GHLongLongBTree instance = new GHLongLongBTree(5, 4, -1);

        long maxValue = instance.getMaxValue();

        // Insérer EXACTEMENT à maxValue (devrait réussir)
        long ret1 = instance.put(100, maxValue);
        assertEquals(-1L, ret1, "put() devrait retourner emptyValue pour une nouvelle insertion");
        assertEquals(maxValue, instance.get(100), "get(100) devrait retourner maxValue");
        assertEquals(1L, instance.getSize(), "La taille devrait être 1");

        // Insérer à maxValue - 1 (devrait réussir)
        long ret2 = instance.put(200, maxValue - 1);
        assertEquals(-1L, ret2, "put() devrait retourner emptyValue pour une nouvelle insertion");
        assertEquals(maxValue - 1, instance.get(200), "get(200) devrait retourner maxValue - 1");
        assertEquals(2L, instance.getSize(), "La taille devrait être 2");

        // Mettre à jour avec maxValue (devrait retourner l'ancienne valeur)
        long ret3 = instance.put(200, maxValue);
        assertEquals(maxValue - 1, ret3, "put() devrait retourner l'ancienne valeur lors d'une mise à jour");
        assertEquals(maxValue, instance.get(200), "La valeur devrait être mise à jour à maxValue");
        assertEquals(2L, instance.getSize(), "La taille ne devrait pas changer lors d'une mise à jour");

        // Tenter d'insérer maxValue + 1 (devrait échouer)
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> instance.put(300, maxValue + 1),
                "put() devrait lever IllegalArgumentException pour value > maxValue");
        assertTrue(ex.getMessage().contains("exceeded max value"),
                "Le message d'exception devrait mentionner 'exceeded max value'");

        // Vérifier l'intégrité après exception
        assertEquals(2L, instance.getSize(),
                "La taille ne devrait pas changer après une insertion échouée");
        assertEquals(maxValue, instance.get(100),
                "Les données existantes ne devraient pas être corrompues");
        assertEquals(maxValue, instance.get(200),
                "Les données existantes ne devraient pas être corrompues");
        assertEquals(-1L, instance.get(300),
                "La clé 300 ne devrait pas exister après insertion échouée");

        instance.clear();
        // Insérer des clés espacées : 10, 20, 30, 40, 50
        for (int i = 1; i <= 5; i++) {
            long retVal = instance.put(i * 10L, i * 100L);
            assertEquals(-1L, retVal, "Nouvelle insertion devrait retourner emptyValue");
        }

        assertEquals(5L, instance.getSize(), "Devrait avoir 5 éléments");

        // Rechercher clé AVANT toutes les autres (< 10)
        assertEquals(-1L, instance.get(5),
                "get(5) devrait retourner emptyValue (clé avant toutes les autres)");
        assertEquals(-1L, instance.get(0),
                "get(0) devrait retourner emptyValue");

        // Rechercher clés ENTRE les clés existantes
        assertEquals(-1L, instance.get(15), "get(15) devrait retourner emptyValue (entre 10 et 20)");
        assertEquals(-1L, instance.get(25), "get(25) devrait retourner emptyValue (entre 20 et 30)");
        assertEquals(-1L, instance.get(35), "get(35) devrait retourner emptyValue (entre 30 et 40)");
        assertEquals(-1L, instance.get(45), "get(45) devrait retourner emptyValue (entre 40 et 50)");

        // Rechercher clé APRÈS toutes les autres (> 50)
        assertEquals(-1L, instance.get(55),
                "get(55) devrait retourner emptyValue (clé après toutes les autres)");
        assertEquals(-1L, instance.get(100),
                "get(100) devrait retourner emptyValue");

        // Vérifier que les clés existantes sont toujours accessibles
        assertEquals(100L, instance.get(10), "get(10) devrait retourner 100");
        assertEquals(200L, instance.get(20), "get(20) devrait retourner 200");
        assertEquals(300L, instance.get(30), "get(30) devrait retourner 300");
        assertEquals(400L, instance.get(40), "get(40) devrait retourner 400");
        assertEquals(500L, instance.get(50), "get(50) devrait retourner 500");

        // Insérer une nouvelle clé au début
        long ret4 = instance.put(5, 50L);
        assertEquals(-1L, ret4, "Insertion au début devrait retourner emptyValue");
        assertEquals(6L, instance.getSize(), "La taille devrait être 6");

        // Insérer une nouvelle clé à la fin
        long ret5 = instance.put(60, 600L);
        assertEquals(-1L, ret5, "Insertion à la fin devrait retourner emptyValue");
        assertEquals(7L, instance.getSize(), "La taille devrait être 7");

        // Mettre à jour la première clé
        long ret6 = instance.put(5, 55L);
        assertEquals(50L, ret6, "Mise à jour devrait retourner l'ancienne valeur 50");
        assertEquals(55L, instance.get(5), "La valeur devrait être mise à jour à 55");
        assertEquals(7L, instance.getSize(), "La taille ne devrait pas changer");

        // Mettre à jour la dernière clé
        long ret7 = instance.put(60, 666L);
        assertEquals(600L, ret7, "Mise à jour devrait retourner l'ancienne valeur 600");
        assertEquals(666L, instance.get(60), "La valeur devrait être mise à jour à 666");
        assertEquals(7L, instance.getSize(), "La taille ne devrait pas changer");

        // Mettre à jour une clé au milieu
        long ret8 = instance.put(30, 333L);
        assertEquals(300L, ret8, "Mise à jour devrait retourner l'ancienne valeur 300");
        assertEquals(333L, instance.get(30), "La valeur devrait être mise à jour à 333");
        assertEquals(7L, instance.getSize(), "La taille ne devrait pas changer");

        // Vérifier toutes les valeurs finales
        assertEquals(55L, instance.get(5));
        assertEquals(100L, instance.get(10));
        assertEquals(200L, instance.get(20));
        assertEquals(333L, instance.get(30));
        assertEquals(400L, instance.get(40));
        assertEquals(500L, instance.get(50));
        assertEquals(666L, instance.get(60));

        // Vérifier que les clés inexistantes retournent toujours emptyValue
        assertEquals(-1L, instance.get(0));
        assertEquals(-1L, instance.get(15));
        assertEquals(-1L, instance.get(70));

        assertEquals(7L, instance.getSize(), "La taille finale devrait être 7");
    }
}
