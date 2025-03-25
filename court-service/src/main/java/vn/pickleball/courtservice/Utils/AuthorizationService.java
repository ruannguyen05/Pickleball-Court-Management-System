package vn.pickleball.courtservice.Utils;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.pickleball.courtservice.repository.CourtRepository;

@Component
@RequiredArgsConstructor
public class AuthorizationService {
    private final CourtRepository courtRepository;

    public boolean hasAccessToCourt(String courtId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ADMIN"));

        if (isAdmin) {
            return true;
        }

        boolean isManager = authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("MANAGER"));

        if (isManager) {
            String userId = authentication.getName();
            return courtRepository.findById(courtId)
                    .map(court -> court.getManagerId().equals(userId))
                    .orElse(false);
        }

        return false;
    }
}
