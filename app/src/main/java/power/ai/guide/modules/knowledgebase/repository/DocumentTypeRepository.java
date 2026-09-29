package power.ai.guide.modules.knowledgebase.repository;

import power.ai.guide.modules.knowledgebase.model.DocumentTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 文档类型 Repository
 */
@Repository
public interface DocumentTypeRepository extends JpaRepository<DocumentTypeEntity, Long> {

    Optional<DocumentTypeEntity> findByName(String name);

    boolean existsByName(String name);
}
