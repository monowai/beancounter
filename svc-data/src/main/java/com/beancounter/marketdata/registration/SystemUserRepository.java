package com.beancounter.marketdata.registration;

import com.beancounter.common.model.SystemUser;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface SystemUserRepository extends CrudRepository<SystemUser, String> {
  Optional<SystemUser> findById(String id);

  Optional<SystemUser> findByEmail(String email);
}
