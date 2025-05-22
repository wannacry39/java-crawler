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
            article.title = getTextOrEmpty(item, "title");
            article.link = getTextOrEmpty(item, "link");
            article.pubDate = getTextOrEmpty(item, "pubDate");
            article.author = getTextOrEmpty(item, "author");
            article.description = getTextOrEmpty(item, "description");
            articles.add(article);
        }
        return articles;
    }

    private String getTextOrEmpty(Element parent, String tag) {
        Element el = parent.selectFirst(tag);
        return el != null ? el.text() : "";
    }
}
