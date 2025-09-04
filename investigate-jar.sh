#!/bin/bash

JAR_FILE="target/Nexus-0.0.0-SNAPSHOT.jar"

echo "🔍 Investigation du contenu JAR:"
echo ""

echo "📦 Classes Triumph GUI relocalisées:"
jar tf "$JAR_FILE" | grep "fr/heneria/nexus/libs/gui/" | sort

echo ""
echo "🚫 Classes Triumph GUI non-relocalisées (ne devrait pas en avoir):"
jar tf "$JAR_FILE" | grep "dev/triumphteam/gui" | head -5

echo ""
echo "📊 Statistiques des dépendances:"
echo "  Total fichiers: $(jar tf "$JAR_FILE" | wc -l)"
echo "  Classes Nexus: $(jar tf "$JAR_FILE" | grep "fr/heneria/nexus/" | grep -v "libs" | wc -l)"
echo "  Classes relocalisées: $(jar tf "$JAR_FILE" | grep "fr/heneria/nexus/libs/" | wc -l)"

echo ""
echo "🏗️ Structure des libs relocalisées:"
jar tf "$JAR_FILE" | grep "fr/heneria/nexus/libs/" | cut -d'/' -f1-5 | sort | uniq -c | sort -nr
