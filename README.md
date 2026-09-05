# Planning Enfants — V3 familiale synchronisée (Supabase)

Application Android familiale pour **Andrew, Shanayss et Kelvyn**.

## Fonctions intégrées

- Vue **Aujourd'hui** et **Semaine**.
- Planning scolaire préchargé + garderie + basket + gym + piano.
- **Ajout d'événements** ponctuels ou récurrents.
- **Personnes autorisées à récupérer** : nom, lien, téléphone, enfant(s) autorisé(s).
- Attribution de **qui récupère quel enfant, quel jour et à quelle heure**.
- Une personne ne peut être choisie que pour un enfant pour lequel elle a été autorisée.
- Sur le profil enfant : lecture seule du planning et des récupérations ; la liste complète des personnes autorisées n'est pas affichée.
- Le contact de la personne réellement prévue pour la récupération peut être affiché avec la récupération.
- Rappels locaux configurables.
- Espace **Famille** avec code d'invitation.
- Synchronisation via Supabase (Postgres + Auth anonyme + REST) entre appareils.
- Synchronisation de secours en arrière-plan via WorkManager (toutes les 15 minutes).
- Rafraîchissement automatique toutes les 20 secondes pendant que l'app est ouverte.

## Planning préchargé

### Andrew
- Lundi : cours 07:30–17:30 ; basket 18:30–20:00
- Mardi : cours 07:30–17:30 ; basket 18:30–20:00
- Mercredi : cours 08:30–11:30
- Jeudi : cours 07:30–11:30
- Vendredi : cours 07:30–17:30 ; basket 18:30–20:00
- Samedi : piano 08:00–09:00

### Shanayss
- Lundi : cours 08:25–16:00 ; gym 17:00–19:00
- Mardi : cours 07:25–17:00
- Mercredi : cours 07:25–11:30
- Jeudi : cours 07:25–16:00
- Vendredi : cours 07:25–12:30
- Samedi : gym 08:00–10:00

### Kelvyn
- Lundi à vendredi : garderie 06:30–07:50 ; cours 07:50–16:00 ; garderie 16:00–18:00 max
- Mardi et vendredi : basket 17:30–19:00

## Synchronisation Supabase

Le projet Supabase (tables, sécurité RLS, clés) est déjà provisionné et intégré dans le code
(`SupabaseSync.java`). Il ne reste qu'**une seule étape manuelle**, à faire une fois :

1. Ouvrir le projet Supabase concerné.
2. Aller dans **Authentication > Sign In / Providers**.
3. Activer **Anonymous Sign-Ins**.

Ensuite, sur le téléphone parent : **☁ Famille > Créer mon espace famille**. Les autres
téléphones rejoignent avec le code affiché et sélectionnent leur profil.

## Notifications push (Firebase Cloud Messaging)

La synchro Supabase seule se rafraîchit toutes les 20 s (app ouverte) / 15 min (arrière-plan).
Pour un push instantané quand un autre membre modifie le planning, deux étapes manuelles,
irréductibles côté Google et côté secret :

1. **Créer un projet Firebase** (console.firebase.google.com), y ajouter une application
   Android avec le package `com.perl.planningenfants`, télécharger `google-services.json`
   et le placer dans `app/google-services.json` (ou l'envoyer pour intégration).
2. **Générer une clé de compte de service** : Firebase Console > Paramètres du projet >
   Comptes de service > *Générer une nouvelle clé privée* (fichier `.json`).
3. Déposer ce fichier comme secret de la fonction Supabase `notify-family` :
   ```bash
   supabase secrets set FIREBASE_SERVICE_ACCOUNT_JSON="$(cat service-account.json)" \
     --project-ref sgykmprbccygxuxfzasa
   ```

Tant que ce secret n'est pas configuré, la fonction `notify-family` répond simplement
« pas encore configurée » sans erreur : l'app continue de fonctionner normalement
(synchro Supabase seule, sans push).

## Compilation APK automatique avec GitHub Actions

La V3 contient `.github/workflows/build-apk.yml` : il installe Android SDK 35, utilise Java 17 +
Gradle 8.9, ajoute `google-services.json` si le secret GitHub `GOOGLE_SERVICES_JSON_B64` est
présent (sinon build sans push), compile `assembleDebug`, puis publie `app-debug.apk` comme
artefact GitHub Actions à chaque push sur `main`/`master` (ou déclenchement manuel).

Pour créer la valeur du secret à partir de `google-services.json` :
```bash
base64 -w 0 app/google-services.json
```

## Android Studio

- Android Gradle Plugin : 8.7.3
- Gradle minimum : 8.9
- compileSdk / targetSdk : 35
- minSdk : 26
- Java : 17

Ouvrir directement le dossier `PlanningEnfantsAndroidV3` dans Android Studio.
