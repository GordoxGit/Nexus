# Guide de création d'une carte Nexus

Ce guide présente la structure de fichiers attendue par le plugin Nexus pour que les cartes soient détectées et chargées correctement.

## Arborescence des dossiers

Lors du premier démarrage, Nexus crée automatiquement l'arborescence suivante dans le dossier de données du plugin (par défaut `/plugins/Nexus`):

```
Nexus/
├─ config.yml
├─ maps.yml
├─ economy.yml
├─ holograms.yml
├─ lang/
│  └─ messages_fr.yml
├─ maps/
│  └─ <identifiant_de_carte>/
│     └─ map.yml
└─ world_templates/
   └─ <nom_du_template>/
      └─ level.dat
```

- `maps/` contient un sous-dossier par carte. Chaque dossier doit au minimum inclure un fichier `map.yml` décrivant la carte et, selon le type d'asset, le fichier `.schem` attendu.
- `world_templates/` stocke les mondes pré-générés référencés par les cartes de type `WORLD_TEMPLATE`. Chaque template doit ressembler à un monde Minecraft valide (présence de `level.dat`, dossiers `region/`, etc.).

Lorsqu'aucune carte n'est encore configurée, Nexus déploie un exemple dans `maps/example_map/` avec un fichier `map.yml` commenté pour servir de modèle.

## Déclaration d'un asset dans `map.yml`

La section `asset` du fichier `map.yml` décrit la ressource à charger. Elle est obligatoire et doit préciser au minimum:

```yaml
asset:
  type: SCHEMATIC # ou WORLD_TEMPLATE
  file: nom_de_l_asset
```

### Type `SCHEMATIC`

- `file` doit pointer vers un fichier `.schem` situé **dans le dossier de la carte** (`maps/<identifiant>/`).
- Lors du chargement, Nexus vérifie l'existence de ce fichier. S'il est manquant, la carte est ignorée et une erreur explicite est journalisée.

### Type `WORLD_TEMPLATE`

- `file` doit correspondre au nom d'un dossier présent dans `world_templates/`.
- Le dossier doit contenir un monde Minecraft valide, au minimum un fichier `level.dat`.
- Si le dossier ou `level.dat` sont absents, la carte est désactivée et une erreur détaillée est affichée dans la console.

## Conseils de validation

- Utilisez `maps.yml` pour répertorier vos cartes et leurs métadonnées (nom d'affichage, modes, etc.).
- Après chaque modification, utilisez la commande de validation (ou redémarrez le serveur) pour vous assurer qu'aucune erreur n'est signalée.
- Les erreurs de validation sont isolées par carte : un asset manquant n'empêche pas le chargement des autres cartes valides.

En suivant cette structure, vos cartes seront chargées et réinitialisées de manière fiable par le MapService de Nexus.
