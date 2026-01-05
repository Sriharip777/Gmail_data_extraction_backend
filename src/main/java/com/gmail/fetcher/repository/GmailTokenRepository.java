package com.gmail.fetcher.repository;

import com.gmail.fetcher.entity.GmailToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Gmail Token operations
 * UPDATED: Added empId-based queries for multi-employee support
 */
@Repository
public interface GmailTokenRepository extends MongoRepository<GmailToken, String> {

    // ========================================
    // MULTI-EMPLOYEE METHODS (PRIMARY)
    // ========================================

    /**
     * Find Gmail token by employee ID
     * ✅ NEW: Primary method for employee-specific token lookup
     *
     * @param empId Employee ID (e.g., "ARGHSE004")
     * @return Optional containing token if found
     */
    Optional<GmailToken> findByEmpId(String empId);

    /**
     * Check if employee has connected Gmail
     *
     * @param empId Employee ID
     * @return true if token exists for this employee
     */
    boolean existsByEmpId(String empId);

    /**
     * Delete token by employee ID
     * Used when employee disconnects Gmail
     *
     * @param empId Employee ID
     */
    void deleteByEmpId(String empId);

    // ========================================
    // LEGACY METHODS (BACKWARD COMPATIBILITY)
    // ========================================

    /**
     * Find token by user ID (Gmail email)
     * @deprecated Use findByEmpId() instead
     */
    Optional<GmailToken> findByUserId(String userId);

    /**
     * Find token by Google email
     * Useful for checking if a Gmail account is already connected
     */
    Optional<GmailToken> findByGoogleEmail(String googleEmail);

    /**
     * Check if email is already connected
     */
    boolean existsByGoogleEmail(String googleEmail);

    /**
     * Delete by user ID
     * @deprecated Use deleteByEmpId() instead
     */
    void deleteByUserId(String userId);

    void deleteByGoogleEmail(String googleEmail);
}
