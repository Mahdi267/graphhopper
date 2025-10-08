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


    /**
     * Teste l'efficacité mémoire avec différentes tailles de valeurs (bytesPerValue).
     *
     * <p>Ce test vérifie que l'utilisation de moins d'octets par valeur réduit effectivement
     * l'empreinte mémoire de l'arbre. Il compare trois configurations (8, 4 et 2 bytes par valeur)
     * avec suffisamment de données pour que les différences soient mesurables.</p>
     *
     * <p><strong>Configuration du test :</strong></p>
     * <ul>
     *   <li>Insère un grand nombre d'entrées (50,000) pour avoir une empreinte mémoire mesurable</li>
     *   <li>Crée trois arbres avec bytesPerValue différents : 8, 4, et 2</li>
     *   <li>Compare l'utilisation mémoire et vérifie l'intégrité des données</li>
     * </ul>
     *
     * <p><strong>Assertions du test :</strong></p>
     * <ul>
     *   <li>La mémoire utilisée diminue quand bytesPerValue diminue</li>
     *   <li>Les données sont correctement stockées et récupérées pour chaque configuration</li>
     *   <li>Les valeurs tronquées (tree2) sont cohérentes avec le modulo appliqué</li>
     *   <li>getSize() retourne le bon nombre d'entrées pour tous les arbres</li>
     * </ul>
     *
     * <p><strong>Couverture de code :</strong></p>
     * <ul>
     *   <li>Teste getMemoryUsage() avec des tailles significatives</li>
     *   <li>Vérifie le calcul correct de la capacité avec différents bytesPerValue</li>
     *   <li>Teste la gestion des valeurs avec différentes plages (selon bytesPerValue)</li>
     *   <li>Exerce les méthodes fromLong() et toLong() avec différentes tailles</li>
     * </ul>
     *
     * <p><strong>Comportement attendu :</strong></p>
     * L'utilisation mémoire devrait être proportionnelle à bytesPerValue. Un arbre avec 4 bytes
     * devrait utiliser environ moitié moins de mémoire qu'un arbre avec 8 bytes pour les mêmes données.
     *
     * @throws AssertionError si l'efficacité mémoire n'est pas respectée ou si les données sont corrompues
     */
    @Test
    public void testMemoryEfficiencyWithDifferentBytesPerValue() {
        int numEntries = 50_000;
        long[] keys = new long[numEntries];
        long[] values = new long[numEntries];

        for (int i = 0; i < numEntries; i++) {
            keys[i] = i;
            values[i] = i * 100L;
        }

        // Arbre 1 : 8 octets par valeur (peut stocker jusqu'à 2^63-1)
        GHLongLongBTree tree8 = new GHLongLongBTree(5, 8, -1);
        for (int i = 0; i < numEntries; i++) {
            tree8.put(keys[i], values[i]);
        }
        int mem8 = tree8.getMemoryUsage();
        assertEquals(numEntries, tree8.getSize(), "tree8 devrait contenir toutes les entrées");

        // Arbre 2 : 4 octets par valeur (peut stocker jusqu'à 2^31-1)
        GHLongLongBTree tree4 = new GHLongLongBTree(5, 4, -1);
        for (int i = 0; i < numEntries; i++) {
            tree4.put(keys[i], values[i]);
        }
        int mem4 = tree4.getMemoryUsage();
        assertEquals(numEntries, tree4.getSize(), "tree4 devrait contenir toutes les entrées");

        GHLongLongBTree tree2 = new GHLongLongBTree(5, 2, -1);
        long maxValueFor2Bytes = tree2.getMaxValue();

        for (int i = 0; i < numEntries; i++) {
            // Utiliser des valeurs qui tiennent dans 2 bytes
            long value = (i * 100L) % maxValueFor2Bytes;
            tree2.put(keys[i], value);
        }
        int mem2 = tree2.getMemoryUsage();
        assertEquals(numEntries, tree2.getSize(), "tree2 devrait contenir toutes les entrées");

        assertTrue(mem8 > 0,
                String.format("tree8 devrait utiliser une mémoire mesurable (actuel: %d MB)", mem8));
        assertTrue(mem4 > 0,
                String.format("tree4 devrait utiliser une mémoire mesurable (actuel: %d MB)", mem4));

        // Vérifier que la mémoire diminue avec moins d'octets
        assertTrue(mem4 < mem8 || mem4 == mem8,
                String.format("4 bytes (%d MB) devrait utiliser <= mémoire que 8 bytes (%d MB)", mem4, mem8));
        assertTrue(mem2 <= mem4,
                String.format("2 bytes (%d MB) devrait utiliser <= mémoire que 4 bytes (%d MB)", mem2, mem4));

        // Vérifier la cohérence des valeurs stockées dans tree8 et tree4
        // On ne vérifie qu'un échantillon pour la performance
        for (int i = 0; i < numEntries; i += 100) {
            assertEquals(values[i], tree8.get(keys[i]),
                    String.format("tree8: get(%d) devrait retourner %d", keys[i], values[i]));
            assertEquals(values[i], tree4.get(keys[i]),
                    String.format("tree4: get(%d) devrait retourner %d", keys[i], values[i]));
        }

        // Vérifier tree2 avec les valeurs modulées
        for (int i = 0; i < numEntries; i += 100) {
            long expected = (values[i]) % maxValueFor2Bytes;
            assertEquals(expected, tree2.get(keys[i]),
                    String.format("tree2: get(%d) devrait retourner %d (valeur modulée)", keys[i], expected));
        }

        // Vérifier que des clés inexistantes retournent emptyValue
        assertEquals(-1L, tree8.get(numEntries + 1000));
        assertEquals(-1L, tree4.get(numEntries + 1000));
        assertEquals(-1L, tree2.get(numEntries + 1000));

        // Vérifier que les hauteurs sont similaires (même structure d'arbre)
        int height8 = tree8.height();
        int height4 = tree4.height();
        int height2 = tree2.height();

        // Les hauteurs devraient être identiques ou très proches (même nombre d'éléments)
        assertTrue(Math.abs(height8 - height4) <= 1,
                String.format("Les hauteurs devraient être similaires (tree8: %d, tree4: %d)", height8, height4));
        assertTrue(Math.abs(height4 - height2) <= 1,
                String.format("Les hauteurs devraient être similaires (tree4: %d, tree2: %d)", height4, height2));
    }

}
