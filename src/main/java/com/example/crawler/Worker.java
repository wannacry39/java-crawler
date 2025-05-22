package com.example.crawler;

import com.rabbitmq.client.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Worker {
    private final String rabbitHost;
    private final String queueName;
    private final String resultRabbitHost;
    private final String resultQueueName;

    public Worker(String rabbitHost, String queueName, String resultRabbitHost, String resultQueueName) {
        this.rabbitHost = rabbitHost;
        this.queueName = queueName;
        this.resultRabbitHost = resultRabbitHost;
        this.resultQueueName = resultQueueName;
    }

    public void start() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitHost);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.queueDeclare(queueName, true, false, false, null);
        // Создаём очередь для результатов
        ConnectionFactory resultFactory = new ConnectionFactory();
        resultFactory.setHost(resultRabbitHost);
        try (Connection resultConnection = resultFactory.newConnection();
             Channel resultChannel = resultConnection.createChannel()) {
            resultChannel.queueDeclare(resultQueueName, true, false, false, null);
        }
        System.out.println("[Worker] Waiting for tasks...");
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String url = new String(delivery.getBody(), "UTF-8");
            System.out.println("[Worker] Processing: " + url);
            try {
                Map<String, Object> article = fetchAndParse(url);
                if (article != null) {
                    // Отправляем результат в result_queue (JSON)
                    ConnectionFactory resultFactory2 = new ConnectionFactory();
                    resultFactory2.setHost(resultRabbitHost);
                    try (Connection resultConnection2 = resultFactory2.newConnection();
                         Channel resultChannel2 = resultConnection2.createChannel()) {
                        resultChannel2.queueDeclare(resultQueueName, true, false, false, null);
                        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(article);
                        resultChannel2.basicPublish("", resultQueueName, null, json.getBytes());
                        System.out.println("[Worker] Sent result to result_queue");
                    }
                }
            } catch (Exception e) {
                System.err.println("[Worker] Error: " + e.getMessage());
            }
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        };
        channel.basicConsume(queueName, false, deliverCallback, consumerTag -> {});
    }

    private Map<String, Object> fetchAndParse(String url) {
        try {
            Document doc = Jsoup.connect(url).get();
            String title = doc.selectFirst("h1").text();
            String pubDate = doc.selectFirst("time").attr("datetime");
            String author = doc.selectFirst(".tm-user-info__username") != null ? doc.selectFirst(".tm-user-info__username").text() : "";
            String text = doc.select(".article-formatted-body").text();
            Map<String, Object> article = new HashMap<>();
            article.put("title", title);
            article.put("pubDate", pubDate);
            article.put("author", author);
            article.put("text", text);
            article.put("link", url);
            return article;
        } catch (IOException e) {
            System.err.println("[Worker] Failed to fetch or parse: " + url);
            return null;
        }
    }
}
