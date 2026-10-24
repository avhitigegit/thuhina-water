package lk.thuhina.water.security.repository;

import java.time.Instant;

import lk.thuhina.water.security.model.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, String> {

    /** Removes finished locks and old failure counts (hourly clean-up job, design 9.1). */
    @Modifying
    @Query("delete from LoginAttempt a where (a.lockedUntil is not null and a.lockedUntil <= :now)"
            + " or (a.lockedUntil is null and a.lastFailedAt < :staleBefore)")
    int deleteExpired(@Param("now") Instant now, @Param("staleBefore") Instant staleBefore);
}
