package lk.thuhina.water.admin.mapper;

import lk.thuhina.water.admin.dto.UserResponse;
import lk.thuhina.water.admin.model.AppUser;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(AppUser u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getPhone(), u.getRole().name(),
                u.getRole().label(), u.isActive(), u.isMustChangePassword(), u.getLastLoginAt(), u.getVersion());
    }
}
