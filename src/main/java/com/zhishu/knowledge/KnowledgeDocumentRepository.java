package com.zhishu.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
    Optional<KnowledgeDocument> findByFileMd5AndDeleted(String md5, Integer deleted);
    List<KnowledgeDocument> findByUserIdAndDeletedOrderByIdDesc(Long userId, Integer deleted);
}
