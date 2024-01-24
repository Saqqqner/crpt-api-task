package ru.adel;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import okhttp3.*;

import javax.swing.text.Document;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {

    private final OkHttpClient httpClient;
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final int requestLimit;
    private int innerCount;
    private final TimeUnit timeUnit;
    private static final Lock lock = new ReentrantLock();
    private static Instant time;
    private final long period;

    public CrptApi(int requestLimit, TimeUnit timeUnit) {
        this.requestLimit = requestLimit;
        this.timeUnit = timeUnit;

        this.period = timeUnit.toMillis(1);
        this.httpClient = new OkHttpClient();
    }
    public void syncThr(Document document , String signature){
        lock.lock();
        try{
            innerCount++;
            Instant now = Instant.now();
            if(innerCount ==1){
                time = now;
                
            } else if (Duration.between(time,now).toMillis()>=period) {
                innerCount = 1;
                time = now;
                
            } else if (innerCount>requestLimit) {
                innerCount = 1 ;
                long diff = period - Duration.between(time,now).toMillis();
                if(diff>0){
                    System.out.println("Выполнение остановлено до : "
                            + TimeUnit.SECONDS.convert(diff,TimeUnit.MILLISECONDS) + " секунд");
                Thread.sleep(diff);
                    }
                time = Instant.now();
            }
            createDocument(document,signature);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }finally {
            lock.unlock();
        }
    }

    @SneakyThrows
    private void createDocument(Document document, String signature) {
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonDocument = objectMapper.writeValueAsString(document);
        sendRequest(jsonDocument, signature);
        
    }

    @SneakyThrows
    private void sendRequest(String jsonDocument, String signature) {
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody requestBody = RequestBody.create(jsonDocument,mediaType);
        Request request = new Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .addHeader("Signature",signature)
                .build();
        Response response =httpClient.newCall(request).execute();
        System.out.println(response.body().string());
    }
    @AllArgsConstructor
    @Getter
    @Setter
    public static class Document {
        private Description description;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private Date productionDate;
        private String productionType;
        private List<Product> products;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private Date regDate;
        private String regNumber;


    }
    @AllArgsConstructor
    @Getter
    @Setter
    public static class Description {
        private final String participantInn;
    }
    @AllArgsConstructor
    @Getter
    @Setter
    public static class Product{
        private String certificateDocument;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private Date certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private Date productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;
    }
    private static Date getDate(String dateString) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            return dateFormat.parse(dateString);
        } catch (ParseException e) {
            throw new RuntimeException("Ошибка в парсе даты", e);
        }
    }
    private static Document createSampleDocumentForExample() {
        Description description = new Description("string");

        Product product = new Product(
                "string",
                getDate("2020-01-23"),
                "string",
                "string",
                "string",
                getDate("2020-01-23"),
                "string",
                "string",
                "string"
        );


        List<Product> products = new ArrayList<>();
        products.add(product);


        return new Document(
                description,
                "string",
                "string",
                "LP_INTRODUCE_GOODS",
                true,
                "string",
                "string",
                "string",
                getDate("2020-01-23"),
                "string",
                products,
                getDate("2020-01-23"),
                "string"
        );
    }

    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(3,TimeUnit.MINUTES);
        Document sampleDocumentForExample = createSampleDocumentForExample();
        String signa = "signature";
        for (int i = 0; i < 4; i ++) {
            new Thread(() -> {
                crptApi.syncThr(sampleDocumentForExample, signa);
            }).start();
        }


    }
}
