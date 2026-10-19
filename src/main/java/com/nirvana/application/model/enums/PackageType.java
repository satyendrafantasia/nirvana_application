package com.nirvana.application.model.enums;

public enum PackageType {
    // Retained for the starter package supplied by the original V18 migration.
    GLOBAL_BRONZE(20000, 5),
    SILVER(5000, 5),
    GOLD(10000, 10),
    PLATINUM(15000, 15),
    DIAMOND(20000, 20);

    private final int priceCents;
    private final int sessions;

    PackageType(int priceCents, int sessions) {
        this.priceCents = priceCents;
        this.sessions = sessions;
    }

    public int getPriceCents() {
        return priceCents;
    }

    public int getSessions() {
        return sessions;
    }
}
