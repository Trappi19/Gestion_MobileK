# Gestion_MobileK

Application Android de gestion de repas réalisée dans le cadre d’un projet d’école.  
Le but est de proposer un outil simple pour organiser des repas, suivre les personnes présentes, les plats, les ingrédients, et garder un historique.

## Contexte du projet

Ce projet a été développé dans une démarche d’apprentissage (niveau débutant), pour tester :
- la création d’une application mobile Android,
- la gestion d’une base de données locale,
- la mise en place de fonctionnalités CRUD complètes,
- la logique métier autour des dates (historique / futur).

## Fonctionnalités principales

- Gestion des **personnes** :
  - ajout, modification, suppression,
  - suivi de la date de dernier passage,
  - gestion des goûts (aime / n’aime pas).

- Gestion des **plats** et **ingrédients** :
  - ajout, modification, suppression,
  - utilisation dans les repas.

- Gestion des **repas futurs** :
  - planification d’un repas avec date, personnes et plats,
  - consultation et modification,
  - migration automatique vers l’historique quand la date est passée.

- Gestion de l’**historique** :
  - affichage des repas passés,
  - recherche,
  - ajout manuel d’un repas dans le passé si nécessaire.

- Navigation orientée productivité :
  - raccourcis d’ajout rapide,
  - menus contextuels,
  - recherche sur plusieurs écrans.

## Stack technique

- **Langage** : Kotlin
- **Plateforme** : Android (SDK min 24)
- **UI** : Android Views (XML), Material Design components
- **Architecture** : Activities + logique métier Kotlin
- **Base de données** : SQLite locale (fichier `bdd.db` dans les assets)
- **Build** : Gradle Kotlin DSL (`build.gradle.kts`)

## Structure simplifiée

- `app/src/main/java/com/example/gestion_mobilek/` : code Kotlin
- `app/src/main/res/layout/` : layouts XML
- `app/src/main/assets/bdd.db` : base SQLite initiale
- `app/src/main/AndroidManifest.xml` : déclaration des activités

## Lancer le projet

1. Ouvrir le projet dans Android Studio.
2. Synchroniser Gradle.
3. Lancer sur émulateur ou appareil Android.

## Générer un APK release

1. Dans Android Studio :  
   `Build` -> `Generate Signed Bundle / APK...`
2. Choisir `APK`.
3. Sélectionner (ou créer) un keystore.
4. Choisir la variante `release`.
5. Générer.

APK attendu (par défaut) :  
`app/build/outputs/apk/release/app-release.apk`

## Pistes d’amélioration

- migration vers fragments / navigation component,
- amélioration de l’architecture (MVVM),
- ajout de tests unitaires et instrumentation plus complets,
- internationalisation des textes (resources `strings.xml`),
- amélioration du design system global.

## Auteur

Projet scolaire réalisé par **Sevan**.
