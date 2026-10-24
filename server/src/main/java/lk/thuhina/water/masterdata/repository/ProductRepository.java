package lk.thuhina.water.masterdata.repository;

import java.util.List;
import java.util.Optional;

import lk.thuhina.water.masterdata.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByOrderByCodeAsc();

    Optional<Product> findByCode(String code);
}
