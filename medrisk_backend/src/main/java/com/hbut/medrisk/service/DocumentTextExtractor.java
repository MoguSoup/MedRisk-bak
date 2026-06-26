package com.hbut.medrisk.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentTextExtractor {
    public String extract(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".txt")) {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }
        if (filename.endsWith(".pdf")) {
            try (PDDocument document = Loader.loadPDF(file.getBytes())) {
                return new PDFTextStripper().getText(document);
            }
        }
        if (filename.endsWith(".docx")) {
            try (InputStream input = file.getInputStream(); XWPFDocument document = new XWPFDocument(input)) {
                StringBuilder text = new StringBuilder();
                document.getParagraphs().forEach(paragraph -> {
                    if (!paragraph.getText().isBlank()) {
                        text.append(paragraph.getText()).append('\n');
                    }
                });
                return text.toString();
            }
        }
        throw new IllegalArgumentException("仅支持 txt、pdf、docx 文档");
    }
}
