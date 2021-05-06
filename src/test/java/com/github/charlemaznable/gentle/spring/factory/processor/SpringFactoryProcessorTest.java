package com.github.charlemaznable.gentle.spring.factory.processor;

import com.google.common.base.Splitter;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static com.github.charlemaznable.gentle.spring.factory.processor.SpringFactoryProcessor.MISSING_SERVICES_ERROR;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@TestInstance(Lifecycle.PER_CLASS)
public class SpringFactoryProcessorTest {

    @Test
    public void autoService() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new SpringFactoryProcessor()).compile(
                        JavaFileObjects.forResource("test/SomeService.java"),
                        JavaFileObjects.forResource("test/SomeServiceProvider1.java"),
                        JavaFileObjects.forResource("test/SomeServiceProvider2.java"),
                        JavaFileObjects.forResource("test/Enclosing.java"),
                        JavaFileObjects.forResource("test/AnotherService.java"),
                        JavaFileObjects.forResource("test/AnotherServiceProvider.java"));
        assertThat(compilation).succeededWithoutWarnings();

        Properties properties = generatedProperties(compilation);
        assertEquals("test.AnotherServiceProvider", properties.getProperty("test.AnotherService"));
        List<String> someServices = Splitter.on(",").splitToList(properties.getProperty("test.SomeService"));
        assertEquals(3, someServices.size());
        assertTrue(someServices.contains("test.Enclosing$NestedSomeServiceProvider"));
        assertTrue(someServices.contains("test.SomeServiceProvider1"));
        assertTrue(someServices.contains("test.SomeServiceProvider2"));
    }

    @Test
    public void multiService() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new SpringFactoryProcessor()).compile(
                        JavaFileObjects.forResource("test/SomeService.java"),
                        JavaFileObjects.forResource("test/AnotherService.java"),
                        JavaFileObjects.forResource("test/MultiServiceProvider.java"));
        assertThat(compilation).succeededWithoutWarnings();

        Properties properties = generatedProperties(compilation);
        assertEquals("test.MultiServiceProvider", properties.getProperty("test.SomeService"));
        assertEquals("test.MultiServiceProvider", properties.getProperty("test.AnotherService"));
    }

    @Test
    public void badMultiService() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new SpringFactoryProcessor())
                .compile(JavaFileObjects.forResource("test/NoServices.java"));
        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(MISSING_SERVICES_ERROR);
    }

    @Test
    public void generic() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new SpringFactoryProcessor()).compile(
                        JavaFileObjects.forResource("test/GenericService.java"),
                        JavaFileObjects.forResource("test/GenericServiceProvider.java"));
        assertThat(compilation).succeededWithoutWarnings();

        Properties properties = generatedProperties(compilation);
        assertEquals("test.GenericServiceProvider", properties.getProperty("test.GenericService"));
    }

    @Test
    public void genericWithVerifyOption() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new SpringFactoryProcessor())
                .withOptions("-Averify=true", "-Adebug").compile(
                        JavaFileObjects.forResource("test/GenericService.java"),
                        JavaFileObjects.forResource("test/GenericServiceProvider.java"));
        assertThat(compilation).succeeded();
        assertThat(compilation).hadWarningContaining(
                "Service provider test.GenericService is generic, so it can't be named exactly by"
                        + " @SpringFactory. If this is OK, add @SuppressWarnings(\"rawtypes\").");
    }

    @Test
    public void genericWithVerifyOptionAndSuppressWarings() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new SpringFactoryProcessor())
                .withOptions("-Averify=true", "-Adebug").compile(
                        JavaFileObjects.forResource("test/GenericService.java"),
                        JavaFileObjects.forResource("test/GenericServiceProviderSuppressWarnings.java"));
        assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    public void nestedGenericWithVerifyOptionAndSuppressWarnings() {
        Compilation compilation = Compiler.javac()
                .withProcessors(new SpringFactoryProcessor())
                .withOptions("-Averify=true", "-Adebug").compile(
                        JavaFileObjects.forResource("test/GenericService.java"),
                        JavaFileObjects.forResource("test/EnclosingGeneric.java"));
        assertThat(compilation).succeededWithoutWarnings();

        Properties properties = generatedProperties(compilation);
        assertEquals("test.EnclosingGeneric$GenericServiceProvider", properties.getProperty("test.GenericService"));
    }

    private Properties generatedProperties(Compilation compilation) {
        Optional<JavaFileObject> javaFileObject = compilation
                .generatedFile(StandardLocation.CLASS_OUTPUT, "META-INF/spring.factories");
        Properties properties = new Properties();
        try {
            assertTrue(javaFileObject.isPresent());
            properties.load(new InputStreamReader(javaFileObject.get().openInputStream(), UTF_8));
        } catch (IOException e) {
            // ignored
        }
        return properties;
    }
}
