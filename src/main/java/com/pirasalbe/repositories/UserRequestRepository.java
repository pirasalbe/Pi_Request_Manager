package com.pirasalbe.repositories;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pirasalbe.models.LastRequestInfo;
import com.pirasalbe.models.database.UserRequest;
import com.pirasalbe.models.database.UserRequestPK;

/**
 * Repository to interact with UserRequest table
 *
 * @author pirasalbe
 *
 */
public interface UserRequestRepository extends JpaRepository<UserRequest, UserRequestPK> {

	@Query("SELECT count(u) FROM UserRequest u WHERE u.id.userId = :userId AND u.date >= :from AND u.role = 'CREATOR'")
	long countUserRequestsInGroupOfToday(@Param("userId") long userId, @Param("from") LocalDateTime from);

	@Query("SELECT new com.pirasalbe.models.LastRequestInfo(u.date, r.otherTags) "
			+ "FROM UserRequest u JOIN Request r ON u.id.requestId = r.id "
			+ "WHERE u.id.userId = :userId AND u.role = 'CREATOR' AND r.format = 'AUDIOBOOK' " + "ORDER BY u.date DESC")
	LastRequestInfo getLastAudiobookRequestOfUserInGroup(@Param("userId") long user);

}
