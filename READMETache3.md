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

### Test : xyz()

#### Classe testée 

SQUELETTE A COMPLETER

### Rickroll


