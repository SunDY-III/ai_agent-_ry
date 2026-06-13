package com.zhishu.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    /**
     * 关键词召回通道：MySQL ngram 全文索引。
     * 纯向量检索对编号、专有名词（如“工单号 TK20260612001”“ERR-1042”）召回差，
     * 用关键词检索补位，再与向量结果做 RRF 融合 —— 混合检索的核心动机。
     */
    @Query(value = """
            SELECT c.* FROM document_chunk c
            JOIN knowledge_document d ON d.id = c.doc_id AND d.deleted = 0 AND d.version = c.doc_version
            WHERE MATCH(c.content) AGAINST(:query IN NATURAL LANGUAGE MODE)
            LIMIT :topK
            """, nativeQuery = true)
    List<DocumentChunk> keywordSearch(@Param("query") String query, @Param("topK") int topK);

    @Modifying
    @Query("delete from DocumentChunk c where c.docId = :docId")
    void deleteByDocId(@Param("docId") Long docId);
}
