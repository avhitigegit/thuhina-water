package lk.thuhina.water.sales.service;

import java.math.BigDecimal;

import lk.thuhina.water.common.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer account balance (design 4.3 {@code customer_account}; positive = the customer owes). Changed only in the
 * same transaction as the bill (or, from M11, the payment) that causes it.
 */
@Service
public class AccountService {

    private final NamedParameterJdbcTemplate jdbc;

    public AccountService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Current balance (not locked). */
    @Transactional(readOnly = true)
    public BigDecimal balance(long customerId) {
        BigDecimal balance = jdbc.query("select balance from customer_account where customer_id = :c",
                new MapSqlParameterSource("c", customerId), rs -> rs.next() ? rs.getBigDecimal(1) : null);
        return Money.of(balance);
    }

    /**
     * Balance locked until the end of the transaction, so the credit check and the update see the same number while
     * other users post for the same customer. A missing row is created first.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal lockedBalance(long customerId) {
        MapSqlParameterSource p = new MapSqlParameterSource("c", customerId);
        jdbc.update("insert into customer_account (customer_id, balance) values (:c, 0) on conflict do nothing", p);
        return Money.of(jdbc.queryForObject("select balance from customer_account where customer_id = :c for update", p, BigDecimal.class));
    }

    /** balance += change */
    @Transactional(propagation = Propagation.MANDATORY)
    public void add(long customerId, BigDecimal change) {
        if (change.signum() == 0) {
            return;
        }
        jdbc.update("update customer_account set balance = balance + :d where customer_id = :c",
                new MapSqlParameterSource("c", customerId).addValue("d", change));
    }
}
