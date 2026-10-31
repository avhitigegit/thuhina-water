package lk.thuhina.water.whatsapp.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.whatsapp.dto.WhatsAppDtos.MessageListResponse;
import lk.thuhina.water.whatsapp.dto.WhatsAppDtos.MessageRow;
import lk.thuhina.water.whatsapp.dto.WhatsAppDtos.MonthCount;
import lk.thuhina.water.whatsapp.model.MessageStatus;
import lk.thuhina.water.whatsapp.model.MessageType;
import lk.thuhina.water.whatsapp.rules.WhatsAppRules;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Message log of the WhatsApp tab (design 9.2): filters, newest first, and this month's counts for cost control. */
@Service
public class WhatsAppQueryService {

    public static final int MAX_PAGE_SIZE = 200;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public WhatsAppQueryService(NamedParameterJdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    public record Filter(MessageType type, MessageStatus status, LocalDate from, LocalDate to, Long customerId) {
    }

    @Transactional(readOnly = true)
    public MessageListResponse list(Filter f, int page, int size) {
        int pg = Math.max(page, 0);
        int sz = Math.clamp(size, 1, MAX_PAGE_SIZE);
        StringBuilder where = new StringBuilder(" where 1 = 1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (f.type() != null) {
            where.append(" and m.type = :type");
            p.addValue("type", f.type().name());
        }
        if (f.status() != null) {
            where.append(" and m.status = :status");
            p.addValue("status", f.status().name());
        }
        if (f.from() != null) {
            where.append(" and m.created_at >= :from");
            p.addValue("from", Timestamp.from(f.from().atStartOfDay(BusinessDates.ZONE).toInstant()));
        }
        if (f.to() != null) {
            where.append(" and m.created_at < :to");
            p.addValue("to", Timestamp.from(f.to().plusDays(1).atStartOfDay(BusinessDates.ZONE).toInstant()));
        }
        if (f.customerId() != null) {
            where.append(" and m.customer_id = :customer");
            p.addValue("customer", f.customerId());
        }
        Long total = jdbc.queryForObject("select count(*) from whatsapp_message m" + where, p, Long.class);
        p.addValue("limit", sz).addValue("offset", (long) pg * sz);
        List<MessageRow> items = jdbc.query("""
                select m.*, m.params::text as params_text, c.code as customer_code, c.name as customer_name
                from whatsapp_message m left join customer c on c.id = m.customer_id""" + where
                + " order by m.created_at desc, m.id desc limit :limit offset :offset", p, this::row);
        return new MessageListResponse(items, pg, sz, total == null ? 0 : total, thisMonth());
    }

    private MonthCount thisMonth() {
        YearMonth month = YearMonth.from(BusinessDates.today(clock));
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", Timestamp.from(month.atDay(1).atStartOfDay(BusinessDates.ZONE).toInstant()))
                .addValue("to", Timestamp.from(month.plusMonths(1).atDay(1).atStartOfDay(BusinessDates.ZONE).toInstant()));
        return jdbc.queryForObject("""
                select count(*) filter (where status in ('SENT', 'DELIVERED', 'READ') and not test_mode) as sent,
                       count(*) filter (where status = 'FAILED') as failed,
                       count(*) filter (where status = 'SKIPPED') as skipped,
                       count(*) filter (where test_mode) as test
                from whatsapp_message where created_at >= :from and created_at < :to""", p,
                (rs, i) -> new MonthCount(month.toString(), rs.getLong("sent"), rs.getLong("failed"), rs.getLong("skipped"), rs.getLong("test")));
    }

    private MessageRow row(ResultSet rs, int i) throws SQLException {
        MessageType type = MessageType.valueOf(rs.getString("type"));
        MessageStatus status = MessageStatus.valueOf(rs.getString("status"));
        List<String> params;
        try {
            params = json.readValue(rs.getString("params_text"), new TypeReference<List<String>>() {
            });
        } catch (RuntimeException e) {
            params = new ArrayList<>();
        }
        return new MessageRow(rs.getLong("id"), instant(rs, "created_at"), rs.getObject("customer_id", Long.class), rs.getString("customer_code"),
                rs.getString("customer_name"), type.name(), type.label(), WhatsAppRules.display(rs.getString("to_number")), status.name(),
                status.label(), rs.getBoolean("test_mode"), params, rs.getInt("attempts"), instant(rs, "sent_at"), instant(rs, "status_at"),
                rs.getString("error"), rs.getString("source_type"), rs.getObject("source_id", Long.class));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }
}
