package lk.thuhina.water.admin.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import lk.thuhina.water.admin.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** {@code username} must already be lower case ({@link AppUser#normalizeUsername}). */
    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);

    /** Users tab: active users first, then by name. */
    List<AppUser> findAllByOrderByActiveDescFullNameAsc();

    List<AppUser> findByUsernameIn(Collection<String> usernames);
}
