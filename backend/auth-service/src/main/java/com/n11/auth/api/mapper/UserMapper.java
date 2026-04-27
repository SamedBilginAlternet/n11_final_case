package com.n11.auth.api.mapper;

import com.n11.auth.api.dto.UserDto;
import com.n11.auth.domain.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDto toDto(User user);
}
