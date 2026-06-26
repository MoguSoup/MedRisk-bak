package com.hbut.medrisk.controller;

import com.hbut.medrisk.service.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileStorageService files;

    public FileController(FileStorageService files) {
        this.files = files;
    }

    @GetMapping("/{bucket}/**")
    ResponseEntity<?> get(@PathVariable String bucket, HttpServletRequest request) throws IOException {
        String prefix = request.getContextPath() + "/api/files/" + bucket + "/";
        String uri = request.getRequestURI();
        String objectKey = UriUtils.decode(uri.substring(prefix.length()), java.nio.charset.StandardCharsets.UTF_8);
        FileStorageService.StoredResource resource = files.load(bucket, objectKey);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(resource.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.filename() + "\"")
                .body(resource.resource());
    }
}
