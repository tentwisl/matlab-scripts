package net.mca.nation.cabinet;

public enum CabinetRole {
    VICE_PRESIDENT("Vice President",       "Assumes leadership if leader absent >7 days"),
    SEC_DEFENSE    ("Secretary of Defense","Advises military; auto-manages garrison when autonomous"),
    SEC_TREASURY   ("Secretary of Treasury","Auto-manages trade routes and tax rate when autonomous"),
    SEC_STATE      ("Secretary of State",  "Advises diplomacy; auto-sends minor diplomatic messages"),
    SEC_INTERIOR   ("Secretary of Interior","Manages internal city policies and building queues"),
    ATTORNEY_GENERAL("Attorney General",   "Enforces laws; manages bandit raids and border incidents"),
    CHIEF_SCIENCE  ("Chief of Science",    "Advises research; auto-allocates science points");

    public final String displayName;
    public final String description;

    CabinetRole(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
