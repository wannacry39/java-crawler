package com.example.crawler;

import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class ElasticStorage {
    private final RestHighLevelClient client;
    private final String indexName;

    public ElasticStorage(String host, int port, String indexName) {
        this.indexName = indexName;
        this.client = new RestHighLevelClient(
                RestClient.builder(new org.apache.http.HttpHost(host, port, "http")));
    }

    public void close() throws IOException {
        client.close();
    }

    public String computeId(String title, String pubDate) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = title + pubDate;
            byte[] hash = digest.digest(input.getBytes());
            return Base64.getUrlEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean exists(String id) throws IOException {
        return client.exists(new org.elasticsearch.action.get.GetRequest(indexName, id), RequestOptions.DEFAULT);
    }

    public void saveArticle(Map<String, Object> article, String id) throws IOException {
        if (exists(id)) {
            System.out.println("[ElasticStorage] Document already exists: " + id);
            return;
        }
        IndexRequest request = new IndexRequest(indexName).id(id).source(article, XContentType.JSON);
        IndexResponse response = client.index(request, RequestOptions.DEFAULT);
        System.out.println("[ElasticStorage] Saved: " + response.getId());
    }

    public void searchByTitle(String title) throws IOException {
        SearchRequest searchRequest = new SearchRequest(indexName);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.matchQuery("title", title));
        searchRequest.source(sourceBuilder);
        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println("[ElasticStorage] Search hits: " + response.getHits().getTotalHits());
    }

    // Поиск по нескольким полям с логическими операторами (AND)
    public void searchByTitleAndAuthor(String title, String author) throws IOException {
        SearchRequest searchRequest = new SearchRequest(indexName);
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
            .must(QueryBuilders.matchQuery("title", title))
            .must(QueryBuilders.matchQuery("author", author));
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(boolQuery);
        searchRequest.source(sourceBuilder);
        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println("[ElasticStorage] Search hits (AND): " + response.getHits().getTotalHits());
    }

    // Поиск по нескольким полям с логическими операторами (OR)
    public void searchByTitleOrAuthor(String title, String author) throws IOException {
        SearchRequest searchRequest = new SearchRequest(indexName);
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
            .should(QueryBuilders.matchQuery("title", title))
            .should(QueryBuilders.matchQuery("author", author))
            .minimumShouldMatch(1);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(boolQuery);
        searchRequest.source(sourceBuilder);
        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println("[ElasticStorage] Search hits (OR): " + response.getHits().getTotalHits());
    }

    // Сложный полнотекстовый поиск с fuzziness
    public void fuzzySearchInText(String text) throws IOException {
        SearchRequest searchRequest = new SearchRequest(indexName);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.matchQuery("text", text).fuzziness("AUTO"));
        searchRequest.source(sourceBuilder);
        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println("[ElasticStorage] Fuzzy search hits: " + response.getHits().getTotalHits());
    }

    // Агрегация: количество публикаций по авторам
    public void aggregateByAuthor() throws IOException {
        SearchRequest searchRequest = new SearchRequest(indexName);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .size(0)
            .aggregation(AggregationBuilders.terms("by_author").field("author.keyword"));
        searchRequest.source(sourceBuilder);
        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        Terms byAuthor = response.getAggregations().get("by_author");
        System.out.println("[ElasticStorage] Публикации по авторам:");
        for (Terms.Bucket bucket : byAuthor.getBuckets()) {
            System.out.println(bucket.getKeyAsString() + ": " + bucket.getDocCount());
        }
    }

    // Агрегация: гистограмма по датам публикаций (по дням)
    public void aggregateByDate() throws IOException {
        SearchRequest searchRequest = new SearchRequest(indexName);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .size(0)
            .aggregation(AggregationBuilders.dateHistogram("by_date")
                .field("pubDate")
                .calendarInterval(DateHistogramInterval.DAY));
        searchRequest.source(sourceBuilder);
        SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
        var byDate = response.getAggregations().get("by_date");
        System.out.println("[ElasticStorage] Гистограмма по датам публикаций:");
        for (var bucket : ((org.elasticsearch.search.aggregations.bucket.histogram.Histogram) byDate).getBuckets()) {
            System.out.println(bucket.getKeyAsString() + ": " + bucket.getDocCount());
        }
    }
}
