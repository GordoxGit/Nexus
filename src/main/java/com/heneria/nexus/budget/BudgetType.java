package com.heneria.nexus.budget;

/**
 * Enumerates the different runtime budgets enforced for arenas.
 */
public enum BudgetType {

    ENTITY("entités"),
    ITEM("items"),
    PROJECTILE("projectiles"),
    PARTICLE("particules");

    private final String label;

    BudgetType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
