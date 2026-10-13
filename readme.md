# Tâche 2 — Tests de `CharsetSupersets`

| Membre | Identifiant GitHub |
|---|---|
| Yohan Zytoon | [@yohanzytoon](https://github.com/yohanzytoon) |
| Zouhair El Yani | [@Zouhair-36](https://github.com/Zouhair-36) |

## Classe choisie et couverture de départ

Nous avons étudié une seule classe, `org.apache.tika.detect.CharsetSupersets`, dans le module `tika-core` du dépôt Tika. Elle comporte déjà le test `CharsetSupersetsTest`, mais elle n’était pas entièrement couverte : la mesure JaCoCo de départ était de 15 lignes sur 19 (79 %) et 4 branches sur 6 (67 %). PIT confirmait que certaines lignes de `decodeAs` n’étaient pas exercées.

Cette classe convient à l’étude parce que son comportement est petit et observable : `supersetOf` traduit certains jeux de caractères vers leurs supersets, tandis que `decodeAs` choisit ce superset ou conserve le jeu détecté. Le test existant vérifie les six associations CJK, les cas sans association et le décodage de caractères d’extension.

Les méthodes choisies ont bien des mutants que la suite originale ne tue pas. Avec `CharsetSupersetsTest` seul, PIT génère 5 mutants et n’en tue que 3 : les 2 mutants de `decodeAs` ne sont détectés par aucun test. PIT les classe `NO_COVERAGE` plutôt que `SURVIVED`, car aucun test original n’exécute `decodeAs` ; c’est la forme la plus forte de mutant non détecté, puisque la méthode n’est jamais appelée. Une seule classe suffit pour l’analyse : la consigne autorise une à trois classes, et une classe de 5 mutants permet de documenter chaque mutant individuellement, de relier chacun au test qui le tue et d’atteindre un score de 100 % vérifiable, plutôt que de rapporter un score global sur des dizaines de mutants non expliqués.

## ChatUniTest et modèle local

Le plugin Maven ChatUniTest 2.1.1 est déclaré dans `tika-core/pom.xml`. La génération reste une commande explicite et ne s’exécute pas dans le cycle normal `mvn test`. La configuration limite la cible à la classe ci-dessus, écrit les résultats sous `tika-core/target/chatunitest-generated` et les informations de génération sous `tika-core/target/chatunitest-info`. Comme `target/` n’est pas versionné, les brouillons produits ont été copiés dans [`docs-tache2/chatunitest/`](docs-tache2/chatunitest/).

Le modèle utilisé est Qwen2.5-Coder 7B (`qwen2.5-coder:7b`) servi localement par Ollama. ChatUniTest 2.1.1 ne proposant pas cet identifiant dans son énumération intégrée, nous avons créé un alias Ollama `code-llama` pointant vers les mêmes poids. Le nom transmis au plugin est donc `code-llama`, mais les poids réellement utilisés sont ceux de `qwen2.5-coder:7b`. Le plugin appelle l’endpoint compatible OpenAI d’Ollama `http://localhost:11434/v1/chat/completions`.

Pour reproduire la préparation du modèle :

```sh
ollama pull qwen2.5-coder:7b
printf 'FROM qwen2.5-coder:7b\n' > Modelfile
ollama create code-llama -f Modelfile
ollama serve
```

ChatUniTest 2.1.1 doit être exécuté avec un JDK 21 : sous JDK 25, le plugin échoue avant la génération (« Invalid Java version format: 25 »).

Commande de génération exécutée depuis la racine du dépôt :

```sh
mvn -f tika-core/pom.xml io.github.zju-aces-ise:chatunitest-maven-plugin:2.1.1:class \
  -DselectClass=org.apache.tika.detect.CharsetSupersets
```

ChatUniTest a produit ses brouillons et diagnostics dans `tika-core/target/chatunitest-info/tika-parent/tika-core/error-message/`. Le répertoire configuré pour les tests exécutables, `tika-core/target/chatunitest-generated/`, est resté vide : ChatUniTest n’y écrit que les tests qui compilent. Les quatre brouillons, chacun suivi de ses erreurs de compilation, sont versionnés ici :

| Fichier | Méthode | Tour |
|---|---|---|
| [`CharsetSupersets_supersetOf_0_0_Test_CompilationError_0.txt`](docs-tache2/chatunitest/CharsetSupersets_supersetOf_0_0_Test_CompilationError_0.txt) | `supersetOf` | génération |
| [`CharsetSupersets_supersetOf_0_0_Test_CompilationError_1.txt`](docs-tache2/chatunitest/CharsetSupersets_supersetOf_0_0_Test_CompilationError_1.txt) | `supersetOf` | réparation |
| [`CharsetSupersets_decodeAs_1_0_Test_CompilationError_0.txt`](docs-tache2/chatunitest/CharsetSupersets_decodeAs_1_0_Test_CompilationError_0.txt) | `decodeAs` | génération |
| [`CharsetSupersets_decodeAs_1_0_Test_CompilationError_1.txt`](docs-tache2/chatunitest/CharsetSupersets_decodeAs_1_0_Test_CompilationError_1.txt) | `decodeAs` | réparation |

Le journal complet de l’exécution est dans [`docs-tache2/chatunitest/chatunitest-run.log`](docs-tache2/chatunitest/chatunitest-run.log).

### Résultat et critique des brouillons

ChatUniTest a traité `supersetOf` et `decodeAs` avec un tour de génération et un tour de réparation par méthode (`maxRounds = 2`). **Les quatre compilations ont échoué ; aucun test généré ne compile ni ne s’exécute sans intervention manuelle.** La cause immédiate est commune : chaque brouillon importe Mockito (`org.mockito.*`, `MockitoExtension`), absent des dépendances de test de `tika-core`. Le tour de réparation n’a pas corrigé cette cause : pour `supersetOf`, le second brouillon est identique au premier à l’ordre des imports près ; pour `decodeAs`, il retire `@Mock` mais garde les imports Mockito et appelle une classe `ReflectionUtils` qui n’existe pas.

Au-delà de la compilation, les brouillons auraient échoué ou testé un faux comportement :

| Brouillon | Problème | Conséquence |
|---|---|---|
| `decodeAs`, les deux tours | L’oracle affirme que `UTF-8` a pour superset `ISO-8859-1`. Ce n’est vrai ni dans `SUPERSET_MAP` (qui ne contient que six charsets CJK) ni en général. | Oracle faux : le test échouerait, et il vérifierait une règle inventée. |
| `decodeAs`, tour 0 | `@Mock` et `@InjectMocks` sur des champs `static`, pour simuler `SUPERSET_MAP`, une map `public static final` non modifiable. | Mockito n’injecte pas dans un champ `static final` ; la map réelle resterait utilisée. |
| `decodeAs`, tour 1 | `new CharsetSupersets()` alors que le constructeur est privé ; `when(...)` appliqué à une vraie `HashMap`. | Erreurs de compilation et d’exécution supplémentaires. |
| `supersetOf`, les deux tours | `when(Charset.forName(...))` tente de simuler une méthode statique du JDK ; réflexion pour écraser `SUPERSET_MAP` ; `Field` non importé. | Impossible avec Mockito standard ; écrire un champ `static final` par réflexion échoue sur un JDK moderne. |
| `supersetOf`, tests 2 et 3 | `Charset.forName("unknown")` et `Charset.forName("exception")` sont appelés pour construire l’entrée. | `UnsupportedCharsetException` levée avant même l’appel à la méthode testée. |

Le seul test réellement correct est `testSupersetOfWithNullCharset` (`supersetOf(null)` doit rendre `null`), mais ce cas est déjà vérifié par `CharsetSupersetsTest`. Dans les brouillons `decodeAs`, l’intention de `testDecodeAs_withoutSuperset` est juste : un charset absent de la map (`ASCII`) doit être rendu tel quel.

Qualitativement, les oracles produits par le modèle sont soit triviaux (`assertNull` sur une entrée `null`), soit faux, car le modèle n’a pas lu le contenu de `SUPERSET_MAP` et a inventé une association `UTF-8 → ISO-8859-1`. Les tests écrits à la main dans `CharsetSupersetsTest` sont plus spécifiques : ils énoncent les six associations CJK réelles avec leurs noms canoniques et démontrent le décodage d’octets d’extension en caractère `U+2460`. Le modèle a surtout tenté de contourner la classe (mocks, réflexion) au lieu d’utiliser ses vraies données, ce qui est inutile pour une classe statique sans dépendance externe.

Nous avons donc corrigé manuellement le brouillon `decodeAs` : Mockito et réflexion retirés, cas « sans superset » et « entrée `null` » conservés, et association inventée remplacée par une entrée réelle de la map (`EUC-KR → x-windows-949`). Le brouillon `supersetOf` a été rejeté : son seul cas valide existait déjà. Au total : 4 échecs de compilation automatiques, puis 1 fichier de tests corrigé à la main (3 tests).

## PIT et résultats de mutation

PIT 1.30.0 et son adaptateur JUnit 5 1.2.3 sont configurés dans `tika-core/pom.xml`. La classe ciblée est fixée dans `<targetClasses>`; la suite de tests est lue dans la propriété Maven `pit.targetTests` (par défaut `CharsetSupersets*Test`), ce qui permet de la surcharger en ligne de commande. Les commandes exécutées depuis la racine du dépôt sont :

```sh
mvn -B install -pl tika-core -am -DskipTests

# Tests originaux seulement
mvn org.pitest:pitest-maven:1.30.0:mutationCoverage -pl tika-core \
  -Dpit.targetTests=org.apache.tika.detect.CharsetSupersetsTest \
  -DreportsDirectory=target/pit-reports-originaux

# Tests originaux + tests ajoutés
mvn org.pitest:pitest-maven:1.30.0:mutationCoverage -pl tika-core \
  -DreportsDirectory=target/pit-reports-etendus
```

Les rapports HTML sont versionnés hors de `target/` : [rapport PIT, tests originaux](docs-tache2/pit/originaux/org.apache.tika.detect/CharsetSupersets.java.html) et [rapport PIT, suite étendue](docs-tache2/pit/etendus/org.apache.tika.detect/CharsetSupersets.java.html) (télécharger le dossier et ouvrir `index.html` pour la vue colorée). Le workflow GitHub Actions régénère les deux rapports à chaque push et les publie comme artefact `rapports-pitest`.

| Suite utilisée | Mutants tués | Score de mutation | Couverture de lignes PIT | Mutants sans couverture |
|---|---:|---:|---:|---:|
| Tests originaux `CharsetSupersetsTest` | 3/5 | 60 % | 15/21 (71 %) | 2 |
| Tests originaux + tests corrigés `decodeAs` | 5/5 | 100 % | 17/21 (81 %) | 0 |

Avec les tests originaux, les trois mutants tués modifiaient les conditions et le retour de `supersetOf`. Les deux mutants non couverts se trouvaient dans `decodeAs` : remplacer son retour par `null`, et inverser la condition qui choisit le superset. Les tests originaux n’appelaient pas cette méthode.

Avec la suite étendue, les cinq mutants sont tués. `decodesWithSupersetWhenOverrideExists` tue les deux mutants `decodeAs` : pour `EUC-KR`, l’oracle exige `x-windows-949` explicitement. Les trois mutants de `supersetOf` restent tués par la suite originale, qui vérifie des associations connues, l’absence de superset et le décodage des caractères d’extension. Aucun mutant ne reste vivant ni sans couverture dans cette analyse.

La couverture de lignes rapportée par PIT n’est pas égale à 100 % (17/21) : des lignes non mutées, notamment le constructeur privé et le chemin de repli d’erreur de résolution d’un charset, restent hors de ces mutants. L’objectif de l’analyse est atteint ici au niveau des mutants générés par PIT, pas une affirmation de couverture absolue de chaque ligne imaginable.

## Tests ajoutés et oracles

Le fichier `tika-core/src/test/java/org/apache/tika/detect/CharsetSupersetsDecodeAsTest.java` contient les trois tests issus de la correction du brouillon :

| Test | Intention et données choisies | Oracle |
|---|---|---|
| `decodesWithSupersetWhenOverrideExists` | Vérifier le chemin où une surcharge existe. `EUC-KR` est une entrée réelle de la map et couvre une association importante du cas d’étude. | Le résultat doit être `Charset.forName("x-windows-949")`, nom explicite du superset défini par la classe. |
| `preservesDetectedCharsetWhenNoOverrideExists` | Vérifier le chemin normal avec `StandardCharsets.UTF_8`, charset disponible partout et absent de la map. | `assertSame` exige que la méthode rende exactement l’objet fourni lorsque le superset vaut `null`. |
| `preservesNullWhenDetectionReturnedNull` | Vérifier la propagation de l’absence de charset détecté. | Le contrat de `decodeAs` indique `null` seulement si l’entrée est `null`; `assertNull` formalise ce résultat. |

Ces trois tests s’exécutent avec les dépendances JUnit 5 déjà présentes et ne requièrent aucun ajout à la configuration des dépendances de test.

## Vérification

Vérification locale exécutée sur le module :

```sh
mvn -f tika-core/pom.xml \
  -Dtest=CharsetSupersetsTest,CharsetSupersetsDecodeAsTest test
```

Résultat ciblé : 6 tests exécutés, 0 échec, 0 erreur. La suite complète de `tika-core` a aussi réussi localement : 752 tests exécutés, 0 échec, 0 erreur, 2 tests ignorés. Checkstyle a rapporté 0 violation. Le rapport PIT a été exécuté avec les deux configurations et donne les scores présentés ci-dessus.

Le workflow dédié [`.github/workflows/tache2.yml`](.github/workflows/tache2.yml) compile `tika-core`, exécute `CharsetSupersetsTest` et `CharsetSupersetsDecodeAsTest` (6 tests, 0 échec), puis lance PIT avec les tests originaux et avec la suite étendue. Il réussit sur GitHub Actions : [exécutions du workflow tache2](https://github.com/yohanzytoon/IFT3913-tache2-tika/actions/workflows/tache2.yml).

Les workflows Tika existants ont aussi été observés. Sur ces builds, les six tests ciblés ont réussi sur les builds Linux JDK 17, 21 et 25. Les builds Windows standard et multi-locale ont réussi après l’activation de `core.longpaths`. Les builds Linux complets ont ensuite échoué dans `tika-pipes-s3-integration-tests`, lorsque Testcontainers n’a pas pu télécharger l’image MinIO (« unauthorized: access to the requested resource is not authorized »). Cette erreur concerne une intégration S3 sans lien avec `CharsetSupersets`; elle ne constitue pas un succès du build complet. Les tests ajoutés sont bien exécutés et réussis dans ces workflows avant cette erreur ultérieure.

- [Build Linux JDK 17](https://github.com/yohanzytoon/IFT3913-tache2-tika/actions/runs/38431765884)
- [Build Linux JDK 21](https://github.com/yohanzytoon/IFT3913-tache2-tika/actions/runs/38431764951)
- [Build Linux JDK 25](https://github.com/yohanzytoon/IFT3913-tache2-tika/actions/runs/38431765108)
- [Build Windows JDK 17](https://github.com/yohanzytoon/IFT3913-tache2-tika/actions/runs/38431765220)
- [Build Windows multi-locale](https://github.com/yohanzytoon/IFT3913-tache2-tika/actions/runs/38431764893)

## Correspondance avec les critères

| Critère | Éléments documentés |
|---|---|
| Choix et couverture de la classe | Couverture JaCoCo de départ, tests préexistants, 2 mutants sur 5 non détectés par la suite originale et justification du choix d’une seule classe. |
| ChatUniTest et génération | Version du plugin dans Maven, classe ciblée, commande exacte, modèle Ollama (`qwen2.5-coder:7b`) et brouillons versionnés dans `docs-tache2/chatunitest/`. |
| Critique des tests générés | Quatre compilations échouées, Mockito absent, réparation inefficace, oracle inventé (`UTF-8 → ISO-8859-1`), comparaison avec les oracles écrits à la main et cas réutilisés après correction. |
| Mutation | Commandes PIT reproductibles (`-Dpit.targetTests`), scores avec tests originaux puis suite étendue, rapports HTML versionnés dans `docs-tache2/pit/`. |
| Mutants détectés | Les cinq mutants et le comportement exercé pour les tuer sont décrits; aucun mutant vivant ou sans couverture ne reste dans cette exécution. |
| Tests ajoutés à la main | Nom, intention, motivation des données et oracle explicite pour chacun des trois tests. |
| Exécution | Workflow dédié `tache2.yml` vert (tests ciblés + PIT), résultats locaux et cause externe des échecs des builds Tika complets. |
| Emplacement du rapport | Ce fichier est le rapport unique à la racine du dépôt. |

## Références

- [ChatUniTest](https://github.com/ZJU-ACES-ISE/ChatUniTest)
- [Plugin Maven ChatUniTest](https://github.com/ZJU-ACES-ISE/chatunitest-maven-plugin)
- [Compatibilité OpenAI de l’API Ollama](https://github.com/ollama/ollama/blob/main/docs/api/openai-compatibility.mdx)
- [Guide Maven PIT](https://pitest.org/quickstart/maven/)
- [Adaptateur JUnit 5 de PIT](https://github.com/pitest/pitest-junit5-plugin)


## Utilisation de l’IA générative

ChatUniTest 2.1.1 a servi à générer des brouillons de tests pour `CharsetSupersets` avec le modèle local Qwen2.5-Coder 7B exécuté par Ollama. Le modèle a reçu la classe cible et a produit des propositions pour `supersetOf` et `decodeAs`; ses sorties et erreurs de compilation ont été examinées manuellement. Aucun brouillon brut n’a été intégré tel quel : les quatre tentatives de compilation ont échoué, et les tests retenus ont été corrigés pour retirer les dépendances absentes et remplacer l’association inventée par des résultats attendus explicites.

Un assistant de programmation basé sur un modèle de langage a également aidé à organiser et relire cette documentation ainsi qu’à interpréter les résultats CI. Les membres de l’équipe ont vérifié les commandes, les rapports de tests, les mutants PIT et les journaux GitHub Actions avant de rapporter les résultats. L’IA a servi d’assistance; les mesures et les conclusions présentées ici sont rattachées aux exécutions décrites dans ce rapport.
