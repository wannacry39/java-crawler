package com.example.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RssCrawler {
    public static class Article {
        public String title;
        public String link;
        public String pubDate;
        public String author;
        public String description;
    }

    public List<Article> fetchArticles(String rssUrl) throws IOException {
        List<Article> articles = new ArrayList<>();
        Document doc = Jsoup.connect(rssUrl).get();
        Elements items = doc.select("item");
        for (Element item : items) {
            Article article = new Article();
            article.title = item.selectFirst("title").text();
            article.link = item.selectFirst("link").text();
            article.pubDate = item.selectFirst("pubDate") != null ? item.selectFirst("pubDate").text() : "";
            article.author = item.selectFirst("author") != null ? item.selectFirst("author").text() : "";
            article.description = item.selectFirst("description") != null ? item.selectFirst("description").text() : "";
            articles.add(article);
        }
        return articles;
    }
}
