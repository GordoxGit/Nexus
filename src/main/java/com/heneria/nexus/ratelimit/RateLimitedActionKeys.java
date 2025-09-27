package com.heneria.nexus.ratelimit;

/**
 * Canonical keys for rate limited actions stored in the database.
 */
public final class RateLimitedActionKeys {

    public static final String SHOP_PURCHASE_CLASS = "purchase:class";
    public static final String SHOP_PURCHASE_COSMETIC = "purchase:cosmetic";
    public static final String SHOP_REFRESH = "shop:refresh";
    public static final String QUEST_REROLL_DAILY = "quest:reroll_daily";

    private RateLimitedActionKeys() {
    }
}
