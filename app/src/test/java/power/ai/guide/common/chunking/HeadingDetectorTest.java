package power.ai.guide.common.chunking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HeadingDetector 标题识别")
class HeadingDetectorTest {

  private final HeadingDetector detector = new HeadingDetector();

  @Nested
  @DisplayName("Markdown 标题")
  class MarkdownHeadings {

    @Test
    @DisplayName("识别 # 与 ## 层级并生成累积路径")
    void detectsMarkdownHeadings() {
      String text = "# 第一章 总则\n这是总则正文。\n## 1.1 术语\n这是术语正文。\n";
      List<Section> sections = detector.detect(text, 50);

      assertThat(sections).hasSize(2);
      assertThat(sections.get(0).getPath()).isEqualTo("第一章 总则");
      assertThat(sections.get(0).getLevel()).isEqualTo(1);
      assertThat(sections.get(0).getContent()).contains("这是总则正文");
      assertThat(sections.get(1).getPath()).isEqualTo("第一章 总则 > 1.1 术语");
      assertThat(sections.get(1).getLevel()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("规程编号标题")
  class RegulationHeadings {

    @Test
    @DisplayName("识别 第X章 与 数字编号层级")
    void detectsRegulationHeadings() {
      String text = "第1章 总则\n本章说明。\n1.1 术语定义\n术语内容。\n1.1.1 术语a\n内容a。\n";
      List<Section> sections = detector.detect(text, 50);

      assertThat(sections).hasSize(3);
      assertThat(sections.get(0).getPath()).isEqualTo("第1章 总则");
      assertThat(sections.get(1).getPath()).isEqualTo("第1章 总则 > 1.1 术语定义");
      assertThat(sections.get(2).getPath()).isEqualTo("第1章 总则 > 1.1 术语定义 > 1.1.1 术语a");
      assertThat(sections.get(2).getLevel()).isEqualTo(3);
    }

    @Test
    @DisplayName("一、与（一）识别为不同层级")
    void detectsCnNumHeadings() {
      String text = "一、总体要求\n总体内容。\n（一）具体要求\n具体内容。\n";
      List<Section> sections = detector.detect(text, 50);

      assertThat(sections).hasSize(2);
      assertThat(sections.get(0).getLevel()).isEqualTo(2);
      assertThat(sections.get(1).getLevel()).isEqualTo(3);
      assertThat(sections.get(1).getPath()).isEqualTo("一、总体要求 > （一）具体要求");
    }
  }

  @Nested
  @DisplayName("边界情况")
  class EdgeCases {

    @Test
    @DisplayName("无标题文本退化为单一内容片段")
    void noHeadingsProducesSingleSection() {
      String text = "这是没有任何标题的普通段落。\n第二行。";
      List<Section> sections = detector.detect(text, 50);

      assertThat(sections).hasSize(1);
      assertThat(sections.get(0).getPath()).isEmpty();
      assertThat(sections.get(0).getLevel()).isZero();
    }

    @Test
    @DisplayName("超长行不误判为标题")
    void longLineNotTreatedAsHeading() {
      String text = "1.1 " + "很长的正文内容".repeat(20);
      List<Section> sections = detector.detect(text, 50);

      assertThat(sections).hasSize(1);
      assertThat(sections.get(0).getPath()).isEmpty();
    }

    @Test
    @DisplayName("正文中的『第3条规定』不误判为标题")
    void articleReferenceNotTreatedAsHeading() {
      String text = "第3条规定：应当如何。";
      List<Section> sections = detector.detect(text, 50);

      assertThat(sections).hasSize(1);
      assertThat(sections.get(0).getPath()).isEmpty();
    }
  }
}
