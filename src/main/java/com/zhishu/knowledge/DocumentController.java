package com.zhishu.knowledge;

import com.zhishu.common.ApiResponse;
import com.zhishu.common.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/doc")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public ApiResponse<KnowledgeDocument> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(documentService.upload(UserContext.require(), file));
    }

    @GetMapping("/list")
    public ApiResponse<List<KnowledgeDocument>> list() {
        return ApiResponse.ok(documentService.listMine(UserContext.require()));
    }

    @GetMapping("/{id}/progress")
    public ApiResponse<Map<String, Object>> progress(@PathVariable Long id) {
        return ApiResponse.ok(documentService.progress(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        documentService.delete(UserContext.require(), id);
        return ApiResponse.ok(null);
    }
}
