import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.util.Enumeration;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class JarAnalysis {
    public static void main(String[] args) throws Exception {
        String jarPath = args[0];
        
        try (JarFile jar = new JarFile(jarPath)) {
            Map<String, Integer> packageCount = new HashMap<>();
            List<String> nonRelocatedDeps = new ArrayList<>();
            
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                
                if (name.endsWith(".class")) {
                    // Analyser les packages
                    String packageName = name.substring(0, name.lastIndexOf('/')).replace('/', '.');
                    packageCount.put(packageName, packageCount.getOrDefault(packageName, 0) + 1);
                    
                    // Détecter les dépendances non relocalisées
                    if (name.startsWith("com/zaxxer/hikari/") ||
                        name.startsWith("org/flywaydb/") ||
                        name.startsWith("dev/triumphteam/gui/")) {
                        nonRelocatedDeps.add(name);
                    }
                }
            }
            
            System.out.println("📊 Top 10 des packages (par nombre de classes):");
            packageCount.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
                .limit(10)
                .forEach(entry ->
                    System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " classes"));
            
            System.out.println("");
            if (nonRelocatedDeps.isEmpty()) {
                System.out.println("✅ Aucune dépendance non-relocalisée détectée");
            } else {
                System.out.println("⚠️  Dépendances non-relocalisées détectées:");
                nonRelocatedDeps.stream().limit(5).forEach(dep ->
                    System.out.println("    " + dep));
                if (nonRelocatedDeps.size() > 5) {
                    System.out.println("    ... et " + (nonRelocatedDeps.size() - 5) + " autres");
                }
                System.exit(1);
            }
            
            boolean hasHikari = packageCount.containsKey("fr.heneria.nexus.libs.hikari");
            boolean hasFlyway = packageCount.containsKey("fr.heneria.nexus.libs.flywaydb.core");
            boolean hasGui = packageCount.keySet().stream().anyMatch(pkg -> pkg.startsWith("fr.heneria.nexus.libs.gui"));
            
            System.out.println("");
            System.out.println("🔍 Vérification des relocalisations:");
            System.out.println("  HikariCP relocalisé: " + (hasHikari ? "✅" : "❌"));
            System.out.println("  Flyway relocalisé: " + (hasFlyway ? "✅" : "❌"));
            System.out.println("  Triumph GUI relocalisé: " + (hasGui ? "✅" : "❌"));
            
            if (!hasHikari || !hasFlyway || !hasGui) {
                System.err.println("❌ Certaines dépendances ne sont pas correctement relocalisées");
                System.exit(1);
            }
            
            System.out.println("✅ Toutes les dépendances sont correctement relocalisées");
        }
    }
}
