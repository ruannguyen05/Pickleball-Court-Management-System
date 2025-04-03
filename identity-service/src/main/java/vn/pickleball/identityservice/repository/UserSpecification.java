package vn.pickleball.identityservice.repository;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import vn.pickleball.identityservice.entity.Role;
import vn.pickleball.identityservice.entity.User;

public class UserSpecification {
    public static Specification<User> filterUsersExcludeAdmin(String username, String phoneNumber, String email, String roleName) {
        return (root, query, cb) -> {
            query.distinct(true);
            Predicate predicate = cb.conjunction();

            if (username != null && !username.isBlank()) {
                predicate = cb.and(predicate, cb.like(cb.lower(root.get("username")), "%" + username.toLowerCase() + "%"));
            }

            if (phoneNumber != null && !phoneNumber.isBlank()) {
                predicate = cb.and(predicate, cb.like(cb.lower(root.get("phoneNumber")), "%" + phoneNumber.toLowerCase() + "%"));
            }

            if (email != null && !email.isBlank()) {
                predicate = cb.and(predicate, cb.like(cb.lower(root.get("email")), "%" + email.toLowerCase() + "%"));
            }

            if (roleName != null && !roleName.isBlank()) {
                Join<User, Role> rolesJoin = root.join("roles");
                predicate = cb.and(predicate, cb.like(cb.lower(rolesJoin.get("name")), "%" + roleName.toLowerCase() + "%"));
            }

            // Exclude users who have ADMIN role
            Subquery<String> subquery = query.subquery(String.class);
            Root<User> subRoot = subquery.from(User.class);
            Join<User, Role> subJoinRole = subRoot.join("roles");
            subquery.select(subRoot.get("id"))
                    .where(cb.equal(subJoinRole.get("name"), "ADMIN"));

            predicate = cb.and(predicate, cb.not(root.get("id").in(subquery)));

            return predicate;
        };
    }

}

