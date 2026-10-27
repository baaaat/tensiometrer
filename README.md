# iGaging USB Diagnostic (Android)

Application Android native de diagnostic du tensiomètre Wheel Fanatyk / iGaging USB. Elle affiche VID/PID, interfaces/endpoints, événements clavier, et essaie de capturer les paquets USB HID bruts. **Elle ne modifie pas les valeurs et ne reconstruit pas encore les décimales.**

## Générer l'APK sur GitHub (sans Android Studio)

1. Aller dans l'onglet **Actions** du dépôt.
2. Sélectionner **Build Android APK** puis **Run workflow** (ou ouvrir l'exécution lancée automatiquement après un push sur `main`).
3. Après un build réussi, ouvrir l'exécution et récupérer **igaging-usb-diagnostic-apk** dans **Artifacts**.
4. Décompresser le ZIP téléchargé et installer `app-debug.apk` sur la tablette. Android peut demander l'autorisation d'installation depuis cette source.

Le build nécessite que GitHub Actions soit activé sur le dépôt. Le premier build doit télécharger Gradle et les composants Android.

## Tester

Brancher l'interface iGaging en USB OTG, appuyer sur « Détecter USB » puis « Autoriser et capturer HID », effectuer une mesure connue (p. ex. 1.63) et photographier le journal USB HEX/KEY. Certains périphériques HID clavier ne peuvent pas être réclamés par l'application sur tous les appareils Android : l'échec est une information de diagnostic.

Projet expérimental non compilé ni testé sur un appareil réel à ce stade.
