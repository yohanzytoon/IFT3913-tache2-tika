# Tâche 2 — Tests de `CharsetSupersets`

| Membre | Identifiant GitHub |
|---|---|
| Yohan Zytoon | [@yohanzytoon](https://github.com/yohanzytoon) |
| Zouhair El Yani | [@Zouhair-36](https://github.com/Zouhair-36) |

## Classe choisie et couverture de départ

Nous avons étudié une seule classe, `org.apache.tika.detect.CharsetSupersets`, dans le module `tika-core` du dépôt Tika. Elle comporte déjà le test `CharsetSupersetsTest`, mais elle n’était pas entièrement couverte : la mesure JaCoCo de départ était de 15 lignes sur 19 (79 %) et 4 branches sur 6 (67 %). PIT confirmait que certaines lignes de `decodeAs` n’étaient pas exercées.

Cette classe convient à l’étude parce que son comportement est petit et observable : `supersetOf` traduit certains jeux de caractères vers leurs supersets, tandis que `decodeAs` choisit ce superset ou conserve le jeu détecté. Le test existant vérifie les six associations CJK, les cas sans association et le décodage de caractères d’extension.

## ChatUniTest et modèle local

Le plugin Maven ChatUniTest 2.1.1 est déclaré dans `tika-core/pom.xml`. La génération reste une commande explicite et ne s’exécute pas dans le cycle normal `mvn test`. La configuration limite la cible à la classe ci-dessus, écrit les résultats sous `tika-core/target/chatunitest-generated` et les informations de génération sous `tika-core/target/chatunitest-info`.

Le modèle utilisé était Qwen2.5-Coder 3B avec Ollama. ChatUniTest 2.1.1 ne proposant pas cet identifiant dans son énumération intégrée, nous avons créé un alias Ollama `code-llama` pointant vers les mêmes poids Qwen2.5-Coder 3B. Le nom transmis au plugin est donc `code-llama`, mais les poids réellement utilisés sont Qwen2.5-Coder 3B. Ollama était servi localement par son endpoint compatible OpenAI `http://localhost:11434/v1/chat/completions`.

Pour reproduire la préparation du modèle :

```sh
ollama pull qwen2.5-coder:3b
printf 'FROM qwen2.5-coder:3b\n' > /tmp/Ollama-Modelfile
ollama create code-llama -f /tmp/Ollama-Modelfile
ollama serve
```

Commande de génération exécutée depuis la racine du dépôt :

```sh
mvn -f tika-core/pom.xml io.github.zju-aces-ise:chatunitest-maven-plugin:2.1.1:class \
  -DselectClass=org.apache.tika.detect.CharsetSupersets
```

ChatUniTest a produit ses brouillons et diagnostics dans `tika-core/target/chatunitest-info/tika-parent/tika-core/error-message/`. Le répertoire configuré pour les tests exécutables est `tika-core/target/chatunitest-generated/`; aucune classe générée n’y a été conservée, car les compilations ont échoué. Les brouillons et diagnostics de compilation sont donc dans les fichiers `CharsetSupersets_supersetOf_0_0_Test_CompilationError_*.txt` et `CharsetSupersets_decodeAs_1_0_Test_CompilationError_*.txt` sous `error-message/`.

### Résultat et critique des brouillons

ChatUniTest a essayé `supersetOf` et `decodeAs` avec deux tours de compilation/réparation par méthode. Les quatre compilations ont échoué : les brouillons importaient Mockito et, pour `decodeAs`, les tests paramétrés JUnit, dépendances absentes du module. Le premier brouillon utilisait aussi Mockito pour simuler une map `static final` et tenter de construire une classe à constructeur privé. Le second calculait une partie de sa valeur attendue en appelant par réflexion `supersetOf`, ce qui aurait rendu cet oracle circulaire même après correction des dépendances.

Aucun test brut de ChatUniTest ne compile et ne s’exécute sans intervention. Nous avons fait une correction manuelle du brouillon utile pour `decodeAs` : un fichier de test JUnit simple, sans Mockito ni paramètres, avec des valeurs attendues explicites. Le brouillon `supersetOf` a été rejeté parce qu’il dépendait du mocking et vérifiait mal la map statique. Au total : 4 échecs de compilation automatiques, puis 1 fichier de tests corrigé manuellement. Le résultat illustre une limite du générateur sur un module ne déclarant pas les dépendances qu’il suppose.

Qualitativement, les cas `null` et « aucune association » suggérés pour `decodeAs` sont pertinents, mais le test paramétré proposé n’avait pas un oracle indépendant. Les tests écrits à la main dans `CharsetSupersetsTest` sont plus spécifiques : ils énoncent les noms canoniques attendus et démontrent le décodage d’octets d’extension en caractère `U+2460`. Les tests corrigés ci-dessous gardent l’intention utile du brouillon en l’exprimant avec un oracle direct.

## PIT et résultats de mutation

PIT 1.30.0 et son adaptateur JUnit 5 1.2.3 sont configurés dans `tika-core/pom.xml`. Les rapports HTML sont produits dans `tika-core/target/pit-reports/`. Les commandes exécutées sont :

```sh
mvn -f tika-core/pom.xml \
  -DtargetTests=org.apache.tika.detect.CharsetSupersetsTest \
  org.pitest:pitest-maven:1.30.0:mutationCoverage

mvn -f tika-core/pom.xml \
  '-DtargetTests=org.apache.tika.detect.CharsetSupersets*Test' \
  org.pitest:pitest-maven:1.30.0:mutationCoverage
```

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

Résultat ciblé : 6 tests exécutés, 0 échec, 0 erreur. La suite complète de tika-core a aussi réussi : 752 tests exécutés, 0 échec, 0 erreur, 2 tests ignorés. Checkstyle a rapporté 0 violation. Le rapport PIT a été exécuté deux fois et donne les scores présentés ci-dessus. L’action GitHub doit encore être vérifiée après publication de ces changements; nous ne prétendons pas qu’une exécution distante a déjà réussi.

## Références

- [ChatUniTest](https://github.com/ZJU-ACES-ISE/ChatUniTest)
- [Plugin Maven ChatUniTest](https://github.com/ZJU-ACES-ISE/chatunitest-maven-plugin)
- [Compatibilité OpenAI de l’API Ollama](https://github.com/ollama/ollama/blob/main/docs/api/openai-compatibility.mdx)
- [Guide Maven PIT](https://pitest.org/quickstart/maven/)
- [Adaptateur JUnit 5 de PIT](https://github.com/pitest/pitest-junit5-plugin)
