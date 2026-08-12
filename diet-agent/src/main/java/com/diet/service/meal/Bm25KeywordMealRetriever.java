package com.diet.service.meal;

import com.diet.model.MealItem;
import com.diet.model.MealSearchRequest;
import com.diet.model.SlotBundle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 小中型餐食库的应用侧 BM25 关键词召回。
 *
 * <p>主库先按 sourceMode、所有者、营养和过敏原完成硬过滤；本类只在受限候选语料上
 * 对菜名与槽位文本进行词法排序。因此即使后续替换为 OpenSearch，也不会把权限或安全约束
 * 下放给外部检索系统。</p>
 */
@Service
public class Bm25KeywordMealRetriever implements KeywordMealCandidateRetriever {
    private static final Pattern CJK_SEGMENT = Pattern.compile("[\\u4e00-\\u9fff]+");
    private static final Pattern LATIN_SEGMENT = Pattern.compile("[A-Za-z0-9]+");

    private final MealCandidateRepository mealRepository;
    private final int corpusLimit;
    private final int resultLimit;
    private final double k1;
    private final double b;

    public Bm25KeywordMealRetriever(
            MealCandidateRepository mealRepository,
            @Value("${diet.retrieval.keyword.corpus-limit:500}") int corpusLimit,
            @Value("${diet.retrieval.keyword.result-limit:50}") int resultLimit,
            @Value("${diet.retrieval.keyword.bm25.k1:1.2}") double k1,
            @Value("${diet.retrieval.keyword.bm25.b:0.75}") double b
    ) {
        this.mealRepository = mealRepository;
        this.corpusLimit = clamp(corpusLimit, 20, 2_000);
        this.resultLimit = clamp(resultLimit, 1, 200);
        this.k1 = Double.isFinite(k1) && k1 > 0 ? k1 : 1.2;
        this.b = Double.isFinite(b) && b >= 0 && b <= 1 ? b : 0.75;
    }

    @Override
    public List<MealItem> recall(MealSearchRequest request, List<String> queryTerms) {
        if (request == null) {
            return List.of();
        }
        Set<String> normalizedTerms = normalizeQueryTerms(queryTerms);
        if (normalizedTerms.isEmpty()) {
            return List.of();
        }

        List<MealItem> corpus = mealRepository.searchKeywordCorpus(
                request.sourceMode(), request.userId(), request.nutritionConstraints(), corpusLimit);
        if (corpus.isEmpty()) {
            return List.of();
        }

        List<Document> documents = corpus.stream()
                .filter(meal -> meal != null && meal.id() != null)
                .map(this::toDocument)
                .filter(document -> document.length() > 0)
                .toList();
        if (documents.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> documentFrequency = documentFrequency(documents, normalizedTerms);
        double averageLength = documents.stream().mapToInt(Document::length).average().orElse(1.0);
        return documents.stream()
                .map(document -> new ScoredMeal(document.meal(), score(document, normalizedTerms, documentFrequency, documents.size(), averageLength)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredMeal::score).reversed()
                        .thenComparing(scored -> scored.meal().id()))
                .limit(resultLimit)
                .map(ScoredMeal::meal)
                .toList();
    }

    private Document toDocument(MealItem meal) {
        Map<String, Integer> termFrequency = new HashMap<>();
        int length = addTokens(termFrequency, meal.name(), 2); // 菜名是用户显式食材/菜式表达的主要载体。
        SlotBundle slots = meal.slots() == null ? SlotBundle.empty() : meal.slots();
        for (List<String> values : dimensions(slots)) {
            for (String value : values) {
                length += addTokens(termFrequency, value, 1);
            }
        }
        return new Document(meal, termFrequency, length);
    }

    private int addTokens(Map<String, Integer> target, String text, int weight) {
        int count = 0;
        for (String token : tokenize(text)) {
            target.merge(token, weight, Integer::sum);
            count += weight;
        }
        return count;
    }

    private Map<String, Integer> documentFrequency(List<Document> documents, Set<String> queryTerms) {
        Map<String, Integer> frequency = new HashMap<>();
        for (Document document : documents) {
            for (String term : queryTerms) {
                if (document.termFrequency().containsKey(term)) {
                    frequency.merge(term, 1, Integer::sum);
                }
            }
        }
        return frequency;
    }

    private double score(
            Document document,
            Set<String> queryTerms,
            Map<String, Integer> documentFrequency,
            int documentCount,
            double averageLength
    ) {
        double score = 0;
        for (String term : queryTerms) {
            int termFrequency = document.termFrequency().getOrDefault(term, 0);
            if (termFrequency == 0) {
                continue;
            }
            int frequency = documentFrequency.getOrDefault(term, 0);
            double idf = Math.log(1 + (documentCount - frequency + 0.5) / (frequency + 0.5));
            double normalization = k1 * (1 - b + b * document.length() / averageLength);
            score += idf * (termFrequency * (k1 + 1)) / (termFrequency + normalization);
        }
        return score;
    }

    private Set<String> normalizeQueryTerms(List<String> queryTerms) {
        if (queryTerms == null) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : queryTerms) {
            String term = normalize(value);
            if (term.length() >= 2) {
                normalized.add(term);
            }
        }
        return normalized;
    }

    /**
     * 中文无需额外分词服务：为连续中文片段生成 2-6 字 n-gram；英文/数字保留完整词项。
     * 查询词由 HybridMealRetrievalService 同样限制在 2-6 字，两个通道的词项空间一致。
     */
    private List<String> tokenize(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        Matcher chinese = CJK_SEGMENT.matcher(normalized);
        while (chinese.find()) {
            String segment = chinese.group();
            for (int length = 2; length <= Math.min(6, segment.length()); length++) {
                for (int start = 0; start + length <= segment.length(); start++) {
                    tokens.add(segment.substring(start, start + length));
                }
            }
        }
        Matcher latin = LATIN_SEGMENT.matcher(normalized);
        while (latin.find()) {
            String term = latin.group();
            if (term.length() >= 2) {
                tokens.add(term);
            }
        }
        return tokens;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private List<List<String>> dimensions(SlotBundle slots) {
        return List.of(slots.mealTime(), slots.mood(), slots.scene(), slots.healthGoal(),
                slots.cuisine(), slots.taste(), slots.convenience());
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Document(MealItem meal, Map<String, Integer> termFrequency, int length) {
    }

    private record ScoredMeal(MealItem meal, double score) {
    }
}
