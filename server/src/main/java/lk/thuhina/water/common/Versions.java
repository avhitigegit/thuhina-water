package lk.thuhina.water.common;

import org.springframework.dao.OptimisticLockingFailureException;

/** Edit pop-ups send back the {@code version} they loaded; a different version means someone else saved first (409). */
public final class Versions {

    private Versions() {
    }

    /** Does nothing when the screen sent no version. */
    public static void check(Long sent, long current, String what) {
        if (sent != null && sent != current) {
            throw new OptimisticLockingFailureException(what + " was changed by someone else");
        }
    }
}
