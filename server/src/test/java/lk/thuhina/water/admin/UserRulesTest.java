package lk.thuhina.water.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import lk.thuhina.water.admin.rules.UserRules;
import org.junit.jupiter.api.Test;

/** User rules of M01 (prototype {@code Ops.saveUser}). */
class UserRulesTest {

    @Test
    void usernameIsAtLeastThreeLowercaseLettersNumbersDotsOrUnderscores() {
        assertThat(UserRules.isValidUsername("saman.d")).isTrue();
        assertThat(UserRules.isValidUsername("kasun_2")).isTrue();
        assertThat(UserRules.isValidUsername("abc")).isTrue();

        assertThat(UserRules.isValidUsername("ab")).isFalse();
        assertThat(UserRules.isValidUsername("Saman.D")).isFalse();      // the service lower-cases first
        assertThat(UserRules.isValidUsername("saman d")).isFalse();
        assertThat(UserRules.isValidUsername("saman-d")).isFalse();
        assertThat(UserRules.isValidUsername("saman@d")).isFalse();
        assertThat(UserRules.isValidUsername("")).isFalse();
        assertThat(UserRules.isValidUsername(null)).isFalse();
        assertThat(UserRules.isValidUsername("a".repeat(51))).isFalse();
    }

    @Test
    void temporaryPasswordHasAtLeastEightCharacters() {
        assertThat(UserRules.isValidTemporaryPassword("1234567")).isFalse();
        assertThat(UserRules.isValidTemporaryPassword("12345678")).isTrue();
        assertThat(UserRules.isValidTemporaryPassword("x".repeat(72))).isTrue();
        assertThat(UserRules.isValidTemporaryPassword("x".repeat(73))).isFalse();
        assertThat(UserRules.isValidTemporaryPassword(null)).isFalse();
    }

    @Test
    void generatedPasswordsAreTenCharactersWithALetterAndADigitAndNoLookAlikes() {
        SecureRandom random = new SecureRandom();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String p = UserRules.temporaryPassword(random);
            assertThat(p).hasSize(10).matches("[A-Za-z0-9]+").doesNotContainPattern("[0O1lI]");
            assertThat(p).containsPattern("[A-Za-z]").containsPattern("[2-9]");
            assertThat(UserRules.isValidTemporaryPassword(p)).isTrue();
            seen.add(p);
        }
        assertThat(seen).hasSize(2000);
    }

    @Test
    void blankOptionalTextBecomesNull() {
        assertThat(UserRules.blankToNull("  ")).isNull();
        assertThat(UserRules.blankToNull(null)).isNull();
        assertThat(UserRules.blankToNull(" 077 100 2201 ")).isEqualTo("077 100 2201");
    }
}
