package vn.pickleball.courtservice.Utils;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.pickleball.courtservice.repository.CourtRepository;
import vn.pickleball.courtservice.repository.CourtSlotRepository;

@Component
@RequiredArgsConstructor
public class AuthorizationService {
    private final CourtSlotRepository courtSlotRepository;

    public boolean hasAccessToCourt(String courtSlotId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) {
            return true;
        }

        boolean isManager = authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_MANAGER"));

        if (isManager) {
            String userId = SecurityContextUtil.getUid();
            return courtSlotRepository.findById(courtSlotId)
                    .map(courtSlot -> courtSlot.getCourt().getManagerId().equals(userId))
                    .orElse(false);
        }

        return false;
    }
}
