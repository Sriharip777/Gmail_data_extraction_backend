// SessionController.java
package com.gmail.fetcher.controller;

import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Session Management Controller
 * Handles employee session for multi-user Gmail support
 */
@RestController
@RequestMapping("/api/session")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@Slf4j
public class SessionController {

    /**
     * Set employee ID in session
     * Called by frontend when user logs in to Gmail module
     */
    @PostMapping("/set-empid")
    public ResponseEntity<Map<String, Object>> setEmpId(
            @RequestBody Map<String, String> request,
            HttpSession session) {

        String empId = request.get("empId");

        if (empId == null || empId.isBlank()) {
            log.error("❌ No empId provided in request");
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "empId is required"
            ));
        }

        log.info("✅ Setting empId in session: {} (Session ID: {})", empId, session.getId());
        session.setAttribute("empId", empId);

        // Set session timeout to 30 minutes
        session.setMaxInactiveInterval(1800);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "empId set in session",
                "empId", empId,
                "sessionId", session.getId()
        ));
    }

    /**
     * ✅ NEW: Store user role in session
     */
    @PostMapping("/set-user-role")
    public ResponseEntity<Map<String, Object>> setUserRole(
            @RequestBody Map<String, String> request,
            HttpSession session) {

        String userRole = request.get("userRole");

        if (userRole != null) {
            session.setAttribute("userRole", userRole);
            log.info("✅ Set userRole in session: {}", userRole);
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "userRole", userRole
        ));
    }

    /**
     * Get current employee ID from session
     */
    @GetMapping("/get-empid")
    public ResponseEntity<Map<String, Object>> getEmpId(HttpSession session) {
        String empId = (String) session.getAttribute("empId");

        log.info("📖 Getting empId from session: {} (Session ID: {})", empId, session.getId());

        if (empId == null) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "No empId in session",
                    "empId", "",
                    "sessionId", session.getId()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "empId", empId,
                "sessionId", session.getId()
        ));
    }

    /**
     * Clear session
     */
    @PostMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearSession(HttpSession session) {
        String empId = (String) session.getAttribute("empId");
        log.info("🗑️ Clearing session for empId: {} (Session ID: {})", empId, session.getId());

        session.invalidate();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Session cleared"
        ));
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health(HttpSession session) {
        String empId = (String) session.getAttribute("empId");

        return ResponseEntity.ok(Map.of(
                "sessionActive", true,
                "empId", empId != null ? empId : "none",
                "sessionId", session.getId()
        ));
    }
}
