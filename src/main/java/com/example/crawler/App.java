package com.example.crawler;

public class App {
    public static void main(String[] args) {
        System.out.println("News Crawler started!");
        final String rssUrl = "https://habr.com/ru/rss/articles/";
        final String rabbitHost = "rabbitmq";
        final String taskQueue = "task_queue";
        final String resultQueue = "result_queue";
        final String elasticHost = "elasticsearch";
        final int elasticPort = 9200;
        final String elasticIndex = "news";

        // 1. Парсим RSS и отправляем задачи
        RssCrawler crawler = new RssCrawler();
        try {
            var articles = crawler.fetchArticles(rssUrl);
            System.out.println("Найдено статей: " + articles.size());
            TaskProducer producer = new TaskProducer(rabbitHost, taskQueue);
            for (var article : articles) {
                producer.sendTask(article.link);
            }
            // Создаём очередь для результатов (result_queue)
            TaskProducer resultProducer = new TaskProducer(rabbitHost, resultQueue);
            resultProducer.sendTask("init"); // Просто чтобы очередь создалась
        } catch (Exception e) {
            System.err.println("Ошибка при парсинге RSS или отправке в RabbitMQ: " + e.getMessage());
        }

        // 2. Запускаем воркер для обработки задач и отправки результатов
        try {
            Worker worker = new Worker(rabbitHost, taskQueue, rabbitHost, resultQueue);
            new Thread(() -> {
                try {
                    worker.start();
                } catch (Exception e) {
                    System.err.println("Ошибка воркера: " + e.getMessage());
                    e.printStackTrace();
                }
            }, "Worker-Thread").start();
        } catch (Exception e) {
            System.err.println("Ошибка при запуске воркера: " + e.getMessage());
            e.printStackTrace();
        }

        // 3. Запускаем ResultConsumer для сохранения в ElasticSearch
        try {
            ResultConsumer consumer = new ResultConsumer(rabbitHost, resultQueue);
            new Thread(() -> {
                try {
                    consumer.consume();
                } catch (Exception e) {
                    System.err.println("Ошибка ResultConsumer: " + e.getMessage());
                    e.printStackTrace();
                }
            }, "ResultConsumer-Thread").start();
        } catch (Exception e) {
            System.err.println("Ошибка при запуске ResultConsumer: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
