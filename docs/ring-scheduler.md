# T-004 — Ring Scheduler Specification

## 1. Objectifs et périmètre

Mettre en place un planificateur circulaire (« ring scheduler ») unique, exécuté sur le thread principal Paper, afin de regrouper et lisser les mises à jour non critiques des arènes. Le système doit :

- Mutualiser l'exécution des tâches HUD, scoreboards, particules, générateurs et orchestrateurs divers à une cadence maîtrisée (2–10 Hz) selon l'état de l'arène.
- Garantir l'absence d'appels Bukkit asynchrones tout en respectant le budget MSPT défini par la documentation Nexus (objectif p99 ≤ 25 ms).
- Exposer des hooks d'instrumentation et de profilage pour les services (HUD, scoreboard, particules, files d'attente, etc.).
- S'intégrer avec l'ArenaService pour appliquer automatiquement les profils LOBBY / STARTING / PLAYING / RESET / END.
- Offrir des mécanismes de dégradation (skip ou réduction d'Hz) lorsque la charge dépasse les seuils configurés.

## 2. Architecture générale

### 2.1 Services et classes principales

| Type | Description | Méthodes / Responsabilités clés |
| --- | --- | --- |
| `RingScheduler` (service singleton) | Gestionnaire central des slots, du profil actif et du backend d'exécution. Maintient la configuration courante et l'état de coalescence par slot. | `start(ArenaContext)`, `stop()`, `setProfile(RingProfile)`, `registerTask(RingTaskRegistration)`, `unregisterTask(RingTaskHandle)`, `pulse(long tick)`, `stats()`.
| `RingProfile` (enum) | Profils cadencés par phase d'arène : `LOBBY`, `STARTING`, `PLAYING`, `RESET`, `END`. Contient les fréquences par slot et les drapeaux d'activation. | `getPeriod(RingSlot)`, `isSlotEnabled(RingSlot)`.
| `RingSlot` (enum) | Catégories logiques : `HUD`, `SCOREBOARD`, `PARTICLES`, `GENERATOR`, `EVENTS`, `QUEUE`, `MISC`. Porte la configuration par défaut et les seuils budgets. | `defaultHz`, `budgetNanos`, `coalescingStrategy`.
| `RingTask` (functional interface) | Tâche synchrone exécutée sur le thread principal : `void run(ArenaContext ctx, long nowNanos)`. Toute interaction Bukkit doit rester thread-safe. | — |
| `RingTaskRegistration` | Valeur immuable décrivant une inscription : slot, tâche, périodes personnalisées (optionnelles), profils autorisés, priority (pour ordonnancement intra-slot). | Validée par `ArenaPhaseGuard` lors de l'enregistrement. |
| `RingTaskHandle` | Poignée retournée pour permettre la désinscription (`close()`). | Permet au service appelant de retirer ses tâches lors d'un arrêt. |
| `RingHook` (listener) | Interface d'observabilité pour instrumentation. | `onProfileEnter`, `onProfileExit`, `beforeTick`, `afterTick`. |
| `RingStats` | Snapshot immuable des métriques (temps d'exécution, Hz effectif, skips). | Exposé via `/nexus admin ring status` et logs debug. |
| `ArenaPhaseGuard` | Valide que la tâche demandée est autorisée pour un profil donné (ex. interdiction cosmétiques pendant RESET). | `validate(RingSlot slot, RingProfile profile)`.

### 2.2 Backend d'exécution

- `RingBackend` (interface) : abstraction du moteur d'ordonnancement (méthodes : `start(Runnable tickTask)`, `stop()`, `isRunning()`).
- `BukkitRingBackend` (Paper/Spigot) : implémentation actuelle utilisant `BukkitScheduler#runTaskTimer(plugin, tickTask, 1L, 1L)` pour garantir une pulsation par tick.
- `FoliaRingBackend` (futur) : mapping vers les Global/Region/Entity Schedulers Folia selon l'arène ciblée.

Le backend sélectionné est injecté dans `RingScheduler` via configuration ou auto-détection au démarrage.

### 2.3 Flux d'exécution

1. À chaque tick serveur, le backend appelle `RingScheduler#pulse(currentTick)`.
2. Le scheduler fait avancer un curseur (index) pour chaque `RingSlot` et calcule si le slot doit être exécuté en fonction de la période (`periodTicks`).
3. Si le slot est éligible :
   - Déclenche `RingHook#beforeTick(slot)`.
   - Exécute séquentiellement les `RingTask` enregistrées pour le slot et autorisées dans le profil actif.
   - Mesure le temps total par slot et met à jour les compteurs (elapsed, skips, hz effectif).
   - Déclenche `RingHook#afterTick(slot, elapsedNanos)`.
4. Si le temps total d'un slot dépasse `budgetNanos` ou si `RingStats#msptRolling` > seuil, le scheduler applique les règles de dégradation (skip prochain cycle ou réduction dynamique d'Hz selon configuration).

### 2.4 Gestion des profils

- `RingScheduler#setProfile(RingProfile)` est appelé par `ArenaService` lors des transitions d'état (T-002). 
- Le changement de profil met immédiatement à jour les périodes par slot, déclenche `onProfileExit` puis `onProfileEnter`, et réinitialise les compteurs de coalescence.
- Les tâches non autorisées dans le profil cible sont automatiquement ignorées jusqu'à retour d'un profil autorisant le slot.

### 2.5 Coalescence & accumulation

Chaque `RingSlot` maintient une file d'événements ou un accumulateur fourni par les services appelants. Par exemple :

- HUD / SCOREBOARD : les services déposent des diffs immuables (Adventure Components). Le scheduler ne pousse les mises à jour qu'à la cadence permise.
- PARTICLES : respect du budget `soft_cap`/`hard_cap`. Si le quota est dépassé, l'exécution du slot est reportée ou tronquée.
- GENERATOR / EVENTS / QUEUE : agrégation d'actions (spawn batch, matchmaking) pour exécution groupée à 2 Hz.

Le scheduler n'alloue pas d'objets sur le chemin chaud ; les structures doivent être préparées en amont (listes réutilisées, caches immuables).

## 3. Profils et fréquences

### 3.1 Fréquences nominales (configurable)

| Slot | Hz par défaut | Période ticks (Paper 20 TPS) |
| --- | --- | --- |
| HUD | 5 Hz | 4 ticks |
| SCOREBOARD | 4 Hz | 5 ticks |
| PARTICLES | 5 Hz | 4 ticks |
| GENERATOR | 2 Hz | 10 ticks |
| EVENTS | 2 Hz | 10 ticks |
| QUEUE | 2 Hz | 10 ticks |
| MISC | 5 Hz | 4 ticks |

### 3.2 Variation par profil

| Profil | HUD | SCOREBOARD | PARTICLES | GENERATOR | EVENTS | QUEUE | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| LOBBY | 5 Hz | 2 Hz | 2–5 Hz (cap léger) | — | 2 Hz | 2 Hz | Générateur désactivé, cosmétiques réduites. |
| STARTING | 10 Hz (compte à rebours) | 4 Hz | 5 Hz | 2 Hz | 2 Hz | 2 Hz | Activation téléport/kit, particules modérées. |
| PLAYING | 5 Hz | 4 Hz | 5–10 Hz (selon budget) | 2 Hz | 2 Hz | 2 Hz | Profil gameplay principal. |
| RESET | Off (cosmétiques coupées) | Off | Off | 2 Hz (rollback) | 2 Hz | Off | Seules les tâches critiques de reset subsistent. |
| END | 2 Hz (annonce) | 2 Hz | 2 Hz | — | 2 Hz | Off | Animation finale, freeze scores. |

Remarques :
- Les colonnes « Off » signifient que `RingProfile#isSlotEnabled` retourne `false` et que le scheduler ignore les tâches correspondantes.
- PARTICLES peut adapter sa fréquence dynamiquement entre 5 et 10 Hz selon le budget runtime (caps per player).

### 3.3 Diagramme d'état Arena ↔ RingProfile

```
LOBBY --(countdown)--> STARTING --(grace over)--> PLAYING
PLAYING --(win condition)--> END --(timeout)--> RESET
RESET --(cleanup done)--> LOBBY
```

Chaque transition déclenche `RingScheduler#setProfile` via `ArenaService#onPhaseChange`.

## 4. API d'intégration

### 4.1 Enregistrement des tâches

```java
RingTaskHandle register(RingTaskRegistration registration);
```

- `RingTaskRegistration` contient : `RingSlot slot`, `RingTask task`, `EnumSet<RingProfile> allowedProfiles`, `int customPeriodTicks` (optionnel), `ArenaPhaseGuard guard`, `String description`, `int priority` (0 par défaut).
- `ArenaPhaseGuard` vérifie au moment de l'enregistrement et lors des changements de profil que la tâche reste autorisée. Les gardes fournis par les services (ex. `CosmeticsGuard`) peuvent refuser `RESET`.
- Les tâches peuvent demander des cadences spécifiques (≥ period par défaut) ; sinon le scheduler utilise la fréquence de profil.

### 4.2 Hooks & instrumentation

Les implémentations de `RingHook` peuvent être enregistrées via `RingScheduler#addHook(RingHook hook)`.

- `onProfileEnter(RingProfile profile)` / `onProfileExit(RingProfile profile)` : instrumentation (logs, comptage, adaptation runtime).
- `beforeTick(RingSlot slot)` : utilisé pour initialiser des budgets (ex. remise à zéro des compteurs particules).
- `afterTick(RingSlot slot, long elapsedNanos)` : push metrics (Micrometer / Prometheus), appliquer caps dynamiques.

### 4.3 Statistiques

`RingStats` expose notamment :

- `currentProfile`
- `hzConfigured(slot)` / `hzEffective(slot)`
- `tickTimeNanos(slot)` (p50/p95/p99)
- `skippedBatches(slot)`
- `msptRolling`

Les statistiques sont rafraîchies toutes les 20 ticks (1 seconde) et accessibles via commande admin.

## 5. Intégrations par service

| Service | Slot | Rôle dans le scheduler |
| --- | --- | --- |
| `HUDService` | `HUD` | Agrège les diffs (barres d'action, boss bars) et publie à la cadence définie. |
| `ScoreboardService` | `SCOREBOARD` | Met à jour les scoreboards côté client, coalesce les lignes modifiées. |
| `ParticleService` | `PARTICLES` | Émet les particules gameplay/cosmétiques en respectant caps et budget runtime. |
| `ResourceGenService` | `GENERATOR` | Tick des générateurs (spawn ressources) à 2 Hz, suspendu hors `PLAYING`/`STARTING`. |
| `EventScheduler` | `EVENTS` | Déclenche événements d'arène (power-ups, scripts) à cadence maîtrisée. |
| `QueueService` | `QUEUE` | Avance le matchmaking interne lorsque l'arène est en attente (`LOBBY`). |
| Services divers (ex. `ReplayService`) | `MISC` | Slots supplémentaires pour tâches ponctuelles, toujours soumis aux budgets. |

Chaque service conserve sa logique métier (gestion des diffs, caches), mais délègue l'exécution synchronisée au `RingScheduler`.

## 6. Configuration & commandes

### 6.1 config.yml

```yaml
ring:
  defaults:
    HUD: 5hz
    SCOREBOARD: 4hz
    PARTICLES: 5hz
    GENERATOR: 2hz
    EVENTS: 2hz
    QUEUE: 2hz
    MISC: 5hz
  profiles:
    RESET:
      enableCosmetics: false
      enableQueue: false
    END:
      hudHz: 2hz
      scoreboardHz: 2hz
  degrade:
    soft:
      mspt: 30
      scale: 0.6
    hard:
      mspt: 45
      pauseCosmetics: true
```

- Les fréquences sont validées à l'initialisation (2 Hz mini, 20 Hz maxi).
- Les profils peuvent surcharger les fréquences par slot (`hudHz`, `particlesHz`, etc.) ou activer/désactiver des slots.
- `degrade.soft.scale` applique un facteur multiplicateur sur les Hz configurés lorsque `msptRolling` dépasse le seuil.
- `degrade.hard.pauseCosmetics` désactive les slots cosmétiques (`HUD`, `SCOREBOARD`, `PARTICLES`) jusqu'à retour sous le seuil.

### 6.2 Commandes & permissions

- `/nexus admin ring status` : affiche profil actif, Hz configuré vs effectif, temps moyen par slot, skips.
- `/nexus admin ring profile <profile>` : force un profil (diagnostic). Réservé aux opérateurs.
- `/nexus admin ring set <slot> <hz>` : ajuste la fréquence d'un slot à chaud (enregistré dans `RingScheduler` mais non persistant, usage debugging).
- `nexus.admin.ring` : permission dédiée (incluse dans `nexus.admin`).

## 7. Journalisation & observabilité

- Logs `INFO` : `ring.<arenaId>.profile=<PROFILE>` lors des transitions.
- Logs `DEBUG` (configurable) : `ring.<arenaId>.<slot>.hz=<x>`, `tickTimeNanos=<y>`, `skippedBatches=<n>`.
- Export métriques (Micrometer) : `ring.slot.elapsed`, `ring.slot.hz_effective`, `ring.slot.skips`, `ring.mspt`.
- À l'arrêt (`RingScheduler#stop`), les tâches programmées sont annulées et un snapshot final est journalisé.

## 8. Performance & sécurité

- Budgets : gameplay + timers ≤ 4 ms/tick, HUD/particules ≤ 4 ms, divers ≤ 3 ms (cumulé). Les dépassements déclenchent la dégradation.
- Respect de la safety tick : aucune tâche ne doit effectuer d'appel Bukkit en asynchrone. Les services restent sur le main thread.
- Particules : caps par joueur et par slot appliqués dans `ParticleService` via `RingHook#beforeTick`.
- Compatibilité Paper/Folia assurée par l'abstraction backend.

## 9. Plan de validation

1. **Profiling visuel** : démarrer une arène et forcer les phases LOBBY → STARTING → PLAYING → RESET → END. Vérifier logs `profile=...` et Hz correspondants.
2. **Cadence scoreboard** : configurer `SCOREBOARD=2 Hz` et observer ~1 mise à jour toutes les 10 ticks, puis passer à 4 Hz via commande runtime.
3. **Coalescence HUD** : pousser 50 diffs HUD en 1 seconde et confirmer que seules 5 pushes effectives (5 Hz) partent vers les joueurs.
4. **RESET sans cosmétiques** : en profil RESET, s'assurer que les slots cosmétiques sont suspendus et que seuls les tasks de rollback s'exécutent.
5. **Dégradation** : simuler MSPT > 30 ms (tâche artificielle) et vérifier que le scheduler réduit les Hz ou skippe des batches (`skippedBatches > 0`).
6. **Arrêt propre** : exécuter `/stop` ou désactiver le plugin et confirmer l'annulation du backend et l'absence de tâches résiduelles.
7. **Compat scheduling** : auditer que toutes les tâches du ring utilisent le backend sync (aucune invocation async sur API Bukkit).

## 10. Dépendances & considérations futures

- Prévoir un point d'extension pour Folia (`FoliaRingBackend`) sans modifier l'API publique des services.
- S'assurer que la configuration est rechargée par `/nexus reload` (T-001) et que les fréquences sont mises à jour sans redémarrage.
- Étendre ultérieurement `RingStats` pour exporter des percentiles MSPT par arène.

