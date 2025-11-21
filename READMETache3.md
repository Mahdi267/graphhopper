# Contrôle de Qualité par Score de Mutation

## Vue d'ensemble

Ce projet implémente un système d'automatisation pour garantir la qualité de la couverture de tests en utilisant PiTest et les scores de mutation.

## Historique des approches

### Première approche : Stockage de l'ancien score

Initialement, nous avons envisagé une solution ambitieuse : faire échouer le build uniquement si le score de mutation baissait par rapport à la version précédente. Pour cela, nous avons exploré la possibilité de stocker l'ancien score de mutation en tant qu'artifact, permettant ainsi de comparer les scores entre les exécutions.

Malheureusement, cette approche s'est avérée beaucoup plus complexe à mettre en œuvre que prévu. Les difficultés de débogage et les défis techniques rencontrés nous ont amenés à abandonner cette solution.

### Approche retenue : Seuil de mutation fixe

Nous avons pivoté vers une stratégie plus pragmatique : le build échoue désormais si le score de mutation tombe en dessous de 70%. Cette approche, bien que moins sophistiquée, offre l'avantage d'être simple à comprendre, maintenir et déboguer.

### Architecture générale

Build & Test unitaires
└── mvn clean test
        ↓
Validation de la qualité (PiTest)
├── Install web-api module
├── Run PiTest → mutations.xml
├── Calculate mutation score
└── Vérifier score >= 70%

### Étapes du workflow GitHub Actions

Le workflow GitHub Actions dans le fichier `build.yaml` comprend quatre étapes principales :

#### 1. Installation du module web-api

Avant d'exécuter PiTest, nous installons d'abord le module web-api avec ses dépendances en sautant les tests pour gagner du temps. Le module core dépend de celui-ci.

```yaml
- name: Install required module
  run: mvn -pl web-api -am -DskipTests install
```

#### 2. Exécution de PiTest

Nous lançons PiTest uniquement sur le module core, en générant les rapports de mutations au format XML et HTML pour une analyse ultérieure.

```yaml
- name: Run PiTest
  working-directory: ./core
  run: mvn -B org.pitest:pitest-maven:mutationCoverage -DoutputFormats=XML,HTML
```

#### 3. Calcul du score de mutation

Cette étape analyse le fichier XML généré par PiTest pour extraire le nombre total de mutations et le nombre de mutations tuées (testées avec succès). Elle calcule ensuite le pourcentage et le stocke en tant que sortie pour utilisation dans l'étape suivante.

```yaml
- name: Calculate mutation score
  id: mutation_score
  run: |
    REPORT=./core/target/pit-reports/mutations.xml
    if [ ! -f "$REPORT" ]; then
      echo "Mutation report not found. Score = 0%"
      echo "score=0" >> $GITHUB_OUTPUT
      exit 0
    fi
    TOTAL=$(grep -c "<mutation " "$REPORT" || echo "0")
    KILLED=$(grep -c "status='KILLED'" "$REPORT" || echo "0")
    if [ "$TOTAL" -gt 0 ]; then
      SCORE=$(echo "scale=2; ($KILLED * 100) / $TOTAL" | bc)
    else
      SCORE="0"
    fi
    echo "Mutation Score: $SCORE%"
    echo "score=$SCORE" >> $GITHUB_OUTPUT
```

#### 4. Vérification du seuil

Enfin, nous comparons le score calculé au seuil minimum de 70%. Si le score est inférieur, le build échoue avec un message explicite.

```yaml
- name: FAIL if mutation score < 70%
  run: |
    SCORE="${{ steps.mutation_score.outputs.score }}"
    THRESHOLD=70

    echo "Current mutation score: $SCORE%"
    echo "Required minimum: $THRESHOLD%"

    if (( $(echo "$SCORE < $THRESHOLD" | bc -l) )); then
      echo "BUILD FAILED: Mutation score ($SCORE%) is below threshold ($THRESHOLD%)"
      exit 1
    fi

    echo "Mutation score OK (>= $THRESHOLD%)"  
``` 

## Documentation des tests

### Test : testAcceptWayWithValidWay()

#### Classe testée 

On teste la méthode acceptWay() de la classe `OSMReader`. C'est la méthode qui décide si une route du fichier OpenStreetMap est valide avant de l'ajouter au graphe.

`OSMReader` a été choisie pour être testée car c'est une classe critique du système. Elle représente le point d'entrée principal pour la validation et l'intégration des données OpenStreetMap dans le graphe routier. La méthode `acceptWay()` agit comme un filtre fondamental : elle détermine quelles routes seront effectivement ajoutées au graphe et lesquelles seront rejetées. Une erreur ou un défaut dans cette logique de validation pourrait compromettre la qualité des données du graphe entier. De plus, cette méthode contient une logique complexe impliquant plusieurs décisions et dépendances, ce qui la rend particulièrement importante à tester en détail pour assurer la robustesse et la fiabilité du système.

#### Justification des classes mockées'

**BaseGraph**

BaseGraph est la base de données du graphe routier. Créer une vraie instance signifierait initialiser une base de données complète, ce qui est très coûteux en ressources et en temps. De plus, `acceptWay()` n'utilise pas BaseGraph directement - elle ne fait que vérifier la structure et les tags de la route. BaseGraph n'est nécessaire que pour le constructeur d'OSMReader. C'est pourquoi on la mock : pour satisfaire le constructeur sans avoir à créer une vraie base de données.

**OSMParsers**

OSMParsers contient la logique métier pour valider les routes selon les règles OpenStreetMap. C'est une dépendance critique car c'est elle qui décide de l'acceptation finale. Si on ne la mockait pas, on testerait à la fois `acceptWay()` et la logique complexe des parseurs ensemble, ce qui rendrait le test difficile à maintenir et à comprendre. En mockant OSMParsers, on peut isoler la logique d'`acceptWay()` et tester uniquement sa responsabilité : vérifier les critères basiques (nombre de nœuds, présence de tags).

#### Configuration des mocks

- `getTurnCostStorage()` retourne null : Juste pour que le constructeur fonctionne.
- `createRelationFlags()` retourne new IntsRef(2) : Nécessaire pour initialiser.
- `acceptWay()` retourne true : Simule une route acceptée par les parseurs.

#### Valeurs de test

- ID = 1 : Simple identifiant.
- Tag "highway=primary" : Route principale valide.
- Deux nœuds (10, 20) : Minimum pour une route valide.

#### Code 

```java
@Test
public void testAcceptWayWithValidWay() {

    // Mock 1 : BaseGraph
    BaseGraph mockGraph = mock(BaseGraph.class);
    when(mockGraph.getTurnCostStorage()).thenReturn(null);  // Juste pour passer le constructeur

    // Mock 2 : OSMParsers
    OSMParsers mockParsers = mock(OSMParsers.class);
    when(mockParsers.createRelationFlags()).thenReturn(new IntsRef(2));
    when(mockParsers.acceptWay(any())).thenReturn(true);

    // Config
    OSMReaderConfig config = new OSMReaderConfig();

    // Créer OSMReader
    OSMReader reader = new OSMReader(mockGraph, mockParsers, config);

    // Données de test
    ReaderWay testWay = new ReaderWay(1);
    testWay.setTag("highway", "primary");
    testWay.getNodes().add(10);
    testWay.getNodes().add(20);

    boolean result = reader.acceptWay(testWay);

    // Assert
    assertTrue(result);
}
```
#### Résumé

Le test vérifie qu'une route valide (2+ nœuds, tags, acceptée) est bien acceptée. Les mocks servent à éviter les dépendances externes et avoir un test rapide.

### Test : testIsClosedWithMocks()

#### Classe testée

On teste la méthode `isClosed()` de la classe `KVStorage`. C'est la classe qui gère le stockage clé-valeur pour les données des arêtes et des nœuds du graphe.

`KVStorage` a été choisie pour être testée car c'est une classe fondamentale du système de stockage de GraphHopper. Elle gère la persistance des métadonnées associées aux éléments du graphe (noms de rues, attributs, etc.). La classe possède deux dépendances injectables clairement identifiées (`Directory` et `DataAccess`), ce qui la rend idéale pour le testing avec mocks. De plus, la méthode `isClosed()` contient un opérateur logique (`&&`) qui est une cible classique pour les mutations de PIT.

#### Justification des classes mockées

**Directory**

Directory est la factory qui crée les instances de `DataAccess`. Dans le constructeur de `KVStorage`, elle est appelée pour créer deux DataAccess distincts : un pour les clés (`keys`) et un pour les valeurs (`vals`). En mockant Directory, on peut contrôler exactement quels DataAccess sont injectés dans KVStorage, permettant ainsi d'isoler complètement la logique de la classe testée sans dépendre du système de fichiers ou de la mémoire réelle.

**DataAccess**

DataAccess représente l'interface de stockage bas niveau. KVStorage utilise deux instances : `keys` pour stocker les métadonnées des clés et `vals` pour stocker les valeurs. En mockant ces deux instances, on peut :
- Contrôler précisément les valeurs retournées par `isClosed()`
- Tester toutes les combinaisons logiques sans avoir à créer de vrais fichiers
- Vérifier que les deux DataAccess sont bien consultés par la méthode testée

#### Configuration des mocks

- `Directory.create("edgekv_keys", 10 * 1024)` retourne `mockKeys` : Simule la création du DataAccess pour les clés.
- `Directory.create("edgekv_vals")` retourne `mockVals` : Simule la création du DataAccess pour les valeurs.
- `mockKeys.isClosed()` et `mockVals.isClosed()` : Configurés pour retourner différentes combinaisons true/false.

#### Valeurs de test

- 4 combinaisons de `isClosed()` : (true,true), (false,true), (true,false), (false,false)
- Permet de tuer la mutation `&&` → `||`

#### Mutations ciblées

**isClosed() :**
```java
return vals.isClosed() && keys.isClosed();
```
- Mutation `&&` → `||` : Tuée par les tests où un seul est fermé
- Mutation `true` → `false` : Tuée par le test où les deux sont fermés

#### Code

```java
@Test
public void testIsClosedWithMocks() {
    // === MOCK 1: Directory ===
    Directory mockDirectory = mock(Directory.class);
    
    // === MOCK 2: DataAccess (deux instances: keys et vals) ===
    DataAccess mockKeys = mock(DataAccess.class);
    DataAccess mockVals = mock(DataAccess.class);
    
    // Configuration: Directory.create() retourne nos DataAccess mockés
    when(mockDirectory.create("edgekv_keys", 10 * 1024)).thenReturn(mockKeys);
    when(mockDirectory.create("edgekv_vals")).thenReturn(mockVals);
    
    // Création du KVStorage avec le Directory mocké
    KVStorage kvStorage = new KVStorage(mockDirectory, true);
    
    // Vérification que Directory.create a été appelé correctement
    verify(mockDirectory).create("edgekv_keys", 10 * 1024);
    verify(mockDirectory).create("edgekv_vals");
    
    // Test 1: Les deux sont fermés → isClosed() = true
    when(mockVals.isClosed()).thenReturn(true);
    when(mockKeys.isClosed()).thenReturn(true);
    assertTrue(kvStorage.isClosed(), 
        "isClosed() doit retourner true quand keys ET vals sont fermés");
    
    // Test 2: vals ouvert → isClosed() = false (court-circuit)
    when(mockVals.isClosed()).thenReturn(false);
    when(mockKeys.isClosed()).thenReturn(true);
    assertFalse(kvStorage.isClosed(), 
        "isClosed() doit retourner false quand vals est ouvert");
    
    // Test 3: vals fermé, keys ouvert → isClosed() = false
    when(mockVals.isClosed()).thenReturn(true);
    when(mockKeys.isClosed()).thenReturn(false);
    assertFalse(kvStorage.isClosed(), 
        "isClosed() doit retourner false quand keys est ouvert");
    
    // Test 4: Les deux sont ouverts → isClosed() = false
    when(mockVals.isClosed()).thenReturn(false);
    when(mockKeys.isClosed()).thenReturn(false);
    assertFalse(kvStorage.isClosed(), 
        "isClosed() doit retourner false quand les deux sont ouverts");
    
    // Vérification des appels (court-circuit de &&)
    verify(mockVals, times(4)).isClosed();
    verify(mockKeys, times(2)).isClosed();
}
```

#### Résumé

Le test vérifie que `isClosed()` retourne true uniquement quand les deux DataAccess sont fermés. Les mocks de Directory et DataAccess permettent d'isoler complètement la logique de KVStorage et de tester toutes les combinaisons possibles pour tuer la mutation `&&` → `||`.

### Rickroll
Pour apporter une touche d'humour dans le workflow GitHub Actions, nous avons ajouté un mécanisme qui déclenche un **Rickroll** lorsque les tests échouent ou que le score de mutation tombe en dessous du seuil de 70%.

#### 1. Fonctionnement

- Une action GitHub custom a été créée dans `.github/actions/rickroll` qui affiche un message et un lien vers la célèbre vidéo de Rick Astley.
- Cette action est déclenchée uniquement si le job `build` échoue, grâce à `if: failure()` dans le job `rickroll` du workflow.

#### 2. Test du Rickroll

Pour tester le Rickroll, nous avons ajouté un test volontairement échoué dans la classe `SpatialKeyAlgoTest`.

#### 3. Exemple d'un cas où un test échoue
![Rickroll dans les logs](./Screenshot%202025-11-21%20180346.png)

##### Code de test

```java
@Test
public void testFailRickRoll() {
    assertEquals(1+1, 1);
}

