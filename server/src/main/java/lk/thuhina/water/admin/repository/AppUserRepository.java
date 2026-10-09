package lk.thuhina.water.admin.repository;

import java.util.Optional;

import lk.thuhina.water.admin.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** {@code username} must already be lower case ({@link AppUser#normalizeUsername}). */
    Optional<AppUser> findByUsername(String username);
}
