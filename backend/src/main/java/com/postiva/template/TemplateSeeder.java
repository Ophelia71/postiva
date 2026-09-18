package com.postiva.template;

import com.postiva.template.entity.IndustryTemplate;
import com.postiva.template.repository.IndustryTemplateRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TemplateSeeder implements CommandLineRunner {
    private final IndustryTemplateRepository repository;

    public TemplateSeeder(IndustryTemplateRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        seed("cafe", "Café / Đồ uống",
                List.of("Giới thiệu món mới", "Ưu đãi khung giờ vàng", "Feedback khách hàng", "Hậu trường pha chế"),
                List.of("Ghé quán hôm nay", "Nhắn tin để đặt trước", "Đặt ngay để giữ ưu đãi"),
                List.of("#cafe", "#douong", "#uudai"),
                List.of("giá tốt", "vị ngon", "khung giờ vàng", "địa điểm gần khách hàng"),
                List.of("trẻ trung", "thân thiện", "gần gũi"));

        seed("fashion", "Thời trang",
                List.of("Hàng mới về", "Gợi ý phối đồ", "Sale cuối tuần", "Feedback khách hàng"),
                List.of("Nhắn tin để được tư vấn size", "Đặt hàng hôm nay", "Inbox shop để giữ mẫu"),
                List.of("#thoitrang", "#outfit", "#sale"),
                List.of("phong cách", "chất liệu", "ưu đãi", "số lượng có hạn"),
                List.of("năng động", "sang trọng", "tự tin"));
    }

    private void seed(String code, String name, List<String> postTypes, List<String> ctas,
                      List<String> hashtags, List<String> angles, List<String> tones) {
        if (repository.findByIndustryCode(code).isPresent()) {
            return;
        }
        IndustryTemplate template = new IndustryTemplate();
        template.setIndustryCode(code);
        template.setIndustryName(name);
        template.setPostTypes(postTypes);
        template.setCtaExamples(ctas);
        template.setHashtagExamples(hashtags);
        template.setContentAngles(angles);
        template.setToneSuggestions(tones);
        repository.save(template);
    }
}
