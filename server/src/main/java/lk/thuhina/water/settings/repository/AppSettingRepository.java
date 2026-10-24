package lk.thuhina.water.settings.repository;

import lk.thuhina.water.settings.model.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
