#!/bin/bash

echo "🔍 Vérification des dépendances du JAR Nexus..."

JAR_FILE="target/Nexus-0.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR non trouvé. Exécutez 'mvn clean package' d'abord."
    exit 1
fi

echo ""
echo "📦 Vérification HikariCP relocalisé:"
if jar tf "$JAR_FILE" | grep -q "fr/heneria/nexus/libs/hikari/HikariDataSource.class"; then
    echo "  ✅ HikariCP correctement relocalisé"
else
    echo "  ❌ HikariCP non relocalisé"
    exit 1
fi

echo ""
echo "📦 Vérification Flyway relocalisé:"
if jar tf "$JAR_FILE" | grep -q "fr/heneria/nexus/libs/flywaydb/core/Flyway.class"; then
    echo "  ✅ Flyway correctement relocalisé"
else
    echo "  ❌ Flyway non relocalisé"
    exit 1
fi

echo ""
echo "📦 Vérification Triumph GUI relocalisé:"
GUI_CLASSES=$(jar tf "$JAR_FILE" | grep "fr/heneria/nexus/libs/gui/" | wc -l)
if [ "$GUI_CLASSES" -gt 0 ]; then
    echo "  ✅ Triumph GUI correctement relocalisé ($GUI_CLASSES classes)"
else
    echo "  ❌ Triumph GUI non relocalisé"
    exit 1
fi

echo ""
echo "📦 Vérification MariaDB Driver:"
if jar tf "$JAR_FILE" | grep -q "org/mariadb/jdbc/Driver.class"; then
    echo "  ✅ MariaDB Driver présent"
else
    echo "  ❌ MariaDB Driver manquant"
    exit 1
fi

echo ""
echo "🚫 Vérification des fuites de relocalisation:"
LEAKS=0
if jar tf "$JAR_FILE" | grep -q "com/zaxxer/hikari/"; then
    echo "  ⚠️ HikariCP non-relocalisé détecté"
    LEAKS=$((LEAKS + 1))
fi
if jar tf "$JAR_FILE" | grep -q "org/flywaydb/"; then
    echo "  ⚠️ Flyway non-relocalisé détecté"
    LEAKS=$((LEAKS + 1))
fi
if jar tf "$JAR_FILE" | grep -q "dev/triumphteam/gui/"; then
    echo "  ⚠️ Triumph GUI non-relocalisé détecté"
    LEAKS=$((LEAKS + 1))
fi

if [ "$LEAKS" -eq 0 ]; then
    echo "  ✅ Aucune fuite de relocalisation détectée"
else
    echo "  ❌ $LEAKS fuite(s) détectée(s)"
    exit 1
fi

echo ""
echo "✅ Toutes les vérifications sont passées avec succès!"
