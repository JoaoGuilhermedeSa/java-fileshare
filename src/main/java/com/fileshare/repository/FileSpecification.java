package com.fileshare.repository;

import com.fileshare.domain.FileMetadata;
import com.fileshare.domain.FileTag;
import com.fileshare.dto.SearchCriteria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Map;

public final class FileSpecification {

    private FileSpecification() {}

    public static Specification<FileMetadata> fromCriteria(SearchCriteria criteria) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            if (criteria.q() != null && !criteria.q().isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("originalName")),
                        "%" + criteria.q().toLowerCase() + "%"
                ));
            }

            if (criteria.virtualDirectory() != null && !criteria.virtualDirectory().isBlank()) {
                predicates.add(cb.like(
                        root.get("virtualDirectory"),
                        criteria.virtualDirectory() + "%"
                ));
            }

            if (criteria.uploadedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("uploadedAt"), criteria.uploadedFrom()));
            }

            if (criteria.uploadedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("uploadedAt"), criteria.uploadedTo()));
            }

            if (criteria.tags() != null && !criteria.tags().isEmpty()) {
                for (Map.Entry<String, String> entry : criteria.tags().entrySet()) {
                    var subquery = query.subquery(Long.class);
                    var tagRoot = subquery.from(FileTag.class);
                    subquery.select(cb.literal(1L))
                            .where(
                                    cb.equal(tagRoot.get("file"), root),
                                    cb.equal(tagRoot.get("name"), entry.getKey()),
                                    cb.equal(tagRoot.get("value"), entry.getValue())
                            );
                    predicates.add(cb.exists(subquery));
                }
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
