package com.github.charlemaznable.gentle.spring.factory.processor;

import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Map.Entry;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;

final class FactoriesFile {

    public static final String FACTORIES_FILE_PATH = "META-INF/spring.factories";

    private FactoriesFile() { }

    static Multimap<String, String> readServiceFile(InputStream input) {
        Properties properties = new Properties();
        try {
            properties.load(new InputStreamReader(input, UTF_8));
        } catch (IOException e) {
            // ignore
        }

        HashMultimap<String, String> serviceClasses = HashMultimap.create();
        for (Entry<Object, Object> entry : properties.entrySet()) {
            serviceClasses.putAll(entry.getKey().toString(),
                    Splitter.on(",").split(entry.getValue().toString()));
        }
        return serviceClasses;
    }

    static void writeServiceFile(Multimap<String, String> services,
                                 OutputStream output) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, UTF_8));
        for (String service : services.keySet()) {
            writer.write(service);
            writer.write("=");
            writer.write(String.join(",", services.get(service)));
            writer.newLine();
            writer.newLine();
        }
        writer.flush();
    }
}
