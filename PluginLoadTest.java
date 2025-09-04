import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.List;

public class PluginLoadTest {
    public static void main(String[] args) {
        try {
            String jarPath = args.length > 0 ? args[0] : "target/Nexus-0.0.0-SNAPSHOT.jar";
            System.out.println("🔍 Test du JAR: " + jarPath);
            
            // Test 1: Vérifier et charger les dépendances non-Bukkit
            testLoadableDependencies();
            
            // Test 2: Vérifier la présence des dépendances Bukkit (sans chargement)
            testBukkitDependenciesPresence(jarPath);
            
            // Test 3: Vérifier l'intégrité générale du JAR
            testJarIntegrity(jarPath);
            
            // Test 4: Vérifier les métadonnées du plugin
            testPluginMetadata();
            
            // Test 5: Vérifier les configurations
            testConfigurations();
            
            System.out.println("🎉 Tous les tests de smoke réussis !");
            
        } catch (Exception e) {
            System.err.println("❌ Erreur lors du smoke test: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void testLoadableDependencies() throws Exception {
        System.out.println("📦 Test des dépendances chargeables (non-Bukkit)...");
        
        // Test HikariCP relocalisé (safe - pas de dépendances Bukkit)
        try {
            Class<?> hikariClass = Class.forName("fr.heneria.nexus.libs.hikari.HikariDataSource");
            Constructor<?> hikariConstructor = hikariClass.getConstructor();
            System.out.println("  ✅ HikariCP relocalisé: " + hikariClass.getName());
            
            // Test construction d'une instance (validation plus poussée)
            Class<?> configClass = Class.forName("fr.heneria.nexus.libs.hikari.HikariConfig");
            Object config = configClass.getDeclaredConstructor().newInstance();
            System.out.println("  ✅ HikariConfig instanciable: " + config.getClass().getName());
            
        } catch (Exception e) {
            throw new RuntimeException("Échec test HikariCP: " + e.getMessage(), e);
        }
        
        // Test Flyway relocalisé (safe - pas de dépendances Bukkit)
        try {
            Class<?> flywayClass = Class.forName("fr.heneria.nexus.libs.flywaydb.core.Flyway");
            Method[] flywayMethods = flywayClass.getDeclaredMethods();
            System.out.println("  ✅ Flyway relocalisé: " + flywayClass.getName() + " (" + flywayMethods.length + " méthodes)");
            
            // Vérifier quelques méthodes clés
            boolean hasConfigureMethod = false;
            boolean hasMigrateMethod = false;
            for (Method method : flywayMethods) {
                if (method.getName().equals("configure")) hasConfigureMethod = true;
                if (method.getName().equals("migrate")) hasMigrateMethod = true;
            }
            if (!hasConfigureMethod || !hasMigrateMethod) {
                throw new RuntimeException("Méthodes Flyway essentielles manquantes");
            }
            System.out.println("  ✅ Méthodes Flyway essentielles présentes");
            
        } catch (Exception e) {
            throw new RuntimeException("Échec test Flyway: " + e.getMessage(), e);
        }
        
        // Test MariaDB (safe - pas de dépendances Bukkit)
        try {
            Class<?> mariadbClass = Class.forName("org.mariadb.jdbc.Driver");
            System.out.println("  ✅ MariaDB JDBC: " + mariadbClass.getName());
            
            // Test instanciation du driver
            Object driver = mariadbClass.getDeclaredConstructor().newInstance();
            System.out.println("  ✅ Driver MariaDB instanciable");
            
        } catch (Exception e) {
            throw new RuntimeException("Échec test MariaDB: " + e.getMessage(), e);
        }
    }
    
    private static void testBukkitDependenciesPresence(String jarPath) throws Exception {
        System.out.println("🎮 Test de présence des dépendances Bukkit (sans chargement)...");
        
        try (JarFile jar = new JarFile(jarPath)) {
            List<String> triumphGuiClasses = new ArrayList<>();
            List<String> nexusClasses = new ArrayList<>();
            
            // Scanner le JAR pour les classes Triumph GUI et Nexus
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                
                if (name.startsWith("fr/heneria/nexus/libs/gui/") && name.endsWith(".class")) {
                    triumphGuiClasses.add(name);
                } else if (name.startsWith("fr/heneria/nexus/") && !name.startsWith("fr/heneria/nexus/libs/") && name.endsWith(".class")) {
                    nexusClasses.add(name);
                }
            }
            
            // Vérifier Triumph GUI
            if (triumphGuiClasses.isEmpty()) {
                throw new RuntimeException("Aucune classe Triumph GUI relocalisée trouvée");
            }
            System.out.println("  ✅ Triumph GUI relocalisé présent: " + triumphGuiClasses.size() + " classes");
            
            // Afficher quelques exemples
            System.out.println("    Exemples de classes GUI:");
            for (int i = 0; i < Math.min(3, triumphGuiClasses.size()); i++) {
                System.out.println("      - " + triumphGuiClasses.get(i));
            }
            
            // Vérifier les classes principales de Triumph GUI
            boolean hasMainGuiClass = triumphGuiClasses.stream()
                .anyMatch(name -> name.contains("Gui.class") || name.contains("BaseGui.class"));
            if (hasMainGuiClass) {
                System.out.println("  ✅ Classes GUI principales détectées");
            } else {
                System.out.println("  ⚠️  Aucune classe GUI principale évidente, mais " + triumphGuiClasses.size() + " classes GUI présentes");
            }
            
            // Vérifier classes Nexus
            if (nexusClasses.isEmpty()) {
                throw new RuntimeException("Aucune classe Nexus trouvée");
            }
            System.out.println("  ✅ Classes Nexus présentes: " + nexusClasses.size() + " classes");
            
            // Vérifier la classe principale
            boolean hasMainClass = nexusClasses.stream()
                .anyMatch(name -> name.equals("fr/heneria/nexus/Nexus.class"));
            if (!hasMainClass) {
                throw new RuntimeException("Classe principale Nexus.class manquante");
            }
            System.out.println("  ✅ Classe principale Nexus.class présente");
        }
    }
    
    private static void testJarIntegrity(String jarPath) throws Exception {
        System.out.println("🔍 Test d'intégrité du JAR...");
        
        try (JarFile jar = new JarFile(jarPath)) {
            int totalEntries = 0;
            int classFiles = 0;
            int relocatedClasses = 0;
            int nexusClasses = 0;
            int resourceFiles = 0;
            
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                totalEntries++;
                
                if (name.endsWith(".class")) {
                    classFiles++;
                    if (name.startsWith("fr/heneria/nexus/libs/")) {
                        relocatedClasses++;
                    } else if (name.startsWith("fr/heneria/nexus/")) {
                        nexusClasses++;
                    }
                } else if (!name.endsWith("/")) {
                    resourceFiles++;
                }
            }
            
            System.out.println("  📊 Statistiques détaillées du JAR:");
            System.out.println("    Total entrées: " + totalEntries);
            System.out.println("    Fichiers .class: " + classFiles);
            System.out.println("    Classes Nexus: " + nexusClasses);
            System.out.println("    Classes relocalisées: " + relocatedClasses);
            System.out.println("    Fichiers ressources: " + resourceFiles);
            
            // Vérifications de sanité renforcées
            if (totalEntries < 100) {
                throw new RuntimeException("JAR trop petit: " + totalEntries + " entrées (minimum 100 attendu)");
            }
            if (classFiles < 50) {
                throw new RuntimeException("Pas assez de classes: " + classFiles + " (minimum 50 attendu)");
            }
            if (nexusClasses < 5) {
                throw new RuntimeException("Pas assez de classes Nexus: " + nexusClasses + " (minimum 5 attendu)");
            }
            if (relocatedClasses < 10) {
                throw new RuntimeException("Pas assez de classes relocalisées: " + relocatedClasses + " (minimum 10 attendu)");
            }
            if (resourceFiles < 5) {
                throw new RuntimeException("Pas assez de ressources: " + resourceFiles + " (minimum 5 attendu)");
            }
            
            double relocatedRatio = (double) relocatedClasses / classFiles * 100;
            System.out.println("    Ratio relocalisation: " + String.format("%.1f", relocatedRatio) + "%");
            
            if (relocatedRatio < 10) {
                throw new RuntimeException("Ratio de relocalisation trop faible: " + String.format("%.1f", relocatedRatio) + "% (minimum 10% attendu)");
            }
            
            System.out.println("  ✅ Intégrité du JAR validée avec succès");
        }
    }
    
    private static void testPluginMetadata() throws Exception {
        System.out.println("📄 Test des métadonnées du plugin...");
        
        // Vérifier plugin.yml
        java.io.InputStream pluginYml = PluginLoadTest.class.getClassLoader()
            .getResourceAsStream("plugin.yml");
        
        if (pluginYml == null) {
            throw new RuntimeException("plugin.yml introuvable dans le JAR");
        }
        
        String content = new String(pluginYml.readAllBytes());
        pluginYml.close();
        
        String[] requiredFields = {
            "name: Nexus",
            "main: fr.heneria.nexus.Nexus",
            "api-version: '1.21'",
            "author: Projet Heneria",
            "commands:"
        };
        
        for (String field : requiredFields) {
            if (!content.contains(field)) {
                throw new RuntimeException("plugin.yml invalide: champ manquant '" + field + "'");
            }
        }
        
        System.out.println("  ✅ plugin.yml valide avec tous les champs requis");
        
        if (!content.contains("version: ")) {
            throw new RuntimeException("plugin.yml invalide: version manquante");
        }
        
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith("version: ")) {
                String version = line.substring(line.indexOf("version: ") + 9).trim();
                System.out.println("  ✅ Version du plugin: " + version);
                break;
            }
        }
    }
    
    private static void testConfigurations() throws Exception {
        System.out.println("⚙️ Test des fichiers de configuration...");
        
        // Vérifier config.yml
        java.io.InputStream configYml = PluginLoadTest.class.getClassLoader()
            .getResourceAsStream("config.yml");
        
        if (configYml == null) {
            throw new RuntimeException("config.yml introuvable dans le JAR");
        }
        
        String configContent = new String(configYml.readAllBytes());
        configYml.close();
        
        String[] requiredSections = {
            "database:",
            "game:",
            "ranking:",
            "debug:"
        };
        
        for (String section : requiredSections) {
            if (!configContent.contains(section)) {
                throw new RuntimeException("config.yml invalide: section manquante '" + section + "'");
            }
        }
        
        String[] dbFields = {"host:", "port:", "database:", "username:", "password:"};
        for (String field : dbFields) {
            if (!configContent.contains(field)) {
                throw new RuntimeException("config.yml invalide: champ database manquant '" + field + "'");
            }
        }
        
        System.out.println("  ✅ config.yml valide avec toutes les sections requises");
        
        String[] migrations = {
            "db/migration/V2__create_arena_tables.sql"
        };
        
        for (String migration : migrations) {
            java.io.InputStream migrationStream = PluginLoadTest.class.getClassLoader()
                .getResourceAsStream(migration);
            
            if (migrationStream == null) {
                throw new RuntimeException("Migration Flyway manquante: " + migration);
            }
            
            String migrationContent = new String(migrationStream.readAllBytes());
            migrationStream.close();
            
            if (migrationContent.trim().isEmpty()) {
                throw new RuntimeException("Migration Flyway vide: " + migration);
            }
            
            if (!migrationContent.toUpperCase().contains("CREATE TABLE")) {
                throw new RuntimeException("Migration Flyway invalide (pas de CREATE TABLE): " + migration);
            }
            
            System.out.println("  ✅ Migration valide: " + migration);
        }
        
        System.out.println("  ✅ Toutes les configurations sont valides");
    }
}
