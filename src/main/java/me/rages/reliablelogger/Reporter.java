package me.rages.reliablelogger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class Reporter {

    private String url;
    private static final Gson gson = new Gson();
    private static final String USER_AGENT = "Mozilla/5.0"; // Example user agent


    public static Reporter trackErrors(String url) {
        return new Reporter(url);
    }

    private static String getFileChecksum(String filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        FileInputStream fis = new FileInputStream(filePath);

        byte[] byteArray = new byte[1024];
        int bytesCount = 0;

        while ((bytesCount = fis.read(byteArray)) != -1) {
            digest.update(byteArray, 0, bytesCount);
        }

        fis.close();

        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }

    public Reporter(String url) {
        this.url = url;
        Bukkit.getLogger().addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getThrown() != null) {
                    try {
                        String handleError = throwableToString(record.getThrown());
                        CodeSource codeSource = Reporter.class.getProtectionDomain().getCodeSource();
                        if (codeSource != null) {
                            URL jarUrl = codeSource.getLocation();
                            File jarFile = new File(jarUrl.toURI());
                            String checksum = getFileChecksum(jarFile.getAbsolutePath());
                            sendReport(url, checksum, handleError);
                        }
                    } catch (Exception err) {
                    }
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() throws SecurityException {
            }
        });
    }

    private static String throwableToString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    public static void sendReport(String url, String pluginId, String errorMessage) {
        try {
            URL apiUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", USER_AGENT); // Set User-Agent header
            connection.setDoOutput(true);

            JsonObject jsonRequest = new JsonObject();
            jsonRequest.addProperty("plugin", pluginId);
            jsonRequest.addProperty("message", errorMessage);

            byte[] jsonBytes = gson.toJson(jsonRequest).getBytes(StandardCharsets.UTF_8);

            // Set Content-Length header
            connection.setRequestProperty("Content-Length", String.valueOf(jsonBytes.length));

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(jsonBytes);
            }

            connection.getResponseCode();
            connection.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}