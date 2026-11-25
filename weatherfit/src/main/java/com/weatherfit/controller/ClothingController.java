
package com.weatherfit.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import com.weatherfit.entity.Clothing;
import com.weatherfit.repository.ClothingRepository;

import java.util.List;
import java.util.UUID;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.batik.transcoder.SVGAbstractTranscoder;

@Controller
public class ClothingController {

    private final ClothingRepository clothingRepository;

    @Value("${weatherfit.upload-dir}")
    private String uploadDir;

    public ClothingController(ClothingRepository clothingRepository) {
        this.clothingRepository = clothingRepository;
    }

    // 이미지 저장 및 SVG 변환 공통 메서드
    private String saveImage(MultipartFile image) throws IOException {

        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("이미지를 선택해 주세요.");
        }

        // 파일 크기 제한: 5MB
        if (image.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("이미지는 5MB 이하만 업로드할 수 있습니다.");
        }

        String originalFilename = image.getOriginalFilename();
        String extension = "";

        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(
                    originalFilename.lastIndexOf(".")).toLowerCase();
        }

        // 저장 폴더 생성
        Files.createDirectories(Path.of(uploadDir));

        String savedFilename;

        // SVG 파일인 경우 PNG로 변환
        if (extension.equals(".svg")) {

            savedFilename = UUID.randomUUID() + ".png";

            Path filePath = Path.of(uploadDir, savedFilename);

            try (
                ByteArrayInputStream inputStream =
                        new ByteArrayInputStream(image.getBytes());

                ByteArrayOutputStream outputStream =
                        new ByteArrayOutputStream()
            ) {

                PNGTranscoder transcoder = new PNGTranscoder();

                // 외부 리소스 접근 제한
                transcoder.addTranscodingHint(
                        SVGAbstractTranscoder.KEY_ALLOW_EXTERNAL_RESOURCES,
                        Boolean.FALSE
                );

                TranscoderInput input =
                        new TranscoderInput(inputStream);

                TranscoderOutput output =
                        new TranscoderOutput(outputStream);

                transcoder.transcode(input, output);

                Files.write(filePath, outputStream.toByteArray());

            } catch (TranscoderException e) {

                throw new IOException(
                        "SVG 이미지를 PNG로 변환하지 못했습니다.", e);
            }

        } else {

            // 일반 이미지 형식 검사
            if (!extension.matches("\\.(jpg|jpeg|png|gif|webp)")) {
                throw new IllegalArgumentException(
                        "지원하지 않는 이미지 형식입니다.");
            }

            savedFilename = UUID.randomUUID() + extension;

            Path filePath = Path.of(uploadDir, savedFilename);

            image.transferTo(filePath.toFile());
        }

        return "/uploads/" + savedFilename;
    }

    // 새 옷 등록 화면
    @GetMapping("/clothes/new")
    public String showForm(Model model) {

        model.addAttribute("clothing", new Clothing());

        return "clothing-form";
    }

    // 새 옷 등록 처리
    @PostMapping("/clothes")
    public String saveClothing(
            @ModelAttribute Clothing clothing,
            @RequestParam("image") MultipartFile image)
            throws IOException {

        String imagePath = saveImage(image);

        clothing.setImagePath(imagePath);

        clothingRepository.save(clothing);

        return "redirect:/clothes";
    }

    // 옷장 목록 화면
    @GetMapping("/clothes")
    public String showClothes(Model model) {

        List<Clothing> clothes = clothingRepository.findAll();

        model.addAttribute("clothes", clothes);

        return "clothing-list";
    }

    // 옷 정보 수정 화면
    @GetMapping("/clothes/{id}/edit")
    public String showEditForm(
            @PathVariable("id") Long id,
            Model model) {

        Clothing clothing = clothingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "잘못된 옷 ID입니다: " + id));

        model.addAttribute("clothing", clothing);

        return "clothing-edit";
    }

    // 옷 정보 수정 처리
    @PostMapping("/clothes/{id}/edit")
    public String updateClothing(
            @PathVariable("id") Long id,
            @ModelAttribute Clothing formData,
            @RequestParam(value = "image", required = false) MultipartFile image)
            throws IOException {

        // 기존 옷 정보 불러오기
        Clothing clothing = clothingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "잘못된 옷 ID입니다: " + id));

        // 옷 정보 수정
        clothing.setName(formData.getName());
        clothing.setCategory(formData.getCategory());
        clothing.setColor(formData.getColor());
        clothing.setThickness(formData.getThickness());

        // 새 사진을 선택한 경우에만 교체
        if (image != null && !image.isEmpty()) {

            String imagePath = saveImage(image);

            clothing.setImagePath(imagePath);
        }

        clothingRepository.save(clothing);

        return "redirect:/clothes";
    }

    // 옷 삭제
    @PostMapping("/clothes/{id}/delete")
    public String deleteClothing(@PathVariable("id") Long id) {

        clothingRepository.deleteById(id);

        return "redirect:/clothes";
    }
}
