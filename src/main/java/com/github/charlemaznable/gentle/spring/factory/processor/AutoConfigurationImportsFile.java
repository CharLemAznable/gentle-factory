package com.github.charlemaznable.gentle.spring.factory.processor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.nonNull;

final class AutoConfigurationImportsFile {

    public static final String IMPORTS_FILE_PATH = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private AutoConfigurationImportsFile() { }

    static List<String> readServiceFile(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, UTF_8));
        List<String> serviceClasses = new ArrayList<>();
        try {
            for (String line = reader.readLine();
                 nonNull(line) && !line.isBlank();
                 line = reader.readLine()) {
                serviceClasses.add(line);
            }
        } catch (IOException e) {
            // ignore
        }
        return serviceClasses;
    }

    static void writeServiceFile(List<String> services,
                                 OutputStream output) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, UTF_8));
        for (String service : services) {
            writer.write(service);
            writer.newLine();
        }
        writer.flush();
    }
}
