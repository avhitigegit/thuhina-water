package lk.thuhina.water.expenses.repository;

import lk.thuhina.water.expenses.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
}
