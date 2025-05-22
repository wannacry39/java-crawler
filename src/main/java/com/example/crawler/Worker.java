package com.example.crawler;

import com.rabbitmq.client.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Worker {
    private final String rabbitHost;
    private final String queueName;
    private final String resultRabbitHost;
    private final String resultQueueName;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public Worker(String rabbitHost, String queueName, String resultRabbitHost, String resultQueueName) {
        this.rabbitHost = rabbitHost;
        this.queueName = queueName;
        this.resultRabbitHost = resultRabbitHost;
        this.resultQueueName = resultQueueName;
    }

    public void start() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitHost);
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            channel.queueDeclare(queueName, true, false, false, null);
            ensureResultQueueExists();
            System.out.println("[Worker] Waiting for tasks...");
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String url = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("[Worker] Processing: " + url);
                try {
                    Map<String, Object> article = fetchAndParse(url);
                    if (article != null) {
                        sendResult(article);
                    }
                } catch (Exception e) {
                    System.err.println("[Worker] Error: " + e.getMessage());
                }
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            };
            channel.basicConsume(queueName, false, deliverCallback, consumerTag -> {});
        }
    }

    private void ensureResultQueueExists() throws Exception {
        ConnectionFactory resultFactory = new ConnectionFactory();
        resultFactory.setHost(resultRabbitHost);
        try (Connection resultConnection = resultFactory.newConnection();
             Channel resultChannel = resultConnection.createChannel()) {
            resultChannel.queueDeclare(resultQueueName, true, false, false, null);
        }
    }

    private void sendResult(Map<String, Object> article) throws Exception {
        ConnectionFactory resultFactory = new ConnectionFactory();
        resultFactory.setHost(resultRabbitHost);
        try (Connection resultConnection = resultFactory.newConnection();
             Channel resultChannel = resultConnection.createChannel()) {
            resultChannel.queueDeclare(resultQueueName, true, false, false, null);
            String json = objectMapper.writeValueAsString(article);
            resultChannel.basicPublish("", resultQueueName, null, json.getBytes(StandardCharsets.UTF_8));
            System.out.println("[Worker] Sent result to result_queue");
        }
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
