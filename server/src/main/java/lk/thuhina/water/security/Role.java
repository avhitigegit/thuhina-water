package lk.thuhina.water.security;

/** The three fixed roles (BRD section 5, design 8.2). */
public enum Role {
    ADMIN("Admin", "/dashboard"),
    ACCOUNTANT("Accountant", "/dashboard"),
    DELIVERY_STAFF("Delivery Staff", "/delivery/daily-list");

    private final String label;
    private final String landingPage;

    Role(String label, String landingPage) {
        this.label = label;
        this.landingPage = landingPage;
    }

    /** Name shown on screens ("Delivery Staff"). */
    public String label() {
        return label;
    }

    /** Page opened after login: Admin / Accountant → Dashboard; Delivery Staff → Daily Delivery List. */
    public String landingPage() {
        return landingPage;
    }
}
