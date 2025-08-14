# Changelog

## 1.5.2 - Protection List Menu
- **Feat:** Implémentation de `/hb protect list` avec menu de gestion des zones.
- **Fix:** Résolution du conflit entre `/hb list` et `/hb protect list`.

## 1.5.1 - Critical Hotfix
- **Fix:** Players are no longer permanently frozen after a game is manually stopped with `/hb stop`. The game state is now correctly reset.

## 1.5.0 - Lobby Adventure & Zone Manager
- **Feat:** Joueurs en GameMode.ADVENTURE au lobby pour une protection totale.
- **Feat:** Commande `/hb protect list` avec GUI pour gérer les zones protégées.
- **Fix:** Réinitialisation correcte de l'état du joueur après une mort au lobby.
- **Fix:** Tablist stable sans clignotement.

## 1.4.1 - Protection UX fixes
- **Fix:** confirmation for "Position 2" is only sent once.
- **Fix:** block hit animation is cancelled in protected zones.

## 1.4.0 - Protection de zones
- **Feat:** Outil en jeu pour définir des zones protégées avec `/hb protect` et `/hb confirm`.
- **Feat:** Chargement et sauvegarde persistants des régions protégées.
- **Feat:** Empêche les joueurs sans permission de casser ou placer des blocs dans ces zones.

## 1.3.2 - File d'attente sans TP
- **Fix:** l'item « Quitter la file » ne peut plus être posé et fonctionne en l'air.
- **UX:** quitter une file d'attente n'entraîne plus de téléportation au lobby.

## 1.3.1 - Queue & stability tweaks
- **Feat:** Système de file repensé avec barrière de sortie.
- **Fix:** Annulation du compte à rebours et arrêt de partie si un joueur quitte.
- **UX:** Gel initial assoupli permettant de regarder autour de soi.

## 1.3.0 - Gameplay & UX Finalization
- **Feat:** Replaced lobby navigation compass with a clock.
- **Feat:** Implemented instant respawn in arenas, removing the death screen.
- **Fix:** Corrected item drop logic; dropping is now only restricted during active games.
- **Fix:** Removed redundant navigation item from lobby inventory.
- **Chore:** General code cleanup and UX improvements for Heneria.

## 1.2.11
- Fix command registration (/hb)
- Add recovery compass on join
- Improve listener registration & logs

## 1.2.8
- Boussole air/no-TP, GUI verrouillé
- Join anti-spam/atomique
- Admin build

## 1.2.7
- Renommage monde → world_hika
- Boussole partout
- /hb admin

## 1.2.6
- Boussole lobby sans TP
- GUI catégories cliquable

## 1.2.5
- UI Lobby (scoreboard/tablist)
- Boussole au join

## 1.2.4
- Boussole catégories
- /hb create <nom> <teamSize>
- /hb setlobby

## 1.2.3
- Scoreboard adaptatif (zéro espace vide)

## 1.2.2 / 1.2.1 / 1.2.0
- Rework esthétique HUD/Tablist
- Services UI/Feedback/Theme

## 1.1.x
- SetBed, SetBroke, pont cassable
- Base gameplay
