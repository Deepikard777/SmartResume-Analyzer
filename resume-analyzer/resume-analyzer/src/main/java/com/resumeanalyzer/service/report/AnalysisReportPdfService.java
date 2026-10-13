package com.resumeanalyzer.service.report;

import com.resumeanalyzer.dto.AnalysisResponse;
import com.resumeanalyzer.exception.ReportGenerationException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a stored analysis as a printable A4 PDF report.
 *
 * Uses PDFBox (already a project dependency for PDF text extraction) and the
 * Standard-14 Helvetica fonts, so no font file has to be embedded and the output is
 * small and portable.
 *
 * Deliberate properties:
 *  - The report contains only data that is already in the analysis payload plus the
 *    owner's own name/email. It never contains server file paths, storage paths,
 *    tokens or internal identifiers beyond the numeric ids shown to the owner.
 *  - It is generated on demand from the persisted analysis, so a report always
 *    matches what was scored; nothing is recomputed.
 *  - Content flows across pages: text is word-wrapped and a new page is started
 *    automatically when the cursor reaches the bottom margin.
 *  - The deterministic nature of the analysis is stated on the report itself, so a
 *    printed copy cannot be mistaken for an AI-generated or vendor-ATS verdict.
 */
@Service
public class AnalysisReportPdfService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisReportPdfService.class);

    // --- Page geometry (A4, points) ---
    private static final float MARGIN = 48f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - (2 * MARGIN);
    private static final float BOTTOM_LIMIT = MARGIN + 28f; // leave room for the footer

    // --- Type scale ---
    private static final float SIZE_TITLE = 20f;
    private static final float SIZE_SUBTITLE = 11f;
    private static final float SIZE_H1 = 13f;
    private static final float SIZE_BODY = 9.5f;
    private static final float SIZE_SMALL = 8.5f;
    private static final float LEADING = 13.5f;

    // --- Palette (RGB 0–1) ---
    private static final float[] INK = {0.11f, 0.12f, 0.15f};
    private static final float[] MUTED = {0.42f, 0.44f, 0.48f};
    private static final float[] ACCENT = {0.20f, 0.35f, 0.72f};
    private static final float[] GOOD = {0.10f, 0.55f, 0.32f};
    private static final float[] WARN = {0.79f, 0.35f, 0.06f};
    private static final float[] RULE = {0.85f, 0.86f, 0.89f};

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    /** Generates one report. Returns the PDF bytes. */
    public byte[] generate(AnalysisResponse analysis,
                           String preparedForName,
                           String preparedForEmail,
                           LocalDateTime generatedAt) {

        if (analysis == null) {
            throw new ReportGenerationException("No analysis was supplied for the report.");
        }

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            Layout layout = new Layout(document, regular, bold);

            writeHeader(layout, analysis, preparedForName, preparedForEmail, generatedAt);
            writeScores(layout, analysis);
            writeSkills(layout, analysis);
            writeKeywordCoverage(layout, analysis);
            writeStructure(layout, analysis);
            writeRecommendations(layout, analysis);
            writeFooterNote(layout);

            layout.close();
            document.save(out);
            log.info("Generated PDF report for analysis id={} ({} pages)",
                    analysis.getId(), document.getNumberOfPages());

            return out.toByteArray();

        } catch (IOException e) {
            throw new ReportGenerationException("Could not generate the PDF report.", e);
        }
    }

    // -----------------------------------------------------------------------
    // Sections
    // -----------------------------------------------------------------------

    private void writeHeader(Layout layout,
                             AnalysisResponse analysis,
                             String preparedForName,
                             String preparedForEmail,
                             LocalDateTime generatedAt) throws IOException {
        layout.heading("SmartResume Analyzer — ATS Diagnostic Report", SIZE_TITLE, true);
        layout.paragraph("Generated " + generatedAt.format(DATE_FORMAT), SIZE_SMALL, MUTED);
        layout.gap(4);

        if (preparedForName != null && !preparedForName.isBlank()) {
            String who = preparedForEmail == null || preparedForEmail.isBlank()
                    ? preparedForName
                    : preparedForName + " <" + preparedForEmail + ">";
            layout.paragraph("Prepared for: " + who, SIZE_BODY, INK);
        }

        layout.paragraph("Resume file: " + safe(analysis.getResumeFileName(), "—"), SIZE_BODY, INK);
        layout.paragraph("Target role: " + safe(analysis.getJobTitle(), "—")
                + (isBlank(analysis.getCompanyName()) ? "" : "  ·  " + analysis.getCompanyName()),
                SIZE_BODY, INK);
        layout.paragraph("Analysis reference: #" + analysis.getId(), SIZE_SMALL, MUTED);

        layout.rule();
    }

    private void writeScores(Layout layout, AnalysisResponse analysis) throws IOException {
        layout.sectionTitle("1. Overall ATS score");

        Integer ats = analysis.getAtsScore();
        layout.paragraph(ats == null ? "Not available" : ats + " / 100", 26f, scoreColour(ats));
        layout.paragraph(bandDescription(ats), SIZE_BODY, MUTED);
        layout.gap(6);

        AnalysisResponse.ScoreBreakdown breakdown = analysis.getScoreBreakdown();
        if (breakdown == null) {
            layout.paragraph("No score breakdown was stored for this analysis.", SIZE_BODY, MUTED);
            return;
        }

        layout.paragraph("Weighted sub-scores", SIZE_H1, INK);
        layout.gap(2);
        scoreRow(layout, "Skill match", breakdown.getSkillMatch(), "40%");
        scoreRow(layout, "Keyword match", breakdown.getKeywordMatch(), "25%");
        scoreRow(layout, "Relevance (JD emphasis)", breakdown.getRelevance(), "15%");
        scoreRow(layout, "Structure", breakdown.getStructure(), "12%");
        scoreRow(layout, "Formatting", breakdown.getFormatting(), "8%");
        layout.gap(4);
        layout.paragraph("The overall score is the weighted sum of the sub-scores above. Weights are fixed by "
                + "the project and are shown so the result can be reproduced by hand.", SIZE_SMALL, MUTED);
    }

    private void scoreRow(Layout layout, String label, Integer value, String weight) throws IOException {
        int v = value == null ? 0 : Math.max(0, Math.min(100, value));
        float labelWidth = 190f;
        float barX = MARGIN + labelWidth;
        float barWidth = 150f;
        float barHeight = 7f;

        layout.ensureSpace(LEADING);
        float y = layout.y;

        layout.text(label + "  (" + weight + ")", MARGIN, y, SIZE_BODY, INK, false);
        // Track
        layout.bar(barX, y + 2f, barWidth, barHeight, RULE);
        // Fill
        layout.bar(barX, y + 2f, barWidth * (v / 100f), barHeight, value == null ? RULE : ACCENT);
        layout.text(v + "/100", barX + barWidth + 10f, y, SIZE_BODY, MUTED, false);

        layout.y -= LEADING;
    }

    private void writeSkills(Layout layout, AnalysisResponse analysis) throws IOException {
        layout.ensureSpace(60);
        layout.sectionTitle("2. Skill matching");

        layout.bulletList("Matched skills", analysis.getMatchedSkills(), GOOD);
        layout.bulletList("Missing skills (required by the job description)", analysis.getMissingSkills(), WARN);
        layout.bulletList("Optional / nice-to-have skills", analysis.getOptionalSkills(), MUTED);
    }

    private void writeKeywordCoverage(Layout layout, AnalysisResponse analysis) throws IOException {
        List<AnalysisResponse.KeywordCoverage> rows = analysis.getKeywordCoverage();
        layout.ensureSpace(60);
        layout.sectionTitle("3. Keyword frequency and coverage");

        AnalysisResponse.KeywordStuffingInfo stuffing = analysis.getKeywordStuffing();
        layout.paragraph("Repetition risk: " + (stuffing == null || stuffing.getRisk() == null
                ? "not recorded" : stuffing.getRisk()), SIZE_BODY, INK);

        if (rows == null || rows.isEmpty()) {
            layout.paragraph("No per-keyword frequency data was stored for this analysis.", SIZE_BODY, MUTED);
            return;
        }

        long matched = rows.stream().filter(r -> Boolean.TRUE.equals(r.getMatched())).count();
        long inJd = rows.stream().filter(r -> Boolean.TRUE.equals(r.getInJobDescription())).count();
        layout.paragraph("Keywords from the job description: " + inJd
                + "   ·   present in the resume: " + matched
                + "   ·   table rows: " + rows.size(), SIZE_SMALL, MUTED);
        layout.gap(4);

        // Column layout
        float[] columns = {MARGIN, MARGIN + 210f, MARGIN + 275f, MARGIN + 340f};
        layout.ensureSpace(LEADING);
        layout.text("Keyword", columns[0], layout.y, SIZE_SMALL, MUTED, true);
        layout.text("In JD", columns[1], layout.y, SIZE_SMALL, MUTED, true);
        layout.text("In resume", columns[2], layout.y, SIZE_SMALL, MUTED, true);
        layout.text("Status", columns[3], layout.y, SIZE_SMALL, MUTED, true);
        layout.y -= 11f;
        layout.hairline();

        for (AnalysisResponse.KeywordCoverage row : rows) {
            layout.ensureSpace(LEADING);

            String status;
            float[] colour;
            if (Boolean.TRUE.equals(row.getFlagged())) {
                status = "Repeated";
                colour = WARN;
            } else if (Boolean.TRUE.equals(row.getMatched())) {
                status = Boolean.TRUE.equals(row.getInJobDescription()) ? "Matched" : "In resume only";
                colour = GOOD;
            } else {
                status = "Missing";
                colour = WARN;
            }

            layout.text(truncate(row.getKeyword(), 34), columns[0], layout.y, SIZE_BODY, INK, false);
            layout.text(String.valueOf(nvl(row.getJobDescriptionCount())), columns[1], layout.y, SIZE_BODY, MUTED, false);
            layout.text(String.valueOf(nvl(row.getResumeCount())), columns[2], layout.y, SIZE_BODY, INK, false);
            layout.text(status, columns[3], layout.y, SIZE_BODY, colour, false);
            layout.y -= LEADING;
        }

        layout.gap(4);
        layout.paragraph("Counts are whole-word occurrences. A term repeated several times is not penalised by "
                + "the score; it is marked \"Repeated\" only when it passes the repetition detector's documented "
                + "density and count thresholds.", SIZE_SMALL, MUTED);
    }

    private void writeStructure(Layout layout, AnalysisResponse analysis) throws IOException {
        List<AnalysisResponse.StructureCheck> checks = analysis.getStructure();
        layout.ensureSpace(60);
        layout.sectionTitle("4. Resume structure and formatting");

        if (checks == null || checks.isEmpty()) {
            layout.paragraph("No structure audit was stored for this analysis.", SIZE_BODY, MUTED);
            return;
        }

        long passed = checks.stream().filter(c -> "PASSED".equals(c.getStatus())).count();
        layout.paragraph(passed + " of " + checks.size() + " section checks passed.", SIZE_BODY, INK);
        layout.gap(2);

        for (AnalysisResponse.StructureCheck check : checks) {
            boolean ok = "PASSED".equals(check.getStatus());
            layout.ensureSpace(LEADING * 2);
            layout.text((ok ? "[OK]  " : "[!]   ") + safe(check.getSection(), "—"),
                    MARGIN, layout.y, SIZE_BODY, ok ? GOOD : WARN, true);
            layout.y -= LEADING;
            layout.wrapped(check.getMessage(), MARGIN + 16f, SIZE_SMALL, MUTED, CONTENT_WIDTH - 16f);
        }
    }

    private void writeRecommendations(Layout layout, AnalysisResponse analysis) throws IOException {
        List<AnalysisResponse.Recommendation> recommendations = analysis.getRecommendations();
        layout.ensureSpace(60);
        layout.sectionTitle("5. Recommended improvements");

        if (recommendations == null || recommendations.isEmpty()) {
            layout.paragraph("No recommendations were generated for this analysis — no sub-score fell below the "
                    + "thresholds the generator checks.", SIZE_BODY, MUTED);
            return;
        }

        int index = 1;
        for (AnalysisResponse.Recommendation recommendation : recommendations) {
            layout.ensureSpace(LEADING * 3);
            float[] colour = "HIGH".equals(recommendation.getPriority())
                    ? WARN
                    : "MEDIUM".equals(recommendation.getPriority()) ? ACCENT : MUTED;

            layout.text(index + ". [" + safe(recommendation.getPriority(), "—") + " · "
                    + safe(recommendation.getCategory(), "—") + "]", MARGIN, layout.y, SIZE_BODY, colour, true);
            layout.y -= LEADING;

            layout.wrapped(safe(recommendation.getTitle(), ""), MARGIN + 16f, SIZE_BODY, INK, CONTENT_WIDTH - 16f);
            layout.wrapped(safe(recommendation.getDetail(), ""), MARGIN + 16f, SIZE_SMALL, MUTED, CONTENT_WIDTH - 16f);

            List<String> related = recommendation.getRelatedItems();
            if (related != null && !related.isEmpty()) {
                layout.wrapped("Related: " + String.join(", ", related),
                        MARGIN + 16f, SIZE_SMALL, MUTED, CONTENT_WIDTH - 16f);
            }
            layout.gap(4);
            index++;
        }
    }

    private void writeFooterNote(Layout layout) throws IOException {
        layout.ensureSpace(70);
        layout.rule();
        layout.wrapped("This report was produced by SmartResume Analyzer from a deterministic, rule-based analysis "
                        + "of the text extracted from the uploaded document. It is not affiliated with, and does not "
                        + "reproduce, any commercial applicant tracking system. The recommendations are derived from "
                        + "measured findings; no AI model generated any part of this report and no resume content was "
                        + "invented. Scores describe the text as parsed and are not a prediction of a hiring outcome.",
                MARGIN, SIZE_SMALL, MUTED, CONTENT_WIDTH);
    }

    // -----------------------------------------------------------------------
    // Layout engine — one page at a time, auto-paginating
    // -----------------------------------------------------------------------

    private static final class Layout {
        private final PDDocument document;
        private final PDFont regular;
        private final PDFont bold;

        private PDPage page;
        private PDPageContentStream content;
        private float y;

        Layout(PDDocument document, PDFont regular, PDFont bold) throws IOException {
            this.document = document;
            this.regular = regular;
            this.bold = bold;
            newPage();
        }

        private void newPage() throws IOException {
            close();
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = PAGE_HEIGHT - MARGIN;
        }

        /** Start a new page if the requested vertical space is not available. */
        void ensureSpace(float needed) throws IOException {
            if (y - needed < BOTTOM_LIMIT) {
                newPage();
            }
        }

        void gap(float amount) {
            y -= amount;
        }

        void heading(String text, float size, boolean isBold) throws IOException {
            ensureSpace(size + 6);
            text(text, MARGIN, y, size, INK, isBold);
            y -= (size + 4);
        }

        void sectionTitle(String text) throws IOException {
            ensureSpace(LEADING * 2);
            text(text, MARGIN, y, SIZE_H1, ACCENT, true);
            y -= LEADING;
            hairline();
        }

        void paragraph(String text, float size, float[] colour) throws IOException {
            if (text == null || text.isBlank()) return;
            wrapped(text, MARGIN, size, colour, CONTENT_WIDTH);
        }

        void text(String value, float x, float baselineY, float size, float[] colour, boolean isBold)
                throws IOException {
            if (value == null || value.isEmpty()) return;
            content.beginText();
            content.setNonStrokingColor(colour[0], colour[1], colour[2]);
            content.setFont(isBold ? bold : regular, size);
            content.newLineAtOffset(x, baselineY);
            content.showText(sanitize(value));
            content.endText();
        }

        /** Word-wrapped paragraph starting at the current cursor. */
        void wrapped(String value, float x, float size, float[] colour, float maxWidth) throws IOException {
            if (value == null || value.isBlank()) return;
            PDFont font = regular;

            for (String paragraph : value.split("\\R", -1)) {
                if (paragraph.isBlank()) {
                    ensureSpace(LEADING);
                    y -= LEADING * 0.6f;
                    continue;
                }
                StringBuilder line = new StringBuilder();
                for (String word : paragraph.trim().split("\\s+")) {
                    String candidate = line.length() == 0 ? word : line + " " + word;
                    if (width(candidate, font, size) <= maxWidth || line.length() == 0) {
                        line.setLength(0);
                        line.append(candidate);
                    } else {
                        ensureSpace(LEADING);
                        text(line.toString(), x, y, size, colour, false);
                        y -= LEADING;
                        line.setLength(0);
                        line.append(word);
                    }
                }
                if (line.length() > 0) {
                    ensureSpace(LEADING);
                    text(line.toString(), x, y, size, colour, false);
                    y -= LEADING;
                }
            }
            y -= 2f;
        }

        void bulletList(String label, List<String> items, float[] colour) throws IOException {
            ensureSpace(LEADING * 2);
            text(label + " (" + (items == null ? 0 : items.size()) + ")", MARGIN, y, SIZE_BODY, INK, true);
            y -= LEADING;
            if (items == null || items.isEmpty()) {
                wrapped("— none", MARGIN + 16f, SIZE_SMALL, MUTED, CONTENT_WIDTH - 16f);
                return;
            }
            wrapped(String.join(", ", items), MARGIN + 16f, SIZE_BODY, colour, CONTENT_WIDTH - 16f);
        }

        void bar(float x, float baselineY, float barWidth, float barHeight, float[] colour) throws IOException {
            if (barWidth <= 0f) return;
            content.setNonStrokingColor(colour[0], colour[1], colour[2]);
            content.addRect(x, baselineY, barWidth, barHeight);
            content.fill();
        }

        void rule() throws IOException {
            ensureSpace(10);
            content.setStrokingColor(RULE[0], RULE[1], RULE[2]);
            content.setLineWidth(0.8f);
            content.moveTo(MARGIN, y);
            content.lineTo(PAGE_WIDTH - MARGIN, y);
            content.stroke();
            y -= 10f;
        }

        void hairline() throws IOException {
            ensureSpace(8);
            content.setStrokingColor(RULE[0], RULE[1], RULE[2]);
            content.setLineWidth(0.5f);
            content.moveTo(MARGIN, y);
            content.lineTo(PAGE_WIDTH - MARGIN, y);
            content.stroke();
            y -= 8f;
        }

        private float width(String value, PDFont font, float size) {
            try {
                return font.getStringWidth(sanitize(value)) / 1000f * size;
            } catch (IOException e) {
                // Fall back to a rough estimate rather than failing the whole report.
                return value.length() * size * 0.5f;
            }
        }

        void close() throws IOException {
            if (content != null) {
                content.close();
                content = null;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Text helpers
    // -----------------------------------------------------------------------

    /**
     * Make a string safe for a Standard-14 (WinAnsi) font.
     *
     * Extracted resume text and job descriptions are untrusted and routinely contain
     * typographic quotes, dashes, ellipses and accented letters. PDFBox throws if a
     * glyph is not encodable, so: normalise Unicode, fold diacritics to ASCII, map the
     * common typographic characters, then replace anything still outside printable
     * ASCII. This keeps the report readable without ever failing on odd input.
     */
    static String sanitize(String value) {
        if (value == null) return "";
        String s = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('\u2018', '\'').replace('\u2019', '\'')
                .replace('\u201C', '"').replace('\u201D', '"')
                .replace('\u2013', '-').replace('\u2014', '-')
                .replace("\u2026", "...")
                .replace('\u00A0', ' ')
                .replace('\u2022', '-')
                .replace('\u00B7', '-')
                .replace('\u00D7', 'x');

        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(c >= 0x20 && c <= 0x7E ? c : (c == '\n' || c == '\r' || c == '\t' ? ' ' : '?'));
        }
        return sb.toString();
    }

    static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "\u2026";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String safe(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private static float[] scoreColour(Integer score) {
        if (score == null) return MUTED;
        if (score >= 80) return GOOD;
        if (score >= 60) return ACCENT;
        return WARN;
    }

    private static String bandDescription(Integer score) {
        if (score == null) return "No score was stored for this analysis.";
        if (score >= 80) return "Strong alignment with the supplied job description.";
        if (score >= 60) return "Reasonable alignment with room to close specific gaps.";
        if (score >= 40) return "Partial alignment — the missing items above are the priority.";
        return "Low alignment — review the missing skills and keywords before applying.";
    }

    /** Convenience for tests: the plain-text content of a generated report. */
    public static List<String> describeSections() {
        List<String> sections = new ArrayList<>();
        sections.add("Overall ATS score");
        sections.add("Skill matching");
        sections.add("Keyword frequency and coverage");
        sections.add("Resume structure and formatting");
        sections.add("Recommended improvements");
        return sections;
    }
}
