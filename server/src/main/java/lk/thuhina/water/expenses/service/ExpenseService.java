package lk.thuhina.water.expenses.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.common.Versions;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseRequest;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseSaved;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseTypeRequest;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseTypeView;
import lk.thuhina.water.expenses.model.Expense;
import lk.thuhina.water.expenses.model.ExpenseType;
import lk.thuhina.water.expenses.repository.ExpenseRepository;
import lk.thuhina.water.expenses.repository.ExpenseTypeRepository;
import lk.thuhina.water.expenses.rules.ExpenseRules;
import lk.thuhina.water.numbering.service.NumberingService;
import lk.thuhina.water.numbering.service.SequenceNames;
import lk.thuhina.water.sales.model.PayMethod;
import lk.thuhina.water.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expense writes (FR-62, FR-63, BR-19): add (EX-0001), edit any field, delete with a reason (soft delete, audit DELETE), and the
 * expense types (add, rename, activate / deactivate). Every change is audited in the prototype's words.
 */
@Service
public class ExpenseService {

    static final String ENTITY = "Expense";
    static final String TYPE_ENTITY = "Expense type";

    private final ExpenseRepository expenses;
    private final ExpenseTypeRepository types;
    private final ExpenseQueryService query;
    private final NumberingService numbering;
    private final AuditService audit;
    private final Clock clock;

    public ExpenseService(ExpenseRepository expenses, ExpenseTypeRepository types, ExpenseQueryService query, NumberingService numbering,
                          AuditService audit, Clock clock) {
        this.expenses = expenses;
        this.types = types;
        this.query = query;
        this.numbering = numbering;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ expenses

    @Transactional
    public ExpenseSaved create(ExpenseRequest r) {
        Expense.Fields f = check(r, null);
        Expense e = new Expense(numbering.next(SequenceNames.EXPENSE));
        e.set(f);
        e = expenses.saveAndFlush(e);
        audit.log(AuditAction.CREATE, ENTITY, e.getExpenseNo(), text(f));
        String message = e.getExpenseNo() + " saved – " + Money.format(f.amount()) + " for "
                + ExpenseRules.monthLabel(YearMonth.from(f.forMonth()));
        return new ExpenseSaved(query.row(e.getId()), message);
    }

    @Transactional
    public ExpenseSaved update(long id, ExpenseRequest r) {
        Expense e = live(id);
        Versions.check(r.version(), e.getVersion(), ENTITY + " " + e.getExpenseNo());
        Expense.Fields before = e.fields();
        Expense.Fields f = check(r, before.typeId());
        if (!f.equals(before)) {
            e.set(f);
            expenses.saveAndFlush(e);
            audit.log(AuditAction.UPDATE, ENTITY, e.getExpenseNo(), changes(before, f));
        }
        return new ExpenseSaved(query.row(id), "Expense updated");
    }

    /** Soft delete with the reason; the audit log keeps what was deleted and why. */
    @Transactional
    public void delete(long id, String reason, Long version) {
        Expense e = live(id);
        Versions.check(version, e.getVersion(), ENTITY + " " + e.getExpenseNo());
        String why = Texts.blankToNull(reason);
        if (why == null) {
            throw new ValidationException("reason", ExpenseRules.MSG_REASON);
        }
        e.delete(why, clock.instant(), CurrentUser.usernameOrSystem());
        expenses.saveAndFlush(e);
        audit.log(AuditAction.DELETE, ENTITY, e.getExpenseNo(), text(e.fields()) + " – deleted: " + why);
    }

    private Expense live(long id) {
        Expense e = expenses.findById(id).orElseThrow(() -> new NotFoundException(ENTITY, id));
        if (e.isDeleted()) {
            throw new NotFoundException(ENTITY, id);
        }
        return e;
    }

    /**
     * Checks the request (prototype messages) and returns the fields to store. A new expense needs an active type; an edit may
     * keep the inactive type it already has.
     */
    private Expense.Fields check(ExpenseRequest r, Long currentTypeId) {
        LocalDate today = BusinessDates.today(clock);
        YearMonth month = ExpenseRules.parseMonth(r.forMonth());
        ExpenseType type = r.typeId() == null ? null : types.findById(r.typeId()).orElse(null);
        String reference = Texts.blankToNull(r.reference());
        ExpenseRules.Problem p = ExpenseRules.problem(new ExpenseRules.Input(r.paidDate(), month, type != null, r.amount(), r.method(),
                reference), today);
        if (p != null) {
            throw new ValidationException(p.field(), p.message());
        }
        if (!type.isActive() && !Objects.equals(type.getId(), currentTypeId)) {
            throw new ValidationException("typeId", ExpenseRules.MSG_TYPE_INACTIVE);
        }
        return new Expense.Fields(r.paidDate(), month.atDay(1), type.getId(), Texts.blankToNull(r.description()),
                Texts.blankToNull(r.paidTo()), Texts.blankToNull(r.billNo()), Money.of(r.amount()), r.method(),
                r.method() == PayMethod.CASH ? null : reference);
    }

    /** "Electricity Rs. 3,920.00 for July 2026 – Store – July bill" */
    private String text(Expense.Fields f) {
        String type = types.findById(f.typeId()).map(ExpenseType::getName).orElse("?");
        return type + " " + Money.format(f.amount()) + " for " + ExpenseRules.monthLabel(YearMonth.from(f.forMonth()))
                + (f.description() == null ? "" : " – " + f.description());
    }

    private String changes(Expense.Fields a, Expense.Fields b) {
        List<String> out = new ArrayList<>();
        if (a.typeId() != b.typeId()) {
            out.add("type → " + types.findById(b.typeId()).map(ExpenseType::getName).orElse("?"));
        }
        if (!a.amount().equals(b.amount())) {
            out.add("amount " + Money.format(a.amount()) + " → " + Money.format(b.amount()));
        }
        if (!a.forMonth().equals(b.forMonth())) {
            out.add("for " + ExpenseRules.monthLabel(YearMonth.from(a.forMonth())) + " → "
                    + ExpenseRules.monthLabel(YearMonth.from(b.forMonth())));
        }
        if (!a.paidDate().equals(b.paidDate())) {
            out.add("paid " + BusinessDates.format(a.paidDate()) + " → " + BusinessDates.format(b.paidDate()));
        }
        if (a.method() != b.method() || !Objects.equals(a.reference(), b.reference())) {
            out.add("paid by " + b.method().label() + (b.reference() == null ? "" : " (" + b.reference() + ")"));
        }
        if (!Objects.equals(a.description(), b.description()) || !Objects.equals(a.paidTo(), b.paidTo())
                || !Objects.equals(a.billNo(), b.billNo())) {
            out.add("details updated");
        }
        return text(b) + ": " + String.join("; ", out);
    }

    // ------------------------------------------------------------------ types

    @Transactional
    public ExpenseTypeView createType(ExpenseTypeRequest r) {
        String name = typeName(r.name());
        if (types.existsByNameIgnoreCase(name)) {
            throw new ValidationException("name", ExpenseRules.MSG_TYPE_EXISTS);
        }
        ExpenseType t = new ExpenseType(name, Texts.blankToNull(r.description()));
        if (r.active() != null && !r.active()) {
            t.update(name, t.getDescription(), false);
        }
        t = types.saveAndFlush(t);
        audit.log(AuditAction.CREATE, TYPE_ENTITY, name, t.getDescription() == null ? "" : t.getDescription());
        return view(t);
    }

    @Transactional
    public ExpenseTypeView updateType(long id, ExpenseTypeRequest r) {
        ExpenseType t = types.findById(id).orElseThrow(() -> new NotFoundException(TYPE_ENTITY, id));
        Versions.check(r.version(), t.getVersion(), TYPE_ENTITY + " " + t.getName());
        String name = r.name() == null ? t.getName() : typeName(r.name());
        if (types.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ValidationException("name", ExpenseRules.MSG_TYPE_EXISTS);
        }
        String description = r.description() == null ? t.getDescription() : Texts.blankToNull(r.description());
        boolean active = r.active() == null ? t.isActive() : r.active();
        List<String> changes = new ArrayList<>();
        if (!name.equals(t.getName())) {
            changes.add("renamed " + t.getName() + " → " + name);
        }
        if (!Objects.equals(description, t.getDescription())) {
            changes.add("description updated");
        }
        if (active != t.isActive()) {
            changes.add(active ? "Activated" : "Deactivated");
        }
        if (!changes.isEmpty()) {
            t.update(name, description, active);
            types.saveAndFlush(t);
            audit.log(AuditAction.UPDATE, TYPE_ENTITY, name, String.join("; ", changes));
        }
        return view(t);
    }

    private static String typeName(String raw) {
        String name = Texts.blankToNull(raw);
        if (name == null) {
            throw new ValidationException("name", ExpenseRules.MSG_TYPE_NAME);
        }
        return name;
    }

    private static ExpenseTypeView view(ExpenseType t) {
        return new ExpenseTypeView(t.getId(), t.getName(), t.getDescription(), t.isActive(), t.getVersion());
    }
}
