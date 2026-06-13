package inu.timetable.repository;

import inu.timetable.entity.User;
import inu.timetable.enums.UserStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    @EntityGraph(attributePaths = "userMajors")
    Optional<User> findByUsername(String username);

    @EntityGraph(attributePaths = "userMajors")
    Optional<User> findByUsernameAndStatus(String username, UserStatus status);

    @EntityGraph(attributePaths = "userMajors")
    Optional<User> findByIdAndStatus(Long id, UserStatus status);
    
    boolean existsByUsername(String username);

    boolean existsByIdAndStatus(Long id, UserStatus status);
}
