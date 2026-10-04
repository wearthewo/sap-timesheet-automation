package com.acme.workforce.identity.infrastructure;

import com.acme.workforce.identity.domain.ApplicationUser;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface UserRepository extends JpaRepository<ApplicationUser,UUID> {
 Optional<ApplicationUser> findByExternalSubject(String subject);
 @Modifying
 @Query(value="INSERT INTO app_users (id,external_subject,email,display_name,role) VALUES (:id,:subject,:email,:name,'EMPLOYEE') ON CONFLICT (external_subject) DO NOTHING", nativeQuery=true)
 int provision(@Param("id") UUID id,@Param("subject") String subject,@Param("email") String email,@Param("name") String name);
}
